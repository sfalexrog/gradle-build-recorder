<script setup lang="ts">
defineProps<{ x: number; height: number; count: number; label: string; pinned: boolean }>()
</script>
<template>
  <g class="time-cursor" pointer-events="none" :data-pinned="pinned">
    <line
      :x1="x"
      :x2="x"
      y1="0"
      :y2="height"
      stroke="#d75d4b"
      stroke-width="1.5"
      :stroke-dasharray="pinned ? undefined : '5 3'"
      vector-effect="non-scaling-stroke"
    />
    <g :transform="`translate(${Math.max(0, Math.min(780, x + 8))}, 8)`">
      <rect width="212" height="23" rx="4" fill="#fff5f0" stroke="#e8b5a9" />
      <text x="8" y="15" fill="#a74738" font-size="11"
        >{{ label }} · {{ count }} running tasks</text
      >
    </g>
  </g>
</template>
