plugins {
    alias(libs.plugins.convention.jvm.module)
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.schema.generator.json)
    // Gradle tooling event definitions
    compileOnly(libs.gradle.tooling.api)
}

tasks.register<JavaExec>("regenerateSchema") {
    description = "Regenerate JSON schemas"
    mainClass = "com.github.sfalexrog.gradlebuildrecorder.SchemaGeneratorKt"
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = layout.projectDirectory.dir("schemas").asFile
}
