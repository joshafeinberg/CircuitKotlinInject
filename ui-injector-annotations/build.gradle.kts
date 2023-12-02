plugins {
    kotlin("multiplatform")
    alias(libs.plugins.mavenpublish)
}

kotlin {
    jvm {
        jvmToolchain(17)
    }
    js(IR) {
        browser()
    }
    sourceSets {
        commonMain {
            dependencies {
                compileOnly(libs.circuit.foundation)
            }
        }
    }
}
