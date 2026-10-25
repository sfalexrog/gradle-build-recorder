# Project guidelines

The aim of this project is twofold:

* first, it should provide a useful tool for recording and profiling Gradle builds;
* second, it is a learning exercise about the possibilities and limitations of Gradle Tooling API.

As such, there are several rules about working with this project:

* the code should be simple to understand first and foremost. Deep inheritance is very much discouraged. Mutability should only be reached for when absolutely necessary;
* the code should be reasonably well-tested. We don't strive for 100% code coverage, but tricky/underdocumented parts should definitely have several tests covering them;
* the code should be well-documented. Kdoc usage is very welcome, as well as adding comments to tricky parts of functions/methods;
* as the code in this project is expected to run in the same environment as the build, it should strive to conserve resources if possible, while maintaining the rules above.

## Project structuring and building

The project will eventually have multiple parts (see [PLAN.md](docs/PLAN.md)). Some of these parts will use Kotlin, other may require reaching for something like Node.js. Still, the final artifact(s) should be built with a single Gradle invocation specifying a single target selector.

Shared code should be put into its own module(s). Shared configuration logic should be put into `build-logic` as convention plugins.

Avoid common Gradle pitfalls. The project should be compatible with Gradle's configuration cache.

## Sharing data schemas

The project uses `kotlinx.serialization` to produce serialized recordings. These recordings will then be loaded by a web app. The schemas should be shared between the recorder and the web app, and should be produced automatically by running a Gradle task.

Produced schemas will be committed to the repository and therefore should be reproducible. A non-reproducible schema is considered a bug.

Shared schemas are generated in the project `core`. The task to generate schemas is called `regenerateSchema`.

The source of truth for generated schemas are files in the `core` project.
