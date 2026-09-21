<script setup>
/**
 * 页面头 —— 全局统一的"记录头"。
 *
 * 左侧页面名，右侧关键读数，下方一条刻度尺。
 * 首页会传入 marks，把刻度尺变成真实的今日时段标尺；
 * 其余页面保持安静的分隔作用。
 */
import TickRule from './TickRule.vue'

defineProps({
  title: { type: String, required: true },
  /** 右侧读数：[{ label, value }] */
  readings: { type: Array, default: () => [] },
  /** 刻度高亮下标，透传给刻度尺 */
  marks: { type: Array, default: () => [] },
  count: { type: Number, default: 24 }
})
</script>

<template>
  <header>
    <div class="flex flex-wrap items-baseline justify-between gap-x-8 gap-y-3">
      <h1 class="text-[19px] font-semibold tracking-tight text-ink">{{ title }}</h1>

      <div v-if="readings.length" class="flex items-baseline gap-6">
        <div v-for="r in readings" :key="r.label" class="flex items-baseline gap-1.5">
          <span class="readout text-[15px] font-medium text-ink">{{ r.value ?? '—' }}</span>
          <span class="eyebrow">{{ r.label }}</span>
        </div>
      </div>
    </div>

    <TickRule class="mt-3" :count="count" :marks="marks" />
  </header>
</template>
