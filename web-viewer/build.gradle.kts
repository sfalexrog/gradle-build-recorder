plugins {
    alias(libs.plugins.convention.jvm.module)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

// Exec tasks use only declared paths/providers so the build supports configuration-cache reuse.
val viewerDirectory = layout.projectDirectory.dir("src/web-viewer")
val installWebDependencies by tasks.registering(Exec::class) {
    description = "Install the locked web viewer dependencies"
    workingDir(viewerDirectory)
    commandLine("npm", "ci", "--no-audit", "--no-fund")
    inputs.files(viewerDirectory.file("package.json"), viewerDirectory.file("package-lock.json"))
    outputs.dir(viewerDirectory.dir("node_modules"))
}
val buildWebViewer by tasks.registering(Exec::class) {
    description = "Build the standalone HTML recording viewer"
    dependsOn(installWebDependencies)
    workingDir(viewerDirectory)
    commandLine("npm", "run", "build:html")
    inputs.dir(viewerDirectory.dir("src"))
    inputs.dir(viewerDirectory.dir("scripts"))
    inputs.files(fileTree(viewerDirectory) { include("*.json", "*.ts", "index.html") })
    inputs.file(project(":core").layout.projectDirectory.file("schemas/GradleBuildRecord.json"))
    outputs.dir(viewerDirectory.dir("dist"))
}
val testWebViewer by tasks.registering(Exec::class) {
    description = "Test the recording viewer"
    dependsOn(installWebDependencies)
    workingDir(viewerDirectory)
    commandLine("npm", "run", "test:unit", "--", "--run")
}
val packageWebViewer by tasks.registering(Sync::class) {
    description = "Package the standalone HTML viewer as a JVM resource"
    from(buildWebViewer) { include("index.html") }
    into(layout.buildDirectory.dir("generated/resources/web-viewer"))
    rename("index.html", "recording-viewer.html")
}
sourceSets.main {
    resources.srcDir(packageWebViewer)
}

tasks.check { dependsOn(testWebViewer) }
