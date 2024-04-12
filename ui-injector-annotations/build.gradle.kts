import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

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
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
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
