# Build recording viewer

Vue + TypeScript + SVG viewer for JSON files produced by `gradle-build-recorder`.
Files are read locally: no server, upload, or network connection is used at runtime.

From this directory:

```sh
npm ci
npm run dev
npm run test:unit -- --run
npm run build:html
```

`npm run build:html` (also `npm run build`) type-checks the app and produces
`dist/index.html` with its CSS, JavaScript, Vue runtime, and schema validator embedded.
Open it directly with a browser, including offline. Node must satisfy `package.json`'s engines.
From the repository root, `./gradlew :web-viewer:build` installs locked npm dependencies,
checks the viewer, and builds the same HTML. `./gradlew assemble` includes the viewer.

## Embedding from Kotlin

The `web-viewer` project also builds a JVM library. `packageWebViewer` copies the built
single HTML file into generated resources; `processResources`, `jar`, and consumers
of the project dependency build it automatically. The recorder depends on this library,
so its application distribution and shadow JAR include the viewer.

```kotlin
import com.github.sfalexrog.gradlebuildrecorder.viewer.RecordingHtml

val html = RecordingHtml.render(recordingJson)
outputFile.writeText(html, Charsets.UTF_8)
```

Pass the serialized recording JSON directly. `RecordingHtml.render()` returns the
empty viewer. The HTML contains `<script id="gbr-recording" type="application/json">""</script>`;
the API replaces this default with an HTML-safe JSON string. The viewer validates
and opens it at startup. Opening or dropping another JSON file still works, including
after an invalid embedded recording. The CLI writes this HTML when its output file
has a `.html` extension; other extensions produce JSON.

With a recording open, **Export JSON** downloads its original JSON, including all
events and logs regardless of the current filters. Embedded recordings download as
`recording.json`; opened JSON files retain their filename.

## Exploring a recording

Open or drop a recorder JSON file. Projects have fixed left-aligned labels; hovering a
row highlights its label. Click a project or a task bar to inspect the project's tasks
on the right. Use **Project name** / **First task start** to choose alphabetical or
earliest-task ordering; ties use project name. Overlapping tasks occupy separate lanes. Bar tooltips include the task
path, elapsed duration, and outcome. Cached, up-to-date, skipped, and failed tasks have
distinct colors. Task durations show their full lifetime even when a range is selected.

Drag across the SVG to select a time range. The inspector shows tasks that overlap
that range, intersected with the selected project. Output below the axis is filtered
by the same range, independently of the project filter. Intervals are half-open
`[start, end)`; zero-duration events are included when their timestamp falls in the
range. Clear the range or project independently.

Move the pointer over the execution SVG to show a vertical time cursor. Click to pin
it; Escape or **Clear time cursor** releases it. Escape also clears any selected time range. An unpinned cursor follows the pointer
and disappears outside the SVG. The cursor spans the task/download rows and parallel
graph, whose label shows the actual running task count at that instant. Pinning uses
an absolute timestamp, so zoom and pan keep the pin attached to build time.

While the cursor is present, the inspector tabs list every task/download intersecting
that instant across all projects, overriding the project/range filters for these lists.
Releasing the cursor outside the timeline restores those filters. Log range filtering
remains independent. Task/download intervals use `[start, end)`; instantaneous events
intersect only at their exact timestamp. Cached, up-to-date, skipped and instantaneous
tasks can appear in the intersected list but do not count as running tasks in the graph.
Dragging still selects a range or pans, without pinning the cursor.

Use Pan mode or Shift-drag to pan. Vertical scroll moves through timeline rows;
horizontal scroll or Shift-scroll pans time. Ctrl/Command-scroll zooms around
the pointer. Focus the timeline to use Up/Down, Page Up/Down, and Home/End to scroll
rows; Left/Right pans time. The +/− controls zoom around the center; Fit resets the viewport.
Relative ticks adapt to zoom. Local time uses the browser's timezone, converting UTC
epoch milliseconds with `Date`. Logs can also be filtered by stream and text. Output
is displayed incrementally to avoid rendering every line in a large recording.

The parallelism graph stays pinned to the top of the timeline while task/download rows
scroll beneath it. Its cursor, selection, download overlay, and time scale stay aligned
with those rows. The parallelism chart counts executing (successful or failed) task intervals. Cached,
up-to-date, skipped, and zero-duration tasks are excluded. Boundaries at the same
instant are aggregated to avoid artificial spikes. It shows task concurrency, not
worker-thread or CPU utilization. Select a task bar (or its inspector button) to highlight its direct dependencies
and draw arrows from their completion to the selected task. Enter/Space also selects
a focused task bar. The selected task lists clickable dependency names; selecting one
updates the project and task selection. Missing dependencies are named as not recorded; repeated task
paths resolve to the latest execution ending before the selected task, falling back
to the first recorded execution when no preceding one exists. The timeline does not
infer project dependencies or a critical path.

File downloads have a separate lane group below the projects. Overlapping transfers
use separate lanes, and tooltips show the URI, duration, bytes downloaded, and outcome.
Select a transfer bar (or use the Downloads inspector tab) to see its URL, duration,
bytes, timestamps, and result, including failure messages. The download list respects
the selected time range and renders incrementally. The Downloads row label also opens
the inspector. Enter/Space selects a focused transfer bar.

Amber bands on the parallel-task graph show when at least one download was active.
The Downloads time beside the graph is the union of all transfer lifetimes across
the recording, including failed transfers; overlapping intervals count only once,
and zero-duration transfers add no time. The overlay shares the time axis and follows
zoom/pan. Downloads do not contribute to the task concurrency count.

SVG rendering and navigation live in `src/components/timeline/`: `BuildTimeline`
coordinates input and layout, with separate task rows, download rows, dependency
links, parallel graph, and time axis components. `App.vue` owns recording and filter state.

## Shared schema

`core/schemas/GradleBuildRecord.json` is the only schema source. It is imported directly
for runtime validation (Ajv draft 2020-12); TypeScript declarations are generated from
it with `npm run schema:types` (also run before development, builds, and tests).
Never edit `src/generated/recording.d.ts` manually. After changing Kotlin models:

```sh
# Repository root
./gradlew :core:regenerateSchema
# This directory
npm run schema:types
```

Commit the regenerated core schema and declarations. Generation is deterministic.
The viewer rejects schema mismatches and invalid/reversed timestamps while keeping
the previously opened recording available. SVG work scales with the number of tasks;
very large recordings may take time to parse and render. Recordings must contain the
recorder's complete top-level fields (including empty lists).
