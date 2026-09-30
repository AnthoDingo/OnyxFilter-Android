buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Kotlin plus récent que celui embarqué par AGP (Kotlin intégré, voir libs.versions.toml).
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
