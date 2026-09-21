<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageTasks, deleteTask, changeStatus, batchDelete, batchStatus } from '@/api/task'
import { listTags } from '@/api/tag'
import {
  TASK_STATUS, TASK_STATUS_TEXT, TASK_STATUS_TAG, STATUS_TRANSITIONS,
  PRIORITY_TEXT, PRIORITY_TAG, resolveQuadrant
} from '@/utils/constants'
import { formatTime, formatRelative } from '@/utils/format'
import TaskFormDialog from '@/components/TaskFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'

// ---- state ----
const loading = ref(false)
const tasks = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const allTags = ref([])

// 筛选
const filterForm = ref({ keyword: '', status: [], tagIds: [], sortBy: 'createTime', sortOrder: 'desc' })
// 多选
const selectedIds = ref([])
// 弹窗
const dialogVisible = ref(false)
const editingTask = ref(null)

// ---- 加载 ----
async function load() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      sortBy: filterForm.value.sortBy,
      sortOrder: filterForm.value.sortOrder
    }
    if (filterForm.value.keyword) params.keyword = filterForm.value.keyword
    if (filterForm.value.status.length) params.status = filterForm.value.status.join(',')
    if (filterForm.value.tagIds.length) params.tagIds = filterForm.value.tagIds.join(',')

    const res = await pageTasks(params)
    tasks.value = res.records || []
    total.value = res.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

onMounted(async () => {
  try { allTags.value = await listTags() } catch { /* ignore */ }
  load()
})

// ---- 操作 ----
function onCreate() {
  editingTask.value = null
  dialogVisible.value = true
}

function onEdit(task) {
  editingTask.value = task
  dialogVisible.value = true
}

async function onDelete(task) {
  try {
    await ElMessageBox.confirm(`确认删除「${task.title}」？`, '删除确认', { type: 'warning' })
    await deleteTask(task.id)
    ElMessage.success('已删除')
    load()
  } catch { /* cancel */ }
}

async function onStatusChange(task, status) {
  try {
    await changeStatus(task.id, status)
    ElMessage.success('状态已更新')
    load()
  } catch { /* ignore */ }
}

async function onBatchStatus(status) {
  if (!selectedIds.value.length) return
  try {
    await batchStatus(selectedIds.value, status)
    ElMessage.success('批量操作成功')
    selectedIds.value = []
    load()
  } catch { /* ignore */ }
}

async function onBatchDelete() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个任务？`, '批量删除', { type: 'warning' })
    await batchDelete(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    load()
  } catch { /* cancel */ }
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

function onPageChange(p) { page.value = p; load() }
function onSizeChange(s) { size.value = s; page.value = 1; load() }

/**
 * 序号跨页连续。
 * 每页都从 1 开始的话翻页后编号会重复，看不出「这是第几个」。
 */
function indexMethod(index) {
  return (page.value - 1) * size.value + index + 1
}

function handleSort({ prop, order }) {
  filterForm.value.sortBy = prop || 'createTime'
  filterForm.value.sortOrder = order === 'ascending' ? 'asc' : 'desc'
  load()
}

const selectedStatuses = computed(() => {
  return filterForm.value.status.map(Number)
})

function onFilterChange() { page.value = 1; load() }

function statusActions(status) {
  return (STATUS_TRANSITIONS[status] || []).map(s => ({ status: s, text: TASK_STATUS_TEXT[s] }))
}
</script>

<template>
  <div class="space-y-5">
    <PageHeader title="任务列表" :readings="[{ label: '匹配', value: total }]" />

    <!-- 筛选 -->
    <div class="app-card p-4">
      <div class="flex flex-wrap items-center gap-3">
        <el-input
          v-model="filterForm.keyword"
          placeholder="搜索标题或备注"
          clearable
          class="!w-56"
          @keyup.enter="onFilterChange"
          @clear="onFilterChange"
        />
        <el-select
          v-model="filterForm.status"
          multiple
          collapse-tags
          placeholder="状态"
          class="!w-44"
          @change="onFilterChange"
        >
          <el-option v-for="(text, key) in TASK_STATUS_TEXT" :key="key" :value="Number(key)" :label="text" />
        </el-select>
        <el-select
          v-model="filterForm.tagIds"
          multiple
          collapse-tags
          placeholder="标签"
          class="!w-44"
          @change="onFilterChange"
        >
          <el-option v-for="tag in allTags" :key="tag.id" :value="tag.id" :label="tag.name">
            <span class="flex items-center gap-1.5">
              <span class="inline-block h-2 w-2 rounded-full" :style="{ background: tag.color }" />
              {{ tag.name }}
            </span>
          </el-option>
        </el-select>

        <div class="ml-auto flex items-center gap-2">
          <el-button type="primary" @click="onCreate">
            <el-icon class="mr-1.5"><Plus /></el-icon>新增任务
          </el-button>
          <el-dropdown v-if="selectedIds.length" trigger="click">
            <el-button>已选 {{ selectedIds.length }} 项</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="onBatchStatus(TASK_STATUS.DONE)">标记为已完成</el-dropdown-item>
                <el-dropdown-item @click="onBatchStatus(TASK_STATUS.CANCELED)">标记为已取消</el-dropdown-item>
                <el-dropdown-item divided @click="onBatchDelete">
                  <span class="text-status-overdue">删除所选</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <!-- 表格 -->
    <div class="app-card overflow-hidden">
      <el-table
        :data="tasks"
        v-loading="loading"
        @selection-change="onSelectionChange"
        @sort-change="handleSort"
        row-key="id"
      >
        <el-table-column type="selection" width="42" />
        <!-- 用插槽而不是 type="index"：序号要走等宽的淡色小字，与其他数字列一致 -->
        <el-table-column label="序号" width="56">
          <template #default="{ $index }">
            <span class="readout text-[11px] text-faint">{{ indexMethod($index) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" sortable="custom">
          <template #default="{ row }">
            <!-- 用 gap 代替 margin：整组居中时 margin 会把内容推偏 -->
            <span class="inline-flex max-w-full items-baseline justify-center gap-2">
              <span
                class="min-w-0 cursor-pointer truncate text-[13px] text-ink transition-colors hover:text-primary"
                @click="onEdit(row)"
              >
                {{ row.title }}
              </span>
              <span v-if="row.overdue" class="readout shrink-0 text-[11px] text-status-overdue">
                逾期 {{ row.overdueDays }} 天
              </span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="TASK_STATUS_TAG[row.status]" disable-transitions>
              {{ TASK_STATUS_TEXT[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="88" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="PRIORITY_TAG[row.priority]" disable-transitions>
              {{ PRIORITY_TEXT[row.priority] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="象限" width="116">
          <template #default="{ row }">
            <span class="inline-flex items-center gap-1.5 text-[11px] text-graphite">
              <span
                class="h-1.5 w-1.5 shrink-0 rounded-full"
                :style="{ background: resolveQuadrant(row.isImportant, row.isUrgent).color }"
              />
              {{ resolveQuadrant(row.isImportant, row.isUrgent).name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="标签" width="150">
          <template #default="{ row }">
            <span class="inline-flex flex-wrap items-center justify-center gap-x-2 gap-y-0.5">
              <span
                v-for="tag in row.tags"
                :key="tag.id"
                class="inline-flex items-center gap-1 text-[11px] text-graphite"
              >
                <span class="h-1.5 w-1.5 shrink-0 rounded-full" :style="{ background: tag.color }" />
                {{ tag.name }}
              </span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="dueTime" label="截止时间" width="132" sortable="custom">
          <template #default="{ row }">
            <span v-if="row.dueTime" class="readout text-[11px] text-graphite">{{ formatTime(row.dueTime) }}</span>
            <span v-else class="text-[11px] text-faint">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="132" sortable="custom">
          <template #default="{ row }">
            <span class="readout text-[11px] text-faint">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="176" fixed="right">
          <template #default="{ row }">
            <!-- 外层用 flex 统一间距与垂直对齐：组件库对相邻按钮的默认外边距会被
                 el-dropdown 隔断，直接排会出现一边 12px、一边 0px 的错位 -->
            <div class="action-cell">
              <el-dropdown trigger="click">
                <el-button text size="small">状态</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="action in statusActions(row.status)"
                      :key="action.status"
                      @click="onStatusChange(row, action.status)"
                    >
                      {{ action.text }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button text size="small" @click="onEdit(row)">编辑</el-button>
              <el-button text size="small" @click="onDelete(row)">
                <span class="text-status-overdue">删除</span>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end border-t border-rule px-4 py-3">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <TaskFormDialog v-model:visible="dialogVisible" :task="editingTask" @saved="load" />
  </div>
</template>

<style scoped>
/*
 * 操作列按钮组。
 * 间距一律由容器决定：组件库的 `.el-button + .el-button` 只认相邻兄弟，
 * 被 el-dropdown 隔断后会失效，导致按钮间距忽宽忽窄。
 */
.action-cell {
  display: flex;
  align-items: center;
  /* flex 子项不吃 text-align，需要单独居中 */
  justify-content: center;
}

.action-cell :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 表格里的文字按钮不需要组件库那么宽的水平内边距 */
.action-cell :deep(.el-button) {
  padding-left: 8px;
  padding-right: 8px;
}

/*
 * 表头与数据一律居中。
 * 单元格对齐由组件库以行内样式给出，必须 !important 才能覆盖。
 */
:deep(.el-table th.el-table__cell .cell),
:deep(.el-table td.el-table__cell .cell) {
  text-align: center !important;
}
</style>
