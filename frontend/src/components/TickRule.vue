<script setup>
/**
 * 刻度尺 —— 全局视觉签名。
 *
 * 默认是等分的纯刻度分隔线；传入 marks 后，对应下标的刻度会以主色拉长。
 * 首页据此把它变成真实的「今日时段」标尺，而不是纯装饰。
 */
defineProps({
  /** 刻度总数 */
  count: { type: Number, default: 24 },
  /** 需要高亮的下标，从 0 开始 */
  marks: { type: Array, default: () => [] },
  /** 每几格出现一个长刻度 */
  majorEvery: { type: Number, default: 6 }
})
</script>

<template>
  <div class="tick-scale" aria-hidden="true">
    <span
      v-for="i in count"
      :key="i"
      :class="{
        'is-major': i % majorEvery === 0,
        'is-marked': marks.includes(i - 1)
      }"
    />
  </div>
</template>
