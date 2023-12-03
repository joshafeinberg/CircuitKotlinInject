plugins {
    kotlin("jvm")
    alias(libs.plugins.ksp)
    // alias(libs.plugins.mavenpublish)
}

dependencies {
    implementation(project(":ui-injector-annotations"))
    implementation(libs.ksp.processor)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.dagger)
    implementation(libs.anvil.annotations)
    implementation(libs.kotlin.inject.runtime)
    implementation(libs.autoService.annotations)

    ksp(libs.autoService.ksp)
}
