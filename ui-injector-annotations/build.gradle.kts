plugins {
    kotlin("multiplatform")
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
