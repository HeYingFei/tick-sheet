<script setup>
import { ref, onMounted } from 'vue'
import dayjs from 'dayjs'
import { getTimelineLogs } from '@/api/stats'
import { OPERATE_TYPE_TEXT, OPERATE_TYPE_COLOR } from '@/utils/constants'
import { formatTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'

/** 变更快照里的字段名是存储用的英文键，展示时换成用户读得懂的名字 */
const FIELD_TEXT = {
  title: '标题',
  content: '备注',
  start_time: '开始时间',
  due_time: '截止时间',
  priority: '优先级',
  is_important: '重要',
  is_urgent: '紧急',
  tag_ids: '标签',
  status: '状态',
  quadrant: '象限'
}

function fieldText(field) {
  return FIELD_TEXT[field] || field
}

const loading = ref(false)
const logs = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const operateTypes = ref([])
const includeDeleted = ref(false)
const expandedIds = ref(new Set())

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, includeDeleted: includeDeleted.value }
    if (operateTypes.value.length) params.operateTypes = operateTypes.value.join(',')
    const res = await getTimelineLogs(params)
    logs.value = res.records || []
    total.value = res.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

onMounted(load)

function onFilterChange() { page.value = 1; load() }
function onPageChange(p) { page.value = p; load() }

function toggleExpand(id) {
  if (expandedIds.value.has(id)) expandedIds.value.delete(id)
  else expandedIds.value.add(id)
}

function typeColor(type) { return OPERATE_TYPE_COLOR[type] || '#9CA3AF' }
function typeText(type) { return OPERATE_TYPE_TEXT[type] || '未知' }

// 按日期分组
function groupedLogs() {
  const groups = []
  let currentDate = ''
  for (const item of logs.value) {
    const date = dayjs(item.operateTime).format('YYYY-MM-DD')
    if (date !== currentDate) {
      currentDate = date
      groups.push({ date, items: [] })
    }
    groups[groups.length - 1].items.push(item)
  }
  return groups
}
</script>

<template>
  <div class="space-y-5">
    <PageHeader title="时间线" :readings="[{ label: '记录', value: total }]" />

    <!-- 筛选 -->
    <div class="app-card flex flex-wrap items-center gap-3 px-4 py-3">
      <el-select
        v-model="operateTypes"
        multiple
        collapse-tags
        placeholder="操作类型"
        class="!w-56"
        @change="onFilterChange"
      >
        <el-option v-for="(text, key) in OPERATE_TYPE_TEXT" :key="key" :value="Number(key)" :label="text" />
      </el-select>
      <el-checkbox v-model="includeDeleted" @change="onFilterChange">
        <span class="text-[13px] text-graphite">包含已删除任务</span>
      </el-checkbox>
    </div>

    <div
      v-if="!loading && groupedLogs().length === 0"
      class="app-card px-6 py-16 text-center text-[13px] text-faint"
    >
      还没有操作记录，创建或调整任务后会出现在这里
    </div>

    <div v-loading="loading" class="space-y-5">
      <section v-for="group in groupedLogs()" :key="group.date" class="app-card overflow-hidden">
        <header class="border-b border-rule bg-paper px-5 py-2">
          <span class="readout text-[12px] font-medium text-graphite">{{ group.date }}</span>
        </header>

        <ol class="ml-8 border-l border-rule py-4">
          <li v-for="item in group.items" :key="item.id" class="relative pb-5 pl-6 last:pb-0">
            <!-- 节点用同色描边环压住竖线，形成断点；left 相对 li 左边缘，正好落在 1px 边框上 -->
            <span
              class="absolute -left-[4.5px] top-[5px] h-2 w-2 rounded-full ring-4 ring-surface"
              :style="{ background: typeColor(item.operateType) }"
            />

            <div class="flex flex-wrap items-baseline gap-x-2.5 gap-y-1">
              <span class="text-[13px] font-medium text-ink">{{ item.taskTitle }}</span>
              <span class="text-[11px] font-medium" :style="{ color: typeColor(item.operateType) }">
                {{ typeText(item.operateType) }}
              </span>
              <span class="readout text-[11px] text-faint">{{ formatTime(item.operateTime) }}</span>
            </div>

            <p class="mt-1 text-[12px] text-graphite">{{ item.operateDesc }}</p>

            <!-- 字段级变更 -->
            <div v-if="item.operateDetail">
              <button
                class="mt-1.5 text-[11px] text-faint transition-colors hover:text-ink"
                @click="toggleExpand(item.id)"
              >
                {{ expandedIds.has(item.id) ? '收起变更' : '查看变更' }}
              </button>

              <div
                v-if="expandedIds.has(item.id)"
                class="mt-2 rounded-card border border-rule bg-paper px-3 py-2"
              >
                <div
                  v-for="(change, field) in item.operateDetail"
                  :key="field"
                  class="flex flex-wrap items-baseline gap-x-2 py-0.5 text-[11px]"
                >
                  <span class="w-16 shrink-0 text-faint">{{ fieldText(field) }}</span>
                  <span class="readout text-status-overdue line-through">{{ change.old ?? '空' }}</span>
                  <span class="text-faint">→</span>
                  <span class="readout text-status-done">{{ change.new ?? '空' }}</span>
                </div>
              </div>
            </div>
          </li>
        </ol>
      </section>
    </div>

    <div v-if="total > size" class="flex justify-center">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>
