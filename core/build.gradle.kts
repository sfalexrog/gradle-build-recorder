plugins {
    alias(libs.plugins.convention.jvm.module)
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // Gradle tooling event definitions
    compileOnly(libs.gradle.tooling.api)
}
