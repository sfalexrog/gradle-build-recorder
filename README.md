# gradle-build-recorder

A simple [gradle tooling API](https://docs.gradle.org/current/userguide/tooling_api.html) program that listens and records Gradle build events.

Current development progress may be tracked by consulting the [`PLAN.md`](docs/PLAN.md) file.

## Usage

### Building `gradle-build-recorder`

Run

```bash
./gradlew :gradle-build-recorder:shadowJar
```

to produce a self-sufficient `.jar` file with `gradle-build-recorder`.

The file will be stored as `gradle-build-recorder/build/libs/gbr-<version>-all.jar`, where `<version>` is the current `gradle-build-recorder` version.

### Using `gradle-build-recorder`

General synopsis is

```text
java -jar 'path/to/gbr-<version>-all.jar' [-o|--output=<path to recording.json>] path/to/your/gradle/project -- [gradle build arguments]
```

For example, you can execute a build this way:

```bash
java -jar gbr-0.0.1-all.jar -o build-recording-1.json $HOME/AndroidStudioProjects/nowinandroid -- --refresh-dependencies :app:assembleDebug
```

This will execute a build in `$HOME/AndroidStudioProjects/nowinandroid`, passing the arguments `--refresh-dependencies` and `:app:assembleDebug` to the Gradle wrapper. Recorded events will be saved to the file `build-recording-1.json`.

If you don't specify output file name, the name will be generated automatically using the pattern `build_log_${System.currentTimeMillis()}.json`, and the file will be saved in the current directory.

Use `-o build-recording.html` to save a standalone HTML viewer with the recording
embedded (the extension check ignores case). Open it in a browser, including offline,
and use **Export JSON** to download the original recording. Other output extensions
continue to produce JSON.

### Profiling builds with async-profiler

Enable profiling with `--with-profiler true` (or `-p true`):

```bash
java -jar gbr-0.0.1-all.jar -p true --profiler-output build-profile.jfr path/to/project -- assemble
```

The first build starts a Gradle daemon with async-profiler attached through `-agentpath` and recording from JVM startup. Subsequent builds reuse a compatible profiled daemon and start a new recording at the root build operation, before initialization and configuration-cache lookup. The recorder stops profiling when that operation finishes, including on task or settings failures, and saves the completed profile to the requested path. Included builds and `buildSrc` share the root recording.

The default output is `profile_<timestamp>_%p.jfr`, where `%p` is replaced with the daemon PID. Use `--profiler-args` to change recording options; the default is `jfr,all`. Commands such as `start` and `stop`, and `file=`, are managed by the recorder. JVM arguments include the recording options, so changing those options may require a different daemon; changing the output path does not.

To run once with a fresh daemon and shut it down afterward:

```bash
java -jar gbr-0.0.1-all.jar -p true --no-daemon path/to/project -- assemble
```

`--no-daemon` can also appear among the Gradle arguments after `--`. This mode uses a private daemon registry and requests shutdown after success or failure, without stopping other clients' daemons. Because the Tooling API always uses a daemon, this is implemented by the recorder rather than forwarding Gradle's CLI flag.

The profiler supports Linux x64/ARM64 and macOS. Its extracted library and init script remain in a content-addressed directory under the system temporary directory so later recorder processes can reuse the daemon. Do not remove those files while using a profiled daemon.

Gradle's public Tooling API does not provide the required early lifecycle hooks or single-use daemon control. The implementation therefore isolates dependencies on internal Gradle APIs in `profiler.init.gradle` and `ProfilerSession`; lifecycle integration tests currently exercise Gradle 9.4.1, including configuration-cache reuse. Abruptly killing the daemon or recorder is outside the normal build-completion lifecycle.

### Build events file structure

TODO (but will be described in a separate file).

For now the [files in the models folder](core/src/main/kotlin/com/github/sfalexrog/gradlebuildrecorder/models) should give a rough idea. Start with [`GradleBuildRecord.kt`](core/src/main/kotlin/com/github/sfalexrog/gradlebuildrecorder/models/GradleBuildRecord.kt), as this file contains the top-level objects.

## LLM usage notice

As a learning exercise, this branch will be continued with extensive contributions from LLMs. As such, the code from this branch should not be used in production systems (but that is equally true for other branches of this project).
