@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.kino.puber.baselineprofile"
    compileSdk = Versions.CompileSdk

    defaultConfig {
        minSdk = 28 // Macrobenchmark requires minSdk 28+
        targetSdk = Versions.TargetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "buildType"
    productFlavors {
        create("instrumentation") {
            dimension = "buildType"
            testInstrumentationRunnerArguments["targetAppId"] = "com.kino.puber.instrumentation"
            testInstrumentationRunnerArguments["baselineMockPort"] =
                providers.gradleProperty("puber.baselineMockPort").get()
        }
    }

    targetProjectPath = ":app"
}

kotlin {
    jvmToolchain(Versions.JavaVersionCompat.majorVersion.toInt())
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.test.runner)
    implementation(libs.mockwebserver3)
}
