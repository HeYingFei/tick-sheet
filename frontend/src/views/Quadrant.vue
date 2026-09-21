<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getQuadrantBoard, changeQuadrant, changeStatus, changeOrder, deleteTask, updateTask } from '@/api/task'
import { TASK_STATUS, PRIORITY_TEXT, QUADRANTS } from '@/utils/constants'
import { formatDue, formatPercent } from '@/utils/format'
import TaskFormDialog from '@/components/TaskFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'

const loading = ref(true)
const board = ref([])
const dialogVisible = ref(false)
const editingTask = ref(null)
const draggingTask = ref(null)
const dragOverQuadrant = ref(null)

/**
 * 按艾森豪威尔矩阵的位置摆放象限：纵轴是重要性，横轴是紧急性。
 * 右上「重要且紧急」、左上「重要不紧急」、右下「紧急不重要」、左下「都不」。
 * 让位置本身传达含义，而不是并排四个彼此无关的卡片。
 */
const MATRIX_ORDER = [2, 1, 4, 3]

const gridBoard = computed(() => {
  const byQuadrant = new Map(board.value.map((q) => [q.quadrant, q]))
  return MATRIX_ORDER.map((n) => byQuadrant.get(n)).filter(Boolean)
})

const boardTotal = computed(() => board.value.reduce((sum, q) => sum + (q.total || 0), 0))

async function load() {
  loading.value = true
  try {
    const data = await getQuadrantBoard()
    board.value = data.quadrants || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

onMounted(load)

function getColor(quadrant) {
  return QUADRANTS[quadrant - 1]?.color || '#9CA3AF'
}

function openEdit(task) {
  editingTask.value = task
  dialogVisible.value = true
}

async function quickDone(task) {
  try {
    await changeStatus(task.id, TASK_STATUS.DONE)
    ElMessage.success('已完成')
    load()
  } catch { /* ignore */ }
}

async function onDelete(task) {
  try {
    await ElMessageBox.confirm(`确认删除「${task.title}」？`, '删除确认', { type: 'warning' })
    await deleteTask(task.id)
    ElMessage.success('已删除')
    load()
  } catch { /* cancel */ }
}

function onDragStart(task, event) {
  draggingTask.value = task
  event.dataTransfer.effectAllowed = 'move'
  // 部分浏览器只有在写入数据后才会继续触发 drag/drop
  event.dataTransfer.setData('text/plain', String(task.id))
}

function onDragEnd() {
  draggingTask.value = null
  dragOverQuadrant.value = null
}

/**
 * 拖拽换象限。
 *
 * 后端按设计方案 §4.3 不静默修改优先级，只返回建议值，
 * 是否同步由用户二次确认，确认后单独提交一次编辑。
 */
async function onDrop(quadrant) {
  const task = draggingTask.value
  draggingTask.value = null
  dragOverQuadrant.value = null

  const target = QUADRANTS[quadrant - 1]
  if (!task || !target) return
  // 拖回原象限，无需请求
  if (task.isImportant === target.important && task.isUrgent === target.urgent) return

  try {
    const result = await changeQuadrant(task.id, {
      isImportant: target.important,
      isUrgent: target.urgent
    })
    if (result.suggestPriority != null) {
      await syncPriority(result)
    }
    ElMessage.success('象限已更新')
    load()
  } catch { /* ignore */ }
}

async function syncPriority(task) {
  try {
    await ElMessageBox.confirm(
      `建议将优先级调整为「${PRIORITY_TEXT[task.suggestPriority]}」，是否同步？`,
      '优先级建议',
      { confirmButtonText: '调整', cancelButtonText: '保持原值', type: 'info' }
    )
  } catch {
    return // 用户选择保持原值
  }
  await updateTask(task.id, {
    title: task.title,
    content: task.content,
    startTime: task.startTime,
    dueTime: task.dueTime,
    priority: task.suggestPriority,
    isImportant: task.isImportant,
    isUrgent: task.isUrgent,
    tagIds: task.tags?.map((t) => t.id) || [],
    version: task.version
  })
}
</script>

<template>
  <div v-loading="loading" class="space-y-5">
    <PageHeader title="四象限" :readings="[{ label: '在办', value: boardTotal }]" />

    <!-- 艾森豪威尔矩阵：纵轴重要性、横轴紧急性，用 1px 分隔线画出十字 -->
    <section class="app-card overflow-hidden">
      <div class="grid gap-px bg-rule md:grid-cols-2">
        <div
          v-for="q in gridBoard"
          :key="q.quadrant"
          class="flex flex-col bg-surface transition-colors"
          :class="dragOverQuadrant === q.quadrant ? 'bg-primary/[0.05]' : ''"
          @dragover.prevent="dragOverQuadrant = q.quadrant"
          @drop.prevent="onDrop(q.quadrant)"
        >
          <header class="flex items-center justify-between border-b border-rule px-4 py-2.5">
            <div class="flex items-center gap-2">
              <span class="h-2 w-2 shrink-0 rounded-full" :style="{ background: getColor(q.quadrant) }" />
              <h2 class="text-[13px] font-semibold text-ink">{{ q.name }}</h2>
              <span class="eyebrow">{{ q.action }}</span>
            </div>
            <div class="flex items-baseline gap-3">
              <span class="readout text-[11px] text-graphite">{{ q.doneCount }}/{{ q.total }}</span>
              <span class="readout text-[11px]" :style="{ color: getColor(q.quadrant) }">
                {{ formatPercent(q.completionRate) }}
              </span>
            </div>
          </header>

          <div class="flex-1 space-y-1.5 overflow-auto p-3" style="max-height: 40vh">
            <p v-if="q.tasks.length === 0" class="py-10 text-center text-[12px] text-faint">
              把任务拖到这里，或先新建一个
            </p>

            <article
              v-for="task in q.tasks"
              :key="task.id"
              draggable="true"
              class="group cursor-grab rounded-card border border-rule bg-paper px-3 py-2
                     transition-colors hover:border-graphite active:cursor-grabbing"
              @dragstart="onDragStart(task, $event)"
              @dragend="onDragEnd"
            >
              <div class="flex items-start gap-2">
                <button
                  v-if="task.status !== TASK_STATUS.DONE"
                  class="mt-px flex h-5 w-5 shrink-0 items-center justify-center rounded-full
                         border-[1.5px] border-faint text-graphite transition-colors
                         hover:border-status-done hover:bg-status-done/10 hover:text-status-done"
                  :title="`标记「${task.title}」为已完成`"
                  aria-label="标记为已完成"
                  @click="quickDone(task)"
                >
                  <el-icon :size="11"><Check /></el-icon>
                </button>
                <el-icon v-else :size="20" class="mt-px shrink-0 text-status-done">
                  <CircleCheckFilled />
                </el-icon>

                <div class="min-w-0 flex-1">
                  <div
                    class="truncate text-[13px] leading-snug"
                    :class="task.status === TASK_STATUS.DONE ? 'text-faint line-through' : 'text-ink'"
                  >
                    {{ task.title }}
                  </div>

                  <div class="mt-1 flex flex-wrap items-center gap-x-2.5 gap-y-1">
                    <span class="eyebrow">{{ PRIORITY_TEXT[task.priority] }}</span>
                    <span
                      class="readout text-[11px]"
                      :class="task.overdue ? 'text-status-overdue' : 'text-faint'"
                    >
                      {{ formatDue(task) }}
                    </span>
                    <span
                      v-for="tag in task.tags"
                      :key="tag.id"
                      class="inline-flex items-center gap-1 text-[11px] text-graphite"
                    >
                      <span class="h-1.5 w-1.5 shrink-0 rounded-full" :style="{ background: tag.color }" />
                      {{ tag.name }}
                    </span>
                  </div>
                </div>

                <el-dropdown trigger="click">
                  <el-button
                    text
                    circle
                    size="small"
                    class="opacity-0 transition-opacity group-hover:opacity-100"
                  >
                    <el-icon :size="13"><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="openEdit(task)">编辑</el-dropdown-item>
                      <el-dropdown-item divided @click="onDelete(task)">
                        <span class="text-status-overdue">删除</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </article>
          </div>
        </div>
      </div>

      <footer class="hidden items-center justify-between border-t border-rule px-4 py-2 md:flex">
        <span class="eyebrow">← 不紧急</span>
        <span class="eyebrow">纵轴 重要 · 横轴 紧急</span>
        <span class="eyebrow">紧急 →</span>
      </footer>
    </section>

    <TaskFormDialog v-model:visible="dialogVisible" :task="editingTask" @saved="load" />
  </div>
</template>
