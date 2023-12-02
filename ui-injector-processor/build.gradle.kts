plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":ui-injector-annotations"))
    implementation(libs.ksp.processor)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
