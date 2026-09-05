import java.io.FileInputStream
import java.util.Base64
import java.util.Properties
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.detekt)
    id("kotlin-parcelize")
    alias(libs.plugins.androidx.baselineprofile)
}

val currentVersion = "1.9.1"

/**
 * Reads CLIENT_SECRET from local.properties or system environment variable
 */
fun getClientSecret(): String {
    // Try to read from local.properties first
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val localProperties = Properties()
        localProperties.load(FileInputStream(localPropertiesFile))
        val localSecret = localProperties.getProperty("PUBER_CLIENT_SECRET")
        if (!localSecret.isNullOrEmpty()) {
            return localSecret
        }
    }

    // Fall back to system environment variable
    val envSecret = System.getenv("PUBER_CLIENT_SECRET")
    if (!envSecret.isNullOrEmpty()) {
        return envSecret
    }

    // Fallback to default value for development (not recommended for production)
    return ""
}

fun getTmdbReadAccessToken(): String {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val localProperties = Properties()
        localProperties.load(FileInputStream(localPropertiesFile))
        val token = localProperties.getProperty("TMDB_READ_ACCESS_TOKEN")
        if (!token.isNullOrEmpty()) return token
    }
    val envToken = System.getenv("TMDB_READ_ACCESS_TOKEN")
    if (!envToken.isNullOrEmpty()) return envToken
    return ""
}

// These public identities are governance-pinned, not selected by supplied credentials.
val validationBlobOid = "109bfc97479cd17724a3ba75a8d3b5ca9df22f52"
val validationKeySha256 = "91c981cee70e84338ff5c7938ae8110f8c0a2378a7a936c245133b4d5ee7620f"
val validationCertificateSha256 = "6b29181257cb520329553691b7a48c9b1123899950105127b3dd67012c49a83e"
val productionCertificateSha256 = "3e0ddb2c5d39953d278f8cce813ff07a6b74059f1f9caa8fd752602e2bb8b61a"
val releaseSigningMode = System.getenv("PUBER_RELEASE_SIGNING_MODE")
val productionInputs = listOf("RELEASE_KEYSTORE_BASE64", "STOREPASS", "KEYALIAS", "KEYPASS")
val prodReleaseRequested = gradle.startParameter.taskNames.any { it.contains("prodRelease", ignoreCase = true) }
fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
fun requireSigningFile(path: java.io.File) {
    require(Files.isRegularFile(path.toPath(), NOFOLLOW_LINKS) && !Files.isSymbolicLink(path.toPath())) {
        "Signing input must be a regular non-symlink file"
    }
    require(Files.getOwner(path.toPath(), NOFOLLOW_LINKS).name.substringAfterLast('\\') == System.getProperty("user.name")) {
        "Signing input has a foreign owner"
    }
}
fun signingCommand(vararg command: String): String {
    val process = ProcessBuilder(*command).directory(rootDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    require(process.waitFor() == 0) { "Signing identity command failed" }
    return output.trim()
}
fun requireSigningMode() {
    require(releaseSigningMode in setOf("debug_validation", "production")) {
        "prodRelease requires explicit PUBER_RELEASE_SIGNING_MODE=debug_validation or production"
    }
}
if (prodReleaseRequested) requireSigningMode()
val releaseProperties = Properties()
var releaseKeyFile: java.io.File? = null
var expectedReleaseCertificate: String? = null
if (releaseSigningMode != null) {
    requireSigningMode()
    val propertiesFile = file("keystore.properties")
    val keyFile = file("release.jks")
    fun present(path: java.io.File) = Files.exists(path.toPath(), NOFOLLOW_LINKS)
    when (releaseSigningMode) {
        "debug_validation" -> {
            require(!present(propertiesFile) && !present(keyFile) && productionInputs.none { System.getenv(it) != null }) {
                "Production signing inputs are forbidden in debug_validation"
            }
            val debugKey = file("debug.jks")
            requireSigningFile(debugKey)
            require(signingCommand("git", "ls-files", "--stage", "--", "app/debug.jks") ==
                "100644 $validationBlobOid 0\tapp/debug.jks") { "Tracked validation key blob drifted" }
            require(signingCommand("git", "hash-object", "--", "app/debug.jks") == validationBlobOid &&
                sha256(debugKey.readBytes()) == validationKeySha256) { "Validation key content drifted" }
            releaseKeyFile = debugKey
            releaseProperties.setProperty("storePassword", "android")
            releaseProperties.setProperty("keyPassword", "android")
            releaseProperties.setProperty("keyAlias", "androiddebugkey")
            expectedReleaseCertificate = validationCertificateSha256
        }
        "production" -> {
            require(System.getenv("KEYPASS") == null) { "KEYPASS is not an admitted signing input" }
            val environmentPresent = productionInputs.any { System.getenv(it) != null }
            if (present(propertiesFile)) {
                require(!environmentPresent && present(keyFile)) { "Ambiguous or partial production signing source" }
                requireSigningFile(propertiesFile)
                // Reject duplicate/extra properties rather than accepting Properties' last-write-wins behavior.
                val keys = propertiesFile.readLines().filter { it.isNotBlank() }.map {
                    require(it.matches(Regex("(storePassword|keyAlias|keyPassword)=.+"))) { "Invalid signing properties" }
                    it.substringBefore('=')
                }
                require(keys.size == 3 && keys.toSet() == setOf("storePassword", "keyAlias", "keyPassword")) {
                    "Production signing properties must contain exactly three fields"
                }
                propertiesFile.inputStream().use { releaseProperties.load(it) }
            } else {
                require(listOf("RELEASE_KEYSTORE_BASE64", "STOREPASS", "KEYALIAS").all { !System.getenv(it).isNullOrBlank() }) {
                    "Production signing environment is missing or partial"
                }
                val decoded = Base64.getDecoder().decode(System.getenv("RELEASE_KEYSTORE_BASE64"))
                if (present(keyFile)) {
                    requireSigningFile(keyFile)
                    require(keyFile.readBytes().contentEquals(decoded)) { "Decoded signing source is ambiguous" }
                } else {
                    Files.createFile(keyFile.toPath(), PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")))
                    keyFile.writeBytes(decoded)
                }
                releaseProperties.setProperty("storePassword", System.getenv("STOREPASS"))
                releaseProperties.setProperty("keyPassword", System.getenv("STOREPASS"))
                releaseProperties.setProperty("keyAlias", System.getenv("KEYALIAS"))
            }
            require(releaseProperties.stringPropertyNames() == setOf("storePassword", "keyAlias", "keyPassword") &&
                releaseProperties.values.all { it is String && it.isNotBlank() }) { "Production signing fields are not closed" }
            releaseKeyFile = keyFile
            expectedReleaseCertificate = productionCertificateSha256
        }
    }
    val selectedKey = requireNotNull(releaseKeyFile)
    requireSigningFile(selectedKey)
    val store = KeyStore.getInstance(selectedKey, releaseProperties.getProperty("storePassword").toCharArray())
    val alias = releaseProperties.getProperty("keyAlias")
    require(store.isKeyEntry(alias) && sha256(requireNotNull(store.getCertificate(alias)).encoded) == expectedReleaseCertificate) {
        "Selected signing alias certificate differs from the pinned identity"
    }
}
// Aggregate task names cannot bypass the configuration-time direct-task admission above.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name.contains("ProdRelease") }) requireSigningMode()
}

android {
    namespace = "com.kino.puber"
    compileSdk = Versions.CompileSdk

    defaultConfig {
        applicationId = "com.kino.puber"
        minSdk = Versions.MinSdk
        targetSdk = Versions.TargetSdk
        versionCode = Versions.DebugVersionCode
        versionName = currentVersion

        // Add CLIENT_SECRET to BuildConfig
        buildConfigField("String", "CLIENT_SECRET", "\"${getClientSecret()}\"")
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"${getTmdbReadAccessToken()}\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    flavorDimensions += "buildType"

    compileOptions {
        sourceCompatibility = Versions.JavaVersionCompat
        targetCompatibility = Versions.JavaVersionCompat
    }

    composeCompiler {
        stabilityConfigurationFiles.addAll(
            rootProject.layout.projectDirectory.file("config/compose/compiler_config.conf")
        )
    }


    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.jks")
        }

        create("release") {
            storeFile = releaseKeyFile
            storePassword = releaseProperties.getProperty("storePassword")
            keyAlias = releaseProperties.getProperty("keyAlias")
            keyPassword = releaseProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }

        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
        create("benchmarkRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }


    productFlavors {
        create("dev") {
            dimension = "buildType"
            versionName = "$currentVersion-$name"
            applicationIdSuffix = ".stage"
            resValue("string", "app_name", "Puber(${name.replaceFirstChar { it.uppercaseChar() }})")
        }

        create("prod") {
            dimension = "buildType"
            versionCode = Versions.VersionCode
            resValue("string", "app_name", "Puber")
        }

        create("instrumentation") {
            dimension = "buildType"
            applicationIdSuffix = ".instrumentation"
            resValue("string", "app_name", "Puber(Instrumentation)")
            buildConfigField(
                "int",
                "BASELINE_MOCK_PORT",
                providers.gradleProperty("puber.baselineMockPort").get(),
            )
        }
    }

}

androidComponents {
    beforeVariants { variantBuilder ->
        variantBuilder.enableAndroidTest =
            variantBuilder.productFlavors.any { (_, flavor) -> flavor == "instrumentation" }
    }
}

kotlin {
    jvmToolchain(Versions.JavaVersionCompat.majorVersion.toInt())
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(Versions.JvmTargetVersion))
        freeCompilerArgs.add("-Xjvm-default=all")
        optIn.addAll(
            listOf(
                "androidx.compose.material3.ExperimentalMaterial3Api",
                "androidx.compose.material.ExperimentalMaterialApi",
                "androidx.compose.foundation.ExperimentalFoundationApi",
                "androidx.compose.ui.test.ExperimentalTestApi",
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
                "kotlinx.coroutines.FlowPreview",
                "androidx.tv.material3.ExperimentalTvMaterial3Api",
                "org.koin.compose.scope.ExperimentalKoinApi",
            )
        )
    }
}

tasks {
    @Suppress("unused")
    val detektAll by registering(io.gitlab.arturbosch.detekt.Detekt::class) {
        parallel = true
        setSource(files(projectDir))
        include("**/*.kt")
        exclude("**/resources/**")
        exclude("**/build/**")
        exclude("**/androidTest/**")
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline.set(file("$rootDir/config/detekt/detekt-baseline.xml"))
        buildUponDefaultConfig = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.phosphor.icons)
    implementation(libs.androidx.security.crypto)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.compose.placeholder.material3)

    //coil
    implementation(libs.coil.core)
    implementation(libs.coil.video)
    implementation(libs.coil.compose)
    implementation(libs.coil.ktor)

    // OkHttp extensions
    implementation(libs.okhttp.doh)

    // Ktor HTTP client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Serialization & Utils
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    //navigation
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.tab.navigator)
    implementation(libs.voyager.koin)

    // Media3 (Video Player)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit5)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit5.launcher)
    testRuntimeOnly(libs.junit5.vintage)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockwebserver3)
    testImplementation(project(":player-test-fixtures"))

    detektPlugins(libs.detekt.compose.rules)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockwebserver3)
    androidTestImplementation(libs.kaspresso)
    androidTestImplementation(libs.kaspresso.compose)
    androidTestImplementation(libs.kakao.compose)
    add("instrumentationImplementation", project(":player-test-fixtures"))
    add("instrumentationImplementation", libs.mockwebserver3)
    add("instrumentationImplementation", libs.androidx.drawerlayout)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.profileinstaller)
}

android.testOptions {
    unitTests.isIncludeAndroidResources = true
}

baselineProfile {
    saveInSrc = true
    automaticGenerationDuringBuild = false
    mergeIntoMain = true
    variants {
        create("instrumentationRelease") {
            from(project(":baselineprofile"))
        }
    }
    warnings {
        maxAgpVersion = false
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxHeapSize = "2g"
}

// A successful assemble is signer proof, not merely an unsigned packaging task.
tasks.matching { it.name == "assembleProdRelease" }.configureEach {
    doLast {
        requireSigningMode()
        val apks = fileTree(layout.buildDirectory.dir("outputs/apk/prod/release")).matching { include("*.apk") }.files
        require(apks.size == 1) { "Expected exactly one release APK" }
        val apksigner = androidComponents.sdkComponents.sdkDirectory.get().asFile.resolve("build-tools/${android.buildToolsVersion}/apksigner")
        val proof = signingCommand(apksigner.absolutePath, "verify", "--print-certs", apks.single().absolutePath)
        val signers = Regex("(?m)^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)$")
            .findAll(proof).map { it.groupValues[1].lowercase() }.toList()
        require(signers == listOf(expectedReleaseCertificate)) { "Release APK signer is not the exact pinned certificate" }
        logger.lifecycle(if (releaseSigningMode == "debug_validation")
            "prodRelease validation APK is non-publishable" else "prodRelease production signer verified")
    }
}
