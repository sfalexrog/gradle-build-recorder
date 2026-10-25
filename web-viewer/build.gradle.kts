plugins {
    base
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
tasks.assemble { dependsOn(buildWebViewer) }
tasks.check { dependsOn(testWebViewer) }
