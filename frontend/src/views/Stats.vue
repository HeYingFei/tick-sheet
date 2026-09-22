<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { getOverview, getTrend, getQuadrantStats, getTagStats, getCompletion } from '@/api/stats'
import { formatPercent } from '@/utils/format'
import { useThemeStore } from '@/store/theme'
import PageHeader from '@/components/PageHeader.vue'

const theme = useThemeStore()

const loading = ref(true)
const range = ref('week')
const overview = ref({})
const completion = ref({})
/** 趋势窗口（天）。以后端回传的生效值为准，缺省时先按本地配置渲染标题 */
const trendDays = ref(theme.statsWindowDays)

const trendChartRef = ref(null)
const quadrantChartRef = ref(null)
const tagChartRef = ref(null)

/** 图表数据留一份，主题切换时用同一份数据重绘，避免重新请求 */
const chartData = { trend: [], quadrant: [], tags: [] }

function cssVar(name) {
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return raw ? `rgb(${raw})` : ''
}

const headerReadings = computed(() => [
  { label: '总任务', value: overview.value.totalTasks ?? 0 }
])

const metrics = computed(() => [
  { label: '周期基数', value: completion.value.base ?? '—', tone: 'text-ink' },
  { label: '完成率', value: formatPercent(completion.value.completionRate), tone: 'text-status-done' },
  { label: '逾期率', value: formatPercent(completion.value.overdueRate), tone: 'text-status-overdue' },
  { label: '日均新增', value: completion.value.avgNewPerDay ?? '—', tone: 'text-ink' },
  { label: '核心占比', value: formatPercent(completion.value.importantRate), tone: 'text-primary' }
])

function chartOf(el) {
  return el ? echarts.getInstanceByDom(el) || echarts.init(el) : null
}

/** 折线图与柱状图共用的轴样式 */
function axisStyle() {
  return {
    axisLine: { lineStyle: { color: cssVar('--c-rule') } },
    axisTick: { show: false },
    axisLabel: { color: cssVar('--c-faint'), fontSize: 11, fontFamily: 'IBM Plex Mono' }
  }
}

function renderTrend() {
  const chart = chartOf(trendChartRef.value)
  if (!chart || chartData.trend.length === 0) return

  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['新增', '完成'],
      bottom: 0,
      icon: 'rect',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: cssVar('--c-graphite'), fontSize: 11 }
    },
    grid: { left: 34, right: 12, top: 14, bottom: 40 },
    xAxis: { type: 'category', data: chartData.trend.map((d) => d.date.slice(5)), ...axisStyle() },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: cssVar('--c-rule'), type: 'dotted' } },
      ...axisStyle()
    },
    series: [
      {
        name: '新增',
        type: 'line',
        data: chartData.trend.map((d) => d.newCount),
        symbolSize: 5,
        itemStyle: { color: cssVar('--c-primary') },
        lineStyle: { color: cssVar('--c-primary'), width: 1.5 }
      },
      {
        name: '完成',
        type: 'line',
        data: chartData.trend.map((d) => d.doneCount),
        symbolSize: 5,
        itemStyle: { color: cssVar('--c-status-done') },
        lineStyle: { color: cssVar('--c-status-done'), width: 1.5 }
      }
    ]
  })
}

function renderQuadrant() {
  const chart = chartOf(quadrantChartRef.value)
  if (!chart || chartData.quadrant.length === 0) return

  const palette = ['--c-q1', '--c-q2', '--c-q3', '--c-q4']
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: {
      bottom: 0,
      icon: 'rect',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: cssVar('--c-graphite'), fontSize: 11 }
    },
    series: [
      {
        type: 'pie',
        radius: ['46%', '68%'],
        center: ['50%', '44%'],
        label: { show: false },
        itemStyle: { borderColor: cssVar('--c-surface'), borderWidth: 2 },
        data: chartData.quadrant.map((q, i) => ({
          value: q.count,
          name: q.name,
          itemStyle: { color: cssVar(palette[i]) }
        }))
      }
    ]
  })
}

function renderTags() {
  const chart = chartOf(tagChartRef.value)
  if (!chart) return

  const items = chartData.tags.filter((t) => t.count > 0)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 34, right: 12, top: 14, bottom: 34 },
    xAxis: {
      type: 'category',
      data: items.map((t) => t.name),
      ...axisStyle(),
      axisLabel: {
        color: cssVar('--c-faint'),
        fontSize: 11,
        rotate: items.length > 6 ? 30 : 0
      }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: cssVar('--c-rule'), type: 'dotted' } },
      ...axisStyle()
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 34,
        data: items.map((t) => ({
          value: t.count,
          itemStyle: { color: t.color || cssVar('--c-primary'), borderRadius: [2, 2, 0, 0] }
        }))
      }
    ]
  })
}

function renderAll() {
  renderTrend()
  renderQuadrant()
  renderTags()
}

async function loadAll() {
  loading.value = true
  try {
    const [ov, tr, qd, td, comp] = await Promise.all([
      getOverview(),
      getTrend(),
      getQuadrantStats(),
      getTagStats(),
      getCompletion({ range: range.value })
    ])
    overview.value = ov
    completion.value = comp
    chartData.trend = tr.items || []
    trendDays.value = tr.days || theme.statsWindowDays
    chartData.quadrant = qd.items || []
    chartData.tags = td.items || []
    renderAll()
  } catch {
    /* 失败提示由请求拦截器统一处理 */
  } finally {
    loading.value = false
  }
}

function onResize() {
  for (const r of [trendChartRef, quadrantChartRef, tagChartRef]) {
    if (r.value) echarts.getInstanceByDom(r.value)?.resize()
  }
}

function onRangeChange() {
  loadAll()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})

// 主题切换后需要重新从 CSS 变量取色
watch(() => theme.mode, () => setTimeout(renderAll, 0))
</script>

<template>
  <div v-loading="loading" class="space-y-5">
    <PageHeader title="数据统计" :readings="headerReadings" />

    <!-- 统计周期 -->
    <div class="app-card flex flex-wrap items-center gap-3 px-4 py-3">
      <span class="eyebrow">统计周期</span>
      <el-radio-group v-model="range" size="small" @change="onRangeChange">
        <el-radio-button value="today">今日</el-radio-button>
        <el-radio-button value="week">本周</el-radio-button>
        <el-radio-button value="month">本月</el-radio-button>
        <el-radio-button value="year">全年</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 指标条 -->
    <div class="app-card flex flex-wrap divide-x divide-rule">
      <div v-for="m in metrics" :key="m.label" class="min-w-[132px] flex-1 px-5 py-3.5">
        <div class="eyebrow">{{ m.label }}</div>
        <div class="readout mt-1.5 text-[19px] font-medium" :class="m.tone">{{ m.value }}</div>
      </div>
    </div>

    <div class="grid gap-5 lg:grid-cols-2">
      <section class="app-card">
        <header class="border-b border-rule px-5 py-3">
          <h2 class="text-[13px] font-semibold text-ink">任务趋势（近 {{ trendDays }} 天）</h2>
        </header>
        <div ref="trendChartRef" class="px-3 py-3" style="height: 268px" />
      </section>

      <section class="app-card">
        <header class="border-b border-rule px-5 py-3">
          <h2 class="text-[13px] font-semibold text-ink">象限分布</h2>
        </header>
        <div ref="quadrantChartRef" class="px-3 py-3" style="height: 268px" />
      </section>
    </div>

    <section class="app-card">
      <header class="border-b border-rule px-5 py-3">
        <h2 class="text-[13px] font-semibold text-ink">标签分布</h2>
      </header>
      <div ref="tagChartRef" class="px-3 py-3" style="height: 268px" />
    </section>
  </div>
</template>
