<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { createTask, updateTask } from '@/api/task'
import { listTags } from '@/api/tag'
import { PRIORITY_TEXT } from '@/utils/constants'

const props = defineProps({
  visible: Boolean,
  task: { type: Object, default: null }
})
const emit = defineEmits(['update:visible', 'saved'])

const formRef = ref(null)
const loading = ref(false)
const allTags = ref([])
const form = ref(emptyForm())

const isEdit = computed(() => !!props.task?.id)
const title = computed(() => isEdit.value ? '编辑任务' : '新增任务')

function emptyForm() {
  return {
    title: '',
    content: '',
    startTime: null,
    dueTime: null,
    priority: 3,
    isImportant: false,
    isUrgent: false,
    tagIds: [],
    version: 0
  }
}

const rules = {
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }]
}

watch(() => props.visible, async (val) => {
  if (val) {
    if (allTags.value.length === 0) {
      try { allTags.value = await listTags() } catch { /* ignore */ }
    }
    if (props.task) {
      form.value = {
        title: props.task.title || '',
        content: props.task.content || '',
        startTime: props.task.startTime || null,
        dueTime: props.task.dueTime || null,
        priority: props.task.priority ?? 3,
        isImportant: !!props.task.isImportant,
        isUrgent: !!props.task.isUrgent,
        tagIds: props.task.tags?.map(t => t.id) || [],
        version: props.task.version ?? 0
      }
    } else {
      form.value = emptyForm()
    }
  }
})

async function onSubmit() {
  try {
    await formRef.value.validate()
  } catch { return }

  loading.value = true
  try {
    if (isEdit.value) {
      await updateTask(props.task.id, form.value)
      ElMessage.success('任务已保存')
    } else {
      await createTask(form.value)
      ElMessage.success('任务已创建')
    }
    emit('update:visible', false)
    emit('saved')
  } catch { /* request.js 已弹错 */ }
  finally { loading.value = false }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" label-position="top">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入任务标题" maxlength="255" show-word-limit />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.content" type="textarea" :rows="3" placeholder="任务详情（可选）" />
      </el-form-item>

      <div class="flex gap-4">
        <el-form-item label="开始时间" class="flex-1">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间"
            value-format="YYYY-MM-DDTHH:mm:ssZ" style="width:100%" />
        </el-form-item>
        <el-form-item label="截止时间" class="flex-1">
          <el-date-picker v-model="form.dueTime" type="datetime" placeholder="选择截止时间"
            value-format="YYYY-MM-DDTHH:mm:ssZ" style="width:100%" />
        </el-form-item>
      </div>

      <div class="flex gap-4">
        <el-form-item label="优先级" class="flex-1">
          <el-select v-model="form.priority" style="width:100%">
            <el-option v-for="(text, key) in PRIORITY_TEXT" :key="key" :value="Number(key)" :label="text" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签" class="flex-1">
          <el-select v-model="form.tagIds" multiple collapse-tags placeholder="选择标签" style="width:100%">
            <el-option v-for="tag in allTags" :key="tag.id" :value="tag.id" :label="tag.name">
              <span class="flex items-center gap-1.5">
                <span class="inline-block h-2.5 w-2.5 rounded-full" :style="{ background: tag.color }" />
                {{ tag.name }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
      </div>

      <div class="flex gap-6">
        <el-form-item label="重要">
          <el-switch v-model="form.isImportant" />
        </el-form-item>
        <el-form-item label="紧急">
          <el-switch v-model="form.isUrgent" />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="onSubmit">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>
