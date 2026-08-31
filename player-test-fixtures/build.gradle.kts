plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kino.puber.playertestfixtures"
    compileSdk = Versions.CompileSdk

    defaultConfig {
        minSdk = Versions.MinSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets["main"].assets.srcDir("src/main/assets")
    sourceSets["test"].resources.srcDir("src/main/assets")

    compileOptions {
        sourceCompatibility = Versions.JavaVersionCompat
        targetCompatibility = Versions.JavaVersionCompat
    }
}

kotlin {
    jvmToolchain(Versions.JavaVersionCompat.majorVersion.toInt())
}

dependencies {
    implementation(libs.mockwebserver3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

tasks.withType<Test>().configureEach {
    classpath += files("src/main/assets")
}
