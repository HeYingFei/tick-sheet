<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getOverview, getTrend, getQuadrantStats } from '@/api/stats'
import { pageTasks, changeStatus } from '@/api/task'
import { TASK_STATUS, QUADRANTS } from '@/utils/constants'
import { formatDue, formatPercent } from '@/utils/format'
import { useThemeStore } from '@/store/theme'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()
const theme = useThemeStore()

const loading = ref(true)
const overview = ref({})
const trend = ref([])
/** 趋势窗口（天）。以后端回传的生效值为准，缺省时先按本地配置渲染标题 */
const trendDays = ref(theme.statsWindowDays)
const quadrantData = ref([])
const todoTasks = ref([])

const trendChartRef = ref(null)
const quadrantChartRef = ref(null)

/** 从 CSS 变量取色，让图表跟随深浅主题，而不是写死两组色值 */
function cssVar(name) {
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return raw ? `rgb(${raw})` : ''
}

/**
 * 今日时段标尺。
 *
 * 刻度尺本身是全局签名，这里让它承载真实信息：
 * 把列表里截止时间落在今天的任务，按小时高亮对应刻度。
 */
const todayHourMarks = computed(() => {
  const today = new Date().toDateString()
  const hours = new Set()
  for (const task of todoTasks.value) {
    const raw = task.dueTime || task.startTime
    if (!raw) continue
    const at = new Date(raw)
    if (at.toDateString() === today) hours.add(at.getHours())
  }
  return [...hours]
})

const headerReadings = computed(() => [
  { label: '总任务', value: overview.value.totalTasks ?? 0 },
  { label: '待办', value: overview.value.todoCount ?? 0 },
  { label: '进行中', value: overview.value.doingCount ?? 0 }
])

const metrics = computed(() => [
  { label: '本周完成率', value: formatPercent(overview.value.completionRate), tone: 'text-status-done' },
  { label: '逾期率', value: formatPercent(overview.value.overdueRate), tone: 'text-status-overdue' },
  { label: '今日新增', value: overview.value.todayNewCount ?? 0, tone: 'text-ink' },
  { label: '今日完成', value: overview.value.todayDoneCount ?? 0, tone: 'text-ink' }
])

async function load() {
  loading.value = true
  try {
    const [ov, tr, qd, tasks] = await Promise.all([
      getOverview(),
      getTrend(),
      getQuadrantStats(),
      pageTasks({ status: '0,1', sortBy: 'dueTime', sortOrder: 'asc', size: 10 })
    ])
    overview.value = ov
    trend.value = tr.items || []
    trendDays.value = tr.days || theme.statsWindowDays
    quadrantData.value = qd.items || []
    todoTasks.value = tasks.records || []
    renderCharts()
  } catch {
    /* 失败提示由请求拦截器统一处理 */
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  renderTrendChart()
  renderQuadrantChart()
}

/** 复用已有实例，避免重复 init 叠加 canvas */
function chartOf(el) {
  return el ? echarts.getInstanceByDom(el) || echarts.init(el) : null
}

function renderTrendChart() {
  const chart = chartOf(trendChartRef.value)
  if (!chart || trend.value.length === 0) return

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
    xAxis: {
      type: 'category',
      data: trend.value.map((d) => d.date.slice(5)),
      axisLine: { lineStyle: { color: cssVar('--c-rule') } },
      axisTick: { show: false },
      axisLabel: { color: cssVar('--c-faint'), fontSize: 11, fontFamily: 'IBM Plex Mono' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: cssVar('--c-rule'), type: 'dotted' } },
      axisLabel: { color: cssVar('--c-faint'), fontSize: 11, fontFamily: 'IBM Plex Mono' }
    },
    series: [
      {
        name: '新增',
        type: 'line',
        data: trend.value.map((d) => d.newCount),
        smooth: false,
        symbolSize: 5,
        itemStyle: { color: cssVar('--c-primary') },
        lineStyle: { color: cssVar('--c-primary'), width: 1.5 }
      },
      {
        name: '完成',
        type: 'line',
        data: trend.value.map((d) => d.doneCount),
        smooth: false,
        symbolSize: 5,
        itemStyle: { color: cssVar('--c-status-done') },
        lineStyle: { color: cssVar('--c-status-done'), width: 1.5 }
      }
    ]
  })
}

function renderQuadrantChart() {
  const chart = chartOf(quadrantChartRef.value)
  if (!chart || quadrantData.value.length === 0) return

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
        data: quadrantData.value.map((q, i) => ({
          value: q.count,
          name: q.name,
          itemStyle: { color: cssVar(palette[i]) }
        }))
      }
    ]
  })
}

async function quickDone(task) {
  try {
    await changeStatus(task.id, TASK_STATUS.DONE)
    ElMessage.success('已完成')
    todoTasks.value = todoTasks.value.filter((t) => t.id !== task.id)
    overview.value.doneCount = (overview.value.doneCount || 0) + 1
    overview.value.todoCount = Math.max(0, (overview.value.todoCount || 0) - 1)
  } catch {
    /* 失败提示由请求拦截器统一处理 */
  }
}

/** 左侧色条直接编码象限，比再挂一个标签更省视觉噪音 */
function quadrantColor(task) {
  const q = QUADRANTS.find(
    (item) => item.important === !!task.isImportant && item.urgent === !!task.isUrgent
  )
  return q ? q.color : 'var(--c-q4)'
}

function onResize() {
  for (const ref_ of [trendChartRef, quadrantChartRef]) {
    if (ref_.value) echarts.getInstanceByDom(ref_.value)?.resize()
  }
}

function onTaskSaved() {
  load()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
  window.addEventListener('task-saved', onTaskSaved)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('task-saved', onTaskSaved)
})

// 主题切换后图表颜色需要重取 CSS 变量
watch(() => theme.mode, () => setTimeout(renderCharts, 0))
</script>

<template>
  <div v-loading="loading">
    <PageHeader title="首页" :readings="headerReadings" :marks="todayHourMarks" />

    <!-- 指标条：用细竖线分隔，取代一排独立卡片 -->
    <div class="app-card mb-5 flex flex-wrap divide-x divide-rule">
      <div v-for="m in metrics" :key="m.label" class="min-w-[130px] flex-1 px-5 py-3.5">
        <div class="eyebrow">{{ m.label }}</div>
        <div class="readout mt-1.5 text-[19px] font-medium" :class="m.tone">{{ m.value }}</div>
      </div>
    </div>

    <!-- 待办与进行中：页面的主角，放在图表之前 -->
    <section class="app-card mb-5">
      <header class="flex items-center justify-between border-b border-rule px-5 py-3">
        <h2 class="text-[13px] font-semibold text-ink">待办与进行中</h2>
        <el-button text size="small" @click="router.push('/tasks')">查看全部</el-button>
      </header>

      <div v-if="todoTasks.length === 0" class="px-5 py-14 text-center text-[13px] text-faint">
        当前没有待办任务，可以开始新的安排
      </div>

      <ul v-else>
        <li
          v-for="task in todoTasks"
          :key="task.id"
          class="flex items-start gap-3 border-b border-rule/60 px-5 py-3 transition-colors
                 last:border-b-0 hover:bg-rule/25"
        >
          <button
            class="mt-px flex h-5 w-5 shrink-0 items-center justify-center rounded-full
                   border-[1.5px] border-faint text-graphite transition-colors
                   hover:border-status-done hover:bg-status-done/10 hover:text-status-done"
            :title="`标记「${task.title}」为已完成`"
            aria-label="标记为已完成"
            @click="quickDone(task)"
          >
            <el-icon :size="11"><Check /></el-icon>
          </button>

          <!-- 象限用色条的位置编码，不必再挂一个标签 -->
          <span
            class="mt-[3px] h-3.5 w-[2px] shrink-0 rounded-full"
            :style="{ background: quadrantColor(task) }"
            :title="task.quadrantName"
          />

          <div class="min-w-0 flex-1">
            <div class="flex items-baseline gap-2">
              <span class="truncate text-[13px] text-ink">{{ task.title }}</span>
              <!-- 待办是默认状态，标出来只是噪音；只有进行中值得强调 -->
              <span
                v-if="task.status === TASK_STATUS.DOING"
                class="shrink-0 text-[11px] font-medium text-primary"
              >
                进行中
              </span>
            </div>

            <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5">
              <span
                class="readout text-[11px]"
                :class="task.overdue ? 'text-status-overdue' : 'text-faint'"
              >
                {{ formatDue(task) }}
              </span>

              <span
                v-for="tag in task.tags"
                :key="tag.id"
                class="inline-flex items-center gap-1 text-[11px] text-faint"
              >
                <span class="h-1 w-1 shrink-0 rounded-full" :style="{ background: tag.color }" />
                {{ tag.name }}
              </span>
            </div>
          </div>
        </li>
      </ul>
    </section>

    <div class="grid gap-5 lg:grid-cols-2">
      <section class="app-card">
        <header class="border-b border-rule px-5 py-3">
          <h2 class="text-[13px] font-semibold text-ink">近 {{ trendDays }} 日新增与完成</h2>
        </header>
        <div ref="trendChartRef" class="px-3 py-3" style="height: 236px" />
      </section>

      <section class="app-card">
        <header class="border-b border-rule px-5 py-3">
          <h2 class="text-[13px] font-semibold text-ink">象限分布</h2>
        </header>
        <div ref="quadrantChartRef" class="px-3 py-3" style="height: 236px" />
      </section>
    </div>
  </div>
</template>
