pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "CircuitKotlinInject"
include(":ui-injector-annotations")
include(":ui-injector-processor")
include("sample")
