<script setup lang="ts">
defineProps<{ ticks: number[]; x: (time: number) => number; label: (time: number) => string }>()
</script>
<template>
  <svg class="time-axis" viewBox="0 0 1000 36" preserveAspectRatio="none" aria-label="Time axis">
    <g v-for="tick in ticks" :key="tick">
      <line :x1="x(tick)" :x2="x(tick)" y1="0" y2="6" stroke="#9ba7b5" />
      <text :x="Math.min(952, Math.max(48, x(tick)))" y="24" text-anchor="middle">
        {{ label(tick) }}
      </text>
    </g>
  </svg>
</template>
