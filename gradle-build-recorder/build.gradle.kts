plugins {
    alias(libs.plugins.convention.jvm.module)
    alias(libs.plugins.shadow)
    application
}

group = "com.github.sfalexrog.gradlebuildrecorder"
version = "0.0.1"

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
    // TODO(sfalexrog): figure out why minimizing breaks stuff.
    // So far the minimizer shrinks the resulting jar by about 1.5 megabytes
    // (9.1MB -> 7.6MB), so we can live without it. Eventually though we'd like
    // to reduce the binary size further (after all, it's not doing anything
    // complicated, so it has no business being this large)
    // minimize {
    //    exclude(dependency(libs.slf4j.simple))
    // }
    archiveBaseName = "gbr"
}

dependencies {
    implementation(libs.gradle.tooling.api)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clikt)
    implementation(projects.core)
    runtimeOnly(libs.slf4j.simple)
}
