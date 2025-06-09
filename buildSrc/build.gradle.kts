plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    // Removed gradle plugin dependencies to avoid version conflicts
    // They should be managed through version catalog in main build.gradle.kts
}