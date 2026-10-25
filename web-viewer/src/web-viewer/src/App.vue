<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import {
  bounds,
  clampView,
  duration,
  groupTasks,
  intersectsTime,
  parallelCountAt,
  overlaps,
  parallelism,
  parseRecording,
  projectOf,
  status,
  taskDependencies,
} from './recording'
import type { Download, GradleBuildRecord, Range, Task } from './recording'
import BuildTimeline from './components/timeline/BuildTimeline.vue'
import DownloadInspector from './components/DownloadInspector.vue'

const recording = shallowRef<GradleBuildRecord | null>(null)
// Keep the original JSON so export preserves every field and is independent of UI filters.
const recordingJson = shallowRef('')
const filename = ref('')
const error = ref('')
const loading = ref(false)
const absolute = ref(false)
const mode = ref<'select' | 'pan'>('select')
const selectedProject = ref<string | null>(null)
const selectedTask = shallowRef<Task | null>(null)
const inspector = ref<'tasks' | 'downloads'>('tasks')
const selectedDownload = shallowRef<Download | null>(null)
const namedDependencies = computed(() => {
  const links = taskDependencies(groups.value, selectedTask.value)
  return (selectedTask.value?.dependencies || []).map((path) => ({
    path,
    task: links.find((link) => link.source.task.taskPath === path)?.source.task,
  }))
})
const cursor = ref<number | null>(null)
const pinnedTime = ref<number | null>(null)
const cursorTaskCount = computed(() =>
  cursor.value === null ? 0 : parallelCountAt(concurrency.value, cursor.value),
)
const cursorDownloads = computed(() =>
  cursor.value === null
    ? []
    : (recording.value?.fileDownloadEvents || []).filter((item) =>
        intersectsTime(item, cursor.value!),
      ),
)
const cursorDownloadCount = computed(
  () => cursorDownloads.value.filter((item) => item.endTime > item.startTime).length,
)
const selection = ref<Range | null>(null)
const view = ref<Range>({ start: 0, end: 1 })
const fileInput = ref<HTMLInputElement>()
const search = ref('')
const stream = ref('all')
const logLimit = ref(300)
const full = computed(() => (recording.value ? bounds(recording.value) : { start: 0, end: 1 }))
const span = computed(() => view.value.end - view.value.start)
const groups = computed(() => groupTasks(recording.value?.taskExecutionEvents || []))
const concurrency = computed(() =>
  parallelism(recording.value?.taskExecutionEvents || [], full.value),
)
const actualPeak = computed(() =>
  concurrency.value.reduce((peak, point) => Math.max(peak, point.count), 0),
)
const visibleTasks = computed(() =>
  (recording.value?.taskExecutionEvents || [])
    .filter((task) =>
      cursor.value !== null
        ? intersectsTime(task.event, cursor.value)
        : (!selectedProject.value || projectOf(task) === selectedProject.value) &&
          (!selection.value || overlaps(task.event, selection.value)),
    )
    .sort((a, b) => a.event.startTime - b.event.startTime),
)
const logs = computed(() =>
  [
    ...(recording.value?.stdoutLines || []).map((line) => ({ ...line, stream: 'stdout' })),
    ...(recording.value?.stderrLines || []).map((line) => ({ ...line, stream: 'stderr' })),
  ].sort((a, b) => a.startTime - b.startTime),
)
const filteredLogs = computed(() =>
  logs.value.filter(
    (line) =>
      (!selection.value || overlaps(line, selection.value)) &&
      (stream.value === 'all' || line.stream === stream.value) &&
      line.line.toLowerCase().includes(search.value.toLowerCase()),
  ),
)
const buildName = computed(
  () =>
    recording.value?.invocation.projectPath.split(/[\\/]/).filter(Boolean).pop() ||
    'Build recording',
)
const timeLabel = (time: number) =>
  absolute.value
    ? new Date(time).toLocaleTimeString([], {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        ...(span.value < 10000 ? { fractionalSecondDigits: 3 as const } : {}),
      })
    : duration(time - full.value.start)
function selectTask(task: Task) {
  inspector.value = 'tasks'
  selectedTask.value = task
  selectedProject.value = projectOf(task)
}
function selectProject(project: string | null) {
  inspector.value = 'tasks'
  selectedProject.value = project
  selectedTask.value = null
}
function selectDownload(download: Download) {
  selectedDownload.value = download
  inspector.value = 'downloads'
}
function selectRange(range: Range | null) {
  selection.value = range
  logLimit.value = 300
}
async function load(file?: File) {
  if (!file) return
  loading.value = true
  error.value = ''
  try {
    loadRecording(await file.text(), file.name)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Could not read the recording.'
  } finally {
    loading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}
function loadRecording(json: string, name: string) {
  const parsed = parseRecording(json)
  recordingJson.value = json
  recording.value = parsed
  filename.value = name
  view.value = bounds(parsed)
  selection.value = null
  cursor.value = null
  pinnedTime.value = null
  selectedProject.value = null
  selectedTask.value = null
  selectedDownload.value = null
  inspector.value = 'tasks'
  logLimit.value = 300
  search.value = ''
}
onMounted(() => {
  try {
    // The HTML resource holds a JSON string; an empty string means no embedded recording.
    const json: unknown = JSON.parse(document.getElementById('gbr-recording')?.textContent || '""')
    if (typeof json !== 'string') throw new Error('Embedded recording must be a JSON string.')
    if (json !== '') loadRecording(json, 'Embedded recording')
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Could not read the embedded recording.'
  }
})
function exportJson() {
  if (!recording.value) return
  const url = URL.createObjectURL(new Blob([recordingJson.value], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = /\.json$/i.test(filename.value) ? filename.value : 'recording.json'
  document.body.append(link)
  try {
    link.click()
  } finally {
    link.remove()
    // Let the browser start the download before releasing its backing data.
    setTimeout(() => URL.revokeObjectURL(url), 0)
  }
}
function zoom(factor: number, anchor = (view.value.start + view.value.end) / 2) {
  const ratio = (anchor - view.value.start) / span.value
  const nextSpan = Math.min(full.value.end - full.value.start, Math.max(1, span.value * factor))
  view.value = clampView(
    { start: anchor - nextSpan * ratio, end: anchor + nextSpan * (1 - ratio) },
    full.value,
  )
}
function fit() {
  view.value = { ...full.value }
}
</script>

<template>
  <main @dragover.prevent @drop.prevent="load($event.dataTransfer?.files[0])">
    <header class="app-header">
      <div class="brand">
        <span class="brand-icon">▥</span>
        <div><strong>Build Recorder</strong><span>GRADLE TIMELINE EXPLORER</span></div>
      </div>
      <div class="header-actions">
        <button v-if="recording" @click="exportJson">Export JSON</button>
        <label class="button primary"
          ><span>↥</span> Open recording<input
            ref="fileInput"
            type="file"
            accept=".json,application/json"
            @change="load(($event.target as HTMLInputElement).files?.[0])"
        /></label>
      </div>
    </header>
    <div v-if="error" role="alert" class="error">{{ error }}</div>
    <section v-if="!recording" class="empty-state">
      <span class="empty-icon">▥</span>
      <p class="eyebrow">EVERY TASK. EVERY MILLISECOND.</p>
      <h1>A clearer view of your build.</h1>
      <p>
        Explore task execution, find parallel work, and connect the timeline to your build logs.
      </p>
      <button class="primary" @click="fileInput?.click()">Open a build recording</button>
      <p class="hint">or drop a recorder JSON file anywhere here · stays on your device</p>
    </section>
    <template v-else>
      <section class="build-heading">
        <div>
          <p class="eyebrow">
            BUILD RECORDING
            <span
              class="result"
              :class="{ failure: recording.invocation.result.type !== 'success' }"
              >{{ recording.invocation.result.type.replace(/_/g, ' ') }}</span
            >
          </p>
          <h1>{{ buildName }}</h1>
          <p class="subtitle">
            {{ recording.invocation.gradleArgs.join(' ') || 'Default tasks' }}
            <span>· {{ filename }}</span>
          </p>
        </div>
        <div class="build-date">
          {{ new Date(recording.invocation.startTime).toLocaleString()
          }}<small
            >Local time · UTC{{
              -new Date(recording.invocation.startTime).getTimezoneOffset() / 60 >= 0 ? '+' : ''
            }}{{ -new Date(recording.invocation.startTime).getTimezoneOffset() / 60 }}</small
          >
        </div>
      </section>
      <section class="stats">
        <div>
          <span>BUILD DURATION</span
          ><strong>{{
            duration(recording.invocation.endTime - recording.invocation.startTime)
          }}</strong>
        </div>
        <div>
          <span>TASKS</span><strong>{{ recording.taskExecutionEvents.length }}</strong>
        </div>
        <div>
          <span>PROJECTS</span><strong>{{ groups.length }}</strong>
        </div>
        <div>
          <span>PEAK PARALLEL TASKS</span><strong>{{ actualPeak }} <small>tasks</small></strong>
        </div>
        <div>
          <span>FROM CACHE / UP-TO-DATE</span
          ><strong>{{
            recording.taskExecutionEvents.filter((t) =>
              ['cached', 'up-to-date'].includes(status(t)),
            ).length
          }}</strong>
        </div>
      </section>
      <section class="workspace">
        <div class="timeline-panel">
          <div class="toolbar">
            <div>
              <h2>Execution timeline</h2>
              <span class="muted">{{ groups.length }} projects</span>
            </div>
            <div class="controls">
              <select v-model="absolute" aria-label="Time display">
                <option :value="false">Relative time</option>
                <option :value="true">Local time</option></select
              ><button :class="{ active: mode === 'select' }" @click="mode = 'select'">
                Select</button
              ><button :class="{ active: mode === 'pan' }" @click="mode = 'pan'">Pan</button
              ><button aria-label="Zoom out" @click="zoom(1.5)">−</button
              ><button aria-label="Zoom in" @click="zoom(1 / 1.5)">+</button
              ><button @click="fit">Fit</button>
            </div>
          </div>
          <div class="legend">
            <span><i class="executed" />Executed</span><span><i class="cached" />Cached</span
            ><span><i class="up-to-date" />Up-to-date</span><span><i class="skipped" />Skipped</span
            ><span><i class="failed" />Failed</span><span><i class="download" />Downloads</span
            ><small>Scroll rows · Shift-drag to pan · Ctrl/⌘ + scroll to zoom</small>
          </div>
          <BuildTimeline
            :groups="groups"
            :downloads="recording.fileDownloadEvents"
            :full="full"
            v-model:view="view"
            v-model:pinned-time="pinnedTime"
            @update:cursor="cursor = $event"
            :selection="selection"
            :selected-project="selectedProject"
            :selected-task="selectedTask"
            :selected-download="selectedDownload"
            :mode="mode"
            :concurrency="concurrency"
            :time-label="timeLabel"
            @update:selection="selectRange"
            @select-project="selectProject"
            @select-task="selectTask"
            @select-download="selectDownload"
            @show-downloads="inspector = 'downloads'"
            @zoom="zoom"
          />
          <div v-if="cursor !== null" class="cursor-status">
            <span
              >{{ timeLabel(cursor) }} · {{ cursorTaskCount }} running tasks ·
              {{ cursorDownloadCount }}
              {{ cursorDownloadCount === 1 ? 'download' : 'downloads' }} in progress ·
              {{ pinnedTime !== null ? 'Pinned' : 'Following pointer' }}</span
            >
            <button v-if="pinnedTime !== null" @click="pinnedTime = null">
              Clear time cursor (Esc)
            </button>
          </div>
          <div class="range-bar">
            <span v-if="selection"
              >{{ timeLabel(selection.start) }} → {{ timeLabel(selection.end) }}
              <strong>· {{ duration(selection.end - selection.start) }}</strong></span
            ><span v-else class="muted">Select a time range to inspect tasks and filter logs.</span
            ><button v-if="selection" @click="selection = null">Clear range (Esc) ×</button>
          </div>
        </div>
        <aside class="task-panel">
          <div class="inspector-tabs" aria-label="Inspector panels">
            <button :aria-pressed="inspector === 'tasks'" @click="inspector = 'tasks'">
              Tasks {{ cursor !== null ? `(${visibleTasks.length})` : '' }}</button
            ><button :aria-pressed="inspector === 'downloads'" @click="inspector = 'downloads'">
              Downloads ({{
                cursor !== null ? cursorDownloads.length : recording.fileDownloadEvents.length
              }})
            </button>
          </div>
          <DownloadInspector
            v-if="inspector === 'downloads'"
            :downloads="recording.fileDownloadEvents"
            :selected="selectedDownload"
            :cursor="cursor"
            :selection="selection"
            :time-label="timeLabel"
            @select="selectDownload"
            @clear="selectedDownload = null"
          />
          <template v-else>
            <div class="panel-heading">
              <div>
                <p class="eyebrow">TASK INSPECTOR</p>
                <h2>
                  {{ cursor !== null ? 'At time cursor' : selectedProject || 'All projects' }}
                </h2>
              </div>
              <button
                v-if="selectedProject"
                aria-label="Clear project filter"
                @click="selectProject(null)"
              >
                ×
              </button>
            </div>
            <p class="task-count">
              {{ visibleTasks.length }} tasks
              {{
                cursor !== null
                  ? 'at time cursor'
                  : selection
                    ? 'in selected range'
                    : 'in recording'
              }}
            </p>
            <div v-if="selectedTask" class="dependency-summary">
              <strong>{{ selectedTask.taskPath }}</strong>
              <p>
                {{ selectedTask.dependencies.length }} direct dependencies · purple bars and arrows
              </p>
              <ul class="dependency-names">
                <li v-for="dependency in namedDependencies" :key="dependency.path">
                  <button
                    v-if="dependency.task"
                    class="dependency-select"
                    @click="selectTask(dependency.task)"
                  >
                    {{ dependency.path }}</button
                  ><span v-else>{{ dependency.path }} <small>(not recorded)</small></span>
                </li>
              </ul>
              <button @click="selectedTask = null">Clear task selection</button>
            </div>
            <div class="task-list">
              <article
                v-for="(task, index) in visibleTasks"
                :key="index"
                :class="{ 'inspected-task': selectedTask === task }"
              >
                <div>
                  <button
                    class="task-select"
                    :aria-pressed="selectedTask === task"
                    @click="selectTask(task)"
                  >
                    {{ task.taskPath }}</button
                  ><span :class="['status-text', status(task)]">{{ status(task) }}</span>
                </div>
                <b>{{ duration(task.event.endTime - task.event.startTime) }}</b
                ><small
                  >{{ timeLabel(task.event.startTime) }} →
                  {{ timeLabel(task.event.endTime) }}</small
                ><small v-if="task.event.type === 'failed'" class="failure-text">{{
                  task.event.failures.join('\n')
                }}</small
                ><small v-if="task.event.type === 'skipped'">{{ task.event.reason }}</small
                ><small v-if="task.dependencies.length"
                  >Depends on {{ task.dependencies.join(', ') }}</small
                >
              </article>
              <p v-if="!visibleTasks.length" class="muted">
                {{ cursor !== null ? 'No tasks at this time.' : 'No tasks overlap this range.' }}
              </p>
            </div>
          </template>
        </aside>
      </section>
      <section class="logs-panel">
        <div class="toolbar">
          <div>
            <h2>Build output</h2>
            <span class="muted"
              >{{ filteredLogs.length }} / {{ logs.length }} lines
              {{ selection ? '· range filtered' : '' }}</span
            >
          </div>
          <div class="controls">
            <input
              v-model="search"
              type="search"
              placeholder="Search output…"
              aria-label="Search logs"
            /><select v-model="stream" aria-label="Output stream">
              <option value="all">All output</option>
              <option>stdout</option>
              <option>stderr</option>
            </select>
          </div>
        </div>
        <div class="log-lines">
          <div
            v-for="(line, index) in filteredLogs.slice(0, logLimit)"
            :key="index"
            :class="{ stderr: line.stream === 'stderr' }"
          >
            <time>{{ timeLabel(line.startTime) }}</time
            ><span>{{ line.stream }}</span>
            <pre>{{ line.line }}</pre>
          </div>
          <p v-if="!filteredLogs.length" class="muted">No output matches the current filters.</p>
        </div>
        <button v-if="filteredLogs.length > logLimit" class="more" @click="logLimit += 500">
          Show 500 more lines ({{ filteredLogs.length - logLimit }} remaining)
        </button>
      </section>
      <footer>GRADLE BUILD RECORDER <span>All recording data stays in your browser.</span></footer>
    </template>
    <div v-if="loading" class="loading" role="status">Loading recording…</div>
  </main>
</template>
