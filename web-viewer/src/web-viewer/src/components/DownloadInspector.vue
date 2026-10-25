<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { duration, overlaps, intersectsTime } from '../recording'
import type { Download, Range } from '../recording'
const props = defineProps<{
  downloads: Download[]
  selected: Download | null
  selection: Range | null
  cursor?: number | null
  timeLabel: (time: number) => string
}>()
defineEmits<{ select: [download: Download]; clear: [] }>()
const limit = ref(200)
const visible = computed(() =>
  props.downloads
    .filter((item) =>
      props.cursor != null
        ? intersectsTime(item, props.cursor)
        : !props.selection || overlaps(item, props.selection),
    )
    .sort((a, b) => a.startTime - b.startTime),
)
watch(
  () => [props.downloads, props.selection, props.cursor],
  () => {
    limit.value = 200
  },
)
</script>
<template>
  <section class="download-inspector" aria-label="Download inspector">
    <div class="panel-heading">
      <div>
        <p class="eyebrow">DOWNLOAD INSPECTOR</p>
        <h2>File downloads</h2>
      </div>
    </div>
    <p class="task-count">
      {{ visible.length }} downloads
      {{ cursor != null ? 'at time cursor' : selection ? 'in selected range' : 'in recording' }}
    </p>
    <div v-if="selected" class="download-summary">
      <strong>{{ selected.uri }}</strong>
      <p>
        {{ duration(selected.endTime - selected.startTime) }} ·
        {{ selected.bytesDownloaded.toLocaleString() }} bytes · {{ selected.result.type }}
      </p>
      <p>{{ timeLabel(selected.startTime) }} → {{ timeLabel(selected.endTime) }}</p>
      <p v-if="selected.result.type === 'failure'" class="failure-text">
        {{ selected.result.messages.join('\n') }}
      </p>
      <p v-if="cursor == null && selection && !overlaps(selected, selection)" class="muted">
        Selected download is outside the current range.
      </p>
      <button @click="$emit('clear')">Clear download selection</button>
    </div>
    <div class="task-list download-list">
      <article
        v-for="(item, index) in visible.slice(0, limit)"
        :key="index"
        :class="{ 'inspected-download': selected === item }"
      >
        <div>
          <button
            class="task-select download-select"
            :aria-pressed="selected === item"
            @click="$emit('select', item)"
          >
            {{ item.uri }}</button
          ><span class="status-text">{{ item.result.type }}</span>
        </div>
        <b>{{ duration(item.endTime - item.startTime) }}</b
        ><small>{{ item.bytesDownloaded.toLocaleString() }} bytes</small
        ><small>{{ timeLabel(item.startTime) }} → {{ timeLabel(item.endTime) }}</small>
        <small v-if="item.result.type === 'failure'" class="failure-text">{{
          item.result.messages.join('\n')
        }}</small>
      </article>
      <p v-if="!visible.length" class="muted">
        {{ cursor != null ? 'No downloads at this time.' : 'No downloads overlap this range.' }}
      </p>
      <button v-if="visible.length > limit" class="more" @click="limit += 200">
        Show more downloads ({{ visible.length - limit }} remaining)
      </button>
    </div>
  </section>
</template>
