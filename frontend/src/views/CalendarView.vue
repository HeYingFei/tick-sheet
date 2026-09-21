<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import isoWeek from 'dayjs/plugin/isoWeek'
import { getCalendarTasks } from '@/api/stats'
import { TASK_STATUS, TASK_STATUS_TEXT, resolveQuadrant } from '@/utils/constants'
import { formatTime } from '@/utils/format'
import TaskFormDialog from '@/components/TaskFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'

dayjs.extend(isoWeek)

const currentMonth = ref(dayjs())
const days = ref([])
const tasksByDate = ref({})
const loading = ref(false)
const selectedDate = ref(null)
const selectedTasks = ref([])
const dialogVisible = ref(false)
const editingTask = ref(null)

/** 当前视图内的任务总数（含为补齐整周而显示的上下月日期） */
const viewTaskCount = computed(() =>
  Object.values(tasksByDate.value).reduce((sum, list) => sum + list.length, 0)
)

/** 左侧色点直接编码象限 */
function quadrantColor(task) {
  return resolveQuadrant(task.isImportant, task.isUrgent).color
}

async function loadMonth() {
  loading.value = true
  try {
    const start = currentMonth.value.startOf('month').startOf('isoWeek')
    const end = currentMonth.value.endOf('month').endOf('isoWeek').add(1, 'day')

    const tasks = await getCalendarTasks({
      startTime: start.format('YYYY-MM-DDTHH:mm:ssZ'),
      endTime: end.format('YYYY-MM-DDTHH:mm:ssZ')
    })

    // 按日期分组
    const byDate = {}
    for (const task of tasks) {
      const key = dayjs(task.dueTime || task.createTime).format('YYYY-MM-DD')
      if (!byDate[key]) byDate[key] = []
      byDate[key].push(task)
    }
    tasksByDate.value = byDate

    // 生成日历格子
    const grid = []
    let cursor = start.clone()
    while (cursor.isBefore(end)) {
      const dateStr = cursor.format('YYYY-MM-DD')
      grid.push({
        date: dateStr,
        day: cursor.date(),
        isCurrentMonth: cursor.month() === currentMonth.value.month(),
        isToday: cursor.isSame(dayjs(), 'day'),
        tasks: byDate[dateStr] || []
      })
      cursor = cursor.add(1, 'day')
    }
    days.value = grid
  } catch { /* ignore */ }
  finally { loading.value = false }
}

onMounted(loadMonth)
watch(currentMonth, loadMonth)

function prevMonth() { currentMonth.value = currentMonth.value.subtract(1, 'month') }
function nextMonth() { currentMonth.value = currentMonth.value.add(1, 'month') }
function goToday() { currentMonth.value = dayjs() }

function selectDate(day) {
  selectedDate.value = day.date
  selectedTasks.value = day.tasks
}

function editTask(task) {
  editingTask.value = task
  dialogVisible.value = true
}
</script>

<template>
  <div v-loading="loading" class="space-y-5">
    <PageHeader title="日历" :readings="[{ label: '视图内', value: viewTaskCount }]" />

    <!-- 月份切换 -->
    <div class="app-card flex items-center gap-2 px-4 py-3">
      <el-button text @click="prevMonth"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="readout min-w-[104px] text-center text-[13px] font-medium text-ink">
        {{ currentMonth.format('YYYY-MM') }}
      </span>
      <el-button text @click="nextMonth"><el-icon><ArrowRight /></el-icon></el-button>
      <el-button text size="small" class="!ml-2" @click="goToday">回到本月</el-button>
    </div>

    <div class="flex flex-col gap-5 lg:flex-row">
      <!-- 月历：用 1px 网格线分隔，接近纸质月历的样子 -->
      <section class="app-card flex-1 overflow-hidden">
        <div class="grid grid-cols-7 gap-px border-b border-rule bg-rule">
          <div v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w" class="bg-paper py-2 text-center">
            <span class="eyebrow">{{ w }}</span>
          </div>
        </div>

        <div class="grid grid-cols-7 gap-px bg-rule">
          <div
            v-for="day in days"
            :key="day.date"
            class="min-h-[86px] cursor-pointer bg-surface p-1.5 transition-colors"
            :class="[
              day.isCurrentMonth ? 'hover:bg-rule/30' : 'opacity-40',
              selectedDate === day.date ? 'bg-primary/[0.06]' : ''
            ]"
            @click="selectDate(day)"
          >
            <div class="flex items-center justify-between">
              <span
                class="readout flex h-5 min-w-[20px] items-center justify-center text-[11px]"
                :class="day.isToday ? 'rounded-full bg-ink px-1 font-medium text-paper' : 'text-graphite'"
              >
                {{ day.day }}
              </span>
              <span v-if="day.tasks.length" class="readout text-[10px] text-faint">{{ day.tasks.length }}</span>
            </div>

            <div class="mt-1 space-y-0.5">
              <div v-for="task in day.tasks.slice(0, 3)" :key="task.id" class="flex items-center gap-1">
                <span class="h-1 w-1 shrink-0 rounded-full" :style="{ background: quadrantColor(task) }" />
                <span
                  class="truncate text-[11px]"
                  :class="task.overdue ? 'text-status-overdue' : 'text-graphite'"
                >
                  {{ task.title }}
                </span>
              </div>
              <div v-if="day.tasks.length > 3" class="readout pl-2 text-[10px] text-faint">
                +{{ day.tasks.length - 3 }}
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 选中日期的任务 -->
      <aside class="app-card flex w-full shrink-0 flex-col lg:w-[268px]">
        <header class="border-b border-rule px-4 py-3">
          <div class="text-[13px] font-semibold text-ink">
            {{ selectedDate ? dayjs(selectedDate).format('M 月 D 日') : '未选择日期' }}
          </div>
          <div class="eyebrow mt-1">
            {{ selectedDate ? `${selectedTasks.length} 项任务` : '点击左侧日期查看当天任务' }}
          </div>
        </header>

        <div v-if="!selectedDate" class="px-4 py-12 text-center text-[12px] text-faint">
          点击日历中的任意一天
        </div>
        <div v-else-if="selectedTasks.length === 0" class="px-4 py-12 text-center text-[12px] text-faint">
          当天没有任务
        </div>
        <ul v-else class="flex-1 overflow-auto lg:max-h-[46vh]">
          <li
            v-for="task in selectedTasks"
            :key="task.id"
            class="cursor-pointer border-b border-rule/60 px-4 py-2.5 transition-colors last:border-b-0
                   hover:bg-rule/25"
            @click="editTask(task)"
          >
            <div class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full" :style="{ background: quadrantColor(task) }" />
              <div class="min-w-0 flex-1">
                <div class="truncate text-[13px] text-ink">{{ task.title }}</div>
                <div class="mt-1 flex items-center gap-2">
                  <span class="eyebrow">{{ TASK_STATUS_TEXT[task.status] }}</span>
                  <span v-if="task.dueTime" class="readout text-[11px] text-faint">
                    {{ formatTime(task.dueTime) }}
                  </span>
                </div>
              </div>
            </div>
          </li>
        </ul>
      </aside>
    </div>

    <TaskFormDialog v-model:visible="dialogVisible" :task="editingTask" @saved="loadMonth" />
  </div>
</template>
