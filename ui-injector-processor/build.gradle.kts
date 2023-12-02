plugins {
    kotlin("jvm")
    alias(libs.plugins.mavenpublish)
}

dependencies {
    implementation(project(":ui-injector-annotations"))
    implementation(libs.ksp.processor)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
