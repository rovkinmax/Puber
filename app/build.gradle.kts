import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
}

val currentVersion = "1.0.0"

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

android {
    namespace = "com.kino.puber"
    compileSdk = Versions.CompileSdk

    defaultConfig {
        applicationId = "com.kino.puber"
        minSdk = Versions.MinSdk
        targetSdk = Versions.TargetSdk
        versionCode = Versions.DebugVersionCode
        versionName = currentVersion

        buildFeatures.compose = true
        buildFeatures.buildConfig = true

        // Add CLIENT_SECRET to BuildConfig
        buildConfigField("String", "CLIENT_SECRET", "\"${getClientSecret()}\"")
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
            try {
                val keystorePropertiesFile = file("keystore.properties")
                if (keystorePropertiesFile.exists()) {
                    val keystoreProperties = Properties()
                    keystoreProperties.load(FileInputStream(keystorePropertiesFile))

                    keyAlias = keystoreProperties["keyAlias"] as String
                    keyPassword = keystoreProperties["keyPassword"] as String
                    storePassword = keystoreProperties["storePassword"] as String
                    storeFile = file("release.jks")
                } else {
                    // option for CI
                    val storePassEnv = System.getenv("STOREPASS")
                    if (storePassEnv != null && storePassEnv.isNotEmpty()) {
                        storePassword = storePassEnv
                        keyAlias = System.getenv("KEYALIAS")
                        keyPassword = System.getenv("KEYPASS")
                        storeFile = file("release.jks")
                    }
                }
            } catch (e: Exception) {
                println(e)
                storeFile = file("debug.jks")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
    }


    productFlavors {
        create("dev") {
            dimension = "buildType"
            versionName = "$currentVersion-$name"
            applicationIdSuffix = ".stage"
            resValue("string", "app_name", "Puber(${name.uppercaseFirstChar()})")
        }

        create("prod") {
            dimension = "buildType"
            versionCode = Versions.VersionCode
            resValue("string", "app_name", "Puber")
        }
    }

}

kotlin {
    jvmToolchain(Versions.JavaVersionCompat.majorVersion.toInt())
    sourceSets.all {
        languageSettings.languageVersion = "2.0"
    }
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
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

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

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)

    detektPlugins(libs.detekt.compose.rules)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}