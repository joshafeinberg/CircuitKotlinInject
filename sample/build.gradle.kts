plugins {
    kotlin("jvm")
    alias(libs.plugins.ksp)
    alias(libs.plugins.jetbrains.compose)
}

dependencies {
    implementation(libs.circuit.foundation)
    implementation(libs.kotlin.inject.runtime)

    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation(compose.material3)

    implementation(project(":ui-injector-annotations"))

    ksp(project(":ui-injector-processor"))
    ksp(libs.kotlin.inject.compiler.ksp)
}

ksp {
    arg("circuit.codegen.package", "com.joshafeinberg.circuitkotlininject.sample")
    arg("circuit.codegen.parent.component", "com.joshafeinberg.circuitkotlininject.sample.ParentComponent")
}