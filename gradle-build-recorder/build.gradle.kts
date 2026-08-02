plugins {
    alias(libs.plugins.convention.jvm.module)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("com.github.sfalexrog.gradlebuildrecorder.EntrypointKt")
}


tasks.withType(Tar::class.java) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType(Zip::class.java) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.shadowJar {
    minimize {
        exclude(dependency(libs.slf4j.simple))
    }
}


dependencies {
    implementation(libs.gradle.tooling.api)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clikt)
    implementation(projects.core)
    runtimeOnly(libs.slf4j.simple)
}
