import java.io.FileInputStream
import java.util.Base64
import java.util.Properties

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

fun envOrNull(name: String): String? {
    return System.getenv(name)?.takeIf { it.isNotBlank() }
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
            val keystorePropertiesFile = file("keystore.properties")
            when {
                // 1. Local: keystore.properties file
                keystorePropertiesFile.exists() -> {
                    val keystoreProperties = Properties()
                    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                    keyAlias = keystoreProperties["keyAlias"] as String
                    keyPassword = keystoreProperties["keyPassword"] as String
                    storePassword = keystoreProperties["storePassword"] as String
                    storeFile = file("release.jks")
                }
                // 2. CI: base64-encoded keystore from RELEASE_KEYSTORE_BASE64 env var
                envOrNull("RELEASE_KEYSTORE_BASE64") != null -> {
                    val decoded = Base64.getDecoder().decode(envOrNull("RELEASE_KEYSTORE_BASE64"))
                    val keystoreFile = file("release.jks")
                    keystoreFile.writeBytes(decoded)
                    storeFile = keystoreFile
                    storePassword = envOrNull("STOREPASS")
                    keyAlias = envOrNull("KEYALIAS") ?: "puber"
                    keyPassword = envOrNull("KEYPASS") ?: envOrNull("STOREPASS")
                }
                // 3. CI: release.jks already present (e.g. copied in CI step) + env vars
                envOrNull("STOREPASS") != null -> {
                    storePassword = envOrNull("STOREPASS")
                    keyAlias = envOrNull("KEYALIAS") ?: "puber"
                    keyPassword = envOrNull("KEYPASS") ?: envOrNull("STOREPASS")
                    storeFile = file("release.jks")
                }
                // 4. Fallback: debug signing (allows build without release keys)
                else -> {
                    storeFile = file("debug.jks")
                }
            }
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
