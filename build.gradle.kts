group = "com.joshafeinberg"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ktlint) apply true
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.mavenpublish) apply false
}
