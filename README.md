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

### Build events file structure

TODO (but will be described in a separate file).

For now the [files in the models folder](core/src/main/kotlin/com/github/sfalexrog/gradlebuildrecorder/models) should give a rough idea. Start with [`GradleBuildRecord.kt`](core/src/main/kotlin/com/github/sfalexrog/gradlebuildrecorder/models/GradleBuildRecord.kt), as this file contains the top-level objects.

## LLM usage notice

This project should be considered a learning exercise. As such, using an LLM to generate any code (no matter how insignificant) is considered detrimental to the experience, and LLM usage is therefore considered inappropriate. The author reserves the right to reconsider this stance eventually, and will update current notice in case an LLM is used.

"Chat mode" LLM usage is known to produce unreliable results, and is therefore also frowned upon. Primary sources for the development of this software are the Gradle project documentation, Gradle project sources, and discussions on Gradle forums.

In other words, all code in this repository should be considered hand-written unless explicitly noted otherwise.
