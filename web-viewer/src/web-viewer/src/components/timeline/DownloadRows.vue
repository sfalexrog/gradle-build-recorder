<script setup lang="ts">
import { duration } from '../../recording'
import type { Download } from '../../recording'
defineProps<{
  bars: { item: Download; lane: number }[]
  top: number
  height: number
  x: (time: number) => number
  selected?: Download | null
  span: number
}>()
defineEmits<{ select: [download: Download] }>()
</script>
<template>
  <g v-if="bars.length" class="download-rows">
    <rect x="0" :y="top" width="1000" :height="height" fill="#fcf9f2" />
    <rect
      v-for="(bar, index) in bars"
      :key="index"
      :data-download="bar.item.uri"
      :data-download-index="index"
      tabindex="0"
      role="button"
      :aria-label="`Select download ${bar.item.uri}`"
      :aria-pressed="selected === bar.item"
      @keydown.enter.prevent="$emit('select', bar.item)"
      @keydown.space.prevent="$emit('select', bar.item)"
      :x="x(bar.item.startTime)"
      :y="top + 10 + bar.lane * 22"
      :width="Math.max(2, ((bar.item.endTime - bar.item.startTime) / span) * 1000)"
      height="15"
      rx="2"
      :class="[
        'download',
        {
          'download-failed': bar.item.result.type !== 'success',
          'selected-download': selected === bar.item,
        },
      ]"
    >
      <title>
        {{ bar.item.uri }} · {{ duration(bar.item.endTime - bar.item.startTime) }} ·
        {{ bar.item.bytesDownloaded.toLocaleString() }} bytes · {{ bar.item.result.type
        }}{{ bar.item.result.type === 'failure' ? ': ' + bar.item.result.messages.join('; ') : '' }}
      </title>
    </rect>
  </g>
</template>
