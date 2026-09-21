<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConfig, updateConfig } from '@/api/config'
import { exportData, importData, createBackup, listBackups, restoreBackup, deleteBackup, clearData } from '@/api/migration'
import { listTags, createTag, updateTag, deleteTag } from '@/api/tag'
import { updateProfile, changePassword } from '@/api/auth'
import { useThemeStore } from '@/store/theme'
import { useAuthStore } from '@/store/auth'
import { PRIORITY_TEXT } from '@/utils/constants'
import { formatTime, formatFileSize } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()
const theme = useThemeStore()
const auth = useAuthStore()
const configForm = ref({
  theme_mode: 'light', default_priority: '3', time_format: 'YYYY-MM-DD HH:mm',
  week_start: '1', calendar_field: 'due_time'
})
const configLoading = ref(false)

// 标签管理
const tags = ref([])
const tagDialogVisible = ref(false)
const editingTag = ref(null)
const tagForm = ref({ name: '', color: '#3B82F6' })

// 备份
const backups = ref([])
const backupLoading = ref(false)

// 导入
const importFile = ref(null)
const importResult = ref(null)

// 账户管理
const profileForm = ref({ nickname: auth.nickname || '', avatarUrl: auth.avatarUrl || '' })
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

onMounted(async () => {
  try {
    const [config, tagList, backupList] = await Promise.all([
      getConfig(), listTags(), listBackups()
    ])
    if (config) Object.assign(configForm.value, config)
    tags.value = tagList || []
    backups.value = backupList || []
  } catch { /* ignore */ }
})

async function saveConfig() {
  configLoading.value = true
  try {
    await updateConfig(configForm.value)
    theme.loadFromServer()
    ElMessage.success('配置已保存')
  } catch { /* ignore */ }
  finally { configLoading.value = false }
}

// ---- 标签 ----
function openTagDialog(tag) {
  editingTag.value = tag
  tagForm.value = tag ? { name: tag.name, color: tag.color } : { name: '', color: '#3B82F6' }
  tagDialogVisible.value = true
}

async function saveTag() {
  try {
    if (editingTag.value) {
      await updateTag(editingTag.value.id, tagForm.value)
    } else {
      await createTag(tagForm.value)
    }
    ElMessage.success('标签已保存')
    tagDialogVisible.value = false
    tags.value = await listTags()
  } catch { /* ignore */ }
}

async function onDeleteTag(tag) {
  try {
    await ElMessageBox.confirm(`确认删除标签「${tag.name}」？关联的任务不受影响。`, '删除确认', { type: 'warning' })
    await deleteTag(tag.id)
    ElMessage.success('标签已删除')
    tags.value = await listTags()
  } catch { /* cancel */ }
}

// ---- 导出 ----
async function onExport() {
  try {
    const data = await exportData()
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `todo_export_${new Date().toISOString().slice(0,10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch { /* ignore */ }
}

// ---- 导入 ----
async function onImport() {
  if (!importFile.value) return
  importResult.value = null
  try {
    importResult.value = await importData(importFile.value)
    ElMessage.success('导入完成')
  } catch { /* ignore */ }
}

function onImportFileChange(file) {
  importFile.value = file.raw
}

// ---- 备份 ----
async function onCreateBackup() {
  backupLoading.value = true
  try {
    await createBackup()
    ElMessage.success('备份创建成功')
    backups.value = await listBackups()
  } catch { /* ignore */ }
  finally { backupLoading.value = false }
}

async function onRestore(backup) {
  try {
    await ElMessageBox.confirm(`确认恢复备份「${backup.fileName}」？重复的任务将跳过。`, '恢复确认', { type: 'warning' })
    const result = await restoreBackup(backup.id)
    ElMessage.success(result.message || '恢复完成')
  } catch { /* cancel */ }
}

async function onDeleteBackup(backup) {
  try {
    await ElMessageBox.confirm(`确认删除备份「${backup.fileName}」？`, '删除确认', { type: 'warning' })
    await deleteBackup(backup.id)
    ElMessage.success('备份已删除')
    backups.value = await listBackups()
  } catch { /* cancel */ }
}

async function onClearData() {
  try {
    await ElMessageBox.confirm('此操作将清空所有任务、标签和日志数据，且不可恢复！', '危险操作', {
      type: 'error',
      confirmButtonText: '确认清空',
      cancelButtonText: '取消'
    })
    await ElMessageBox.prompt('请输入"确认清空"以继续', '二次确认', {
      confirmButtonText: '执行清空',
      inputPattern: /^确认清空$/,
      inputErrorMessage: '请输入"确认清空"'
    })
    await clearData()
    ElMessage.success('数据已清空')
  } catch { /* cancel */ }
}

// ---- 账户管理 ----
async function onSaveProfile() {
  try {
    await updateProfile(profileForm.value)
    await auth.fetchUser()
    profileForm.value.nickname = auth.nickname
    profileForm.value.avatarUrl = auth.avatarUrl
    ElMessage.success('资料已更新')
  } catch { /* ignore */ }
}

async function onChangePassword() {
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码长度不能少于 6 位')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  try {
    await changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    ElMessage.success('密码已修改')
  } catch { /* ignore */ }
}

function onLogout() {
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-8">
    <PageHeader title="设置" />

    <!-- 账户 -->
    <section class="space-y-3">
      <h2 class="eyebrow">账户</h2>

      <div class="app-card p-5">
        <h3 class="mb-4 text-[13px] font-semibold text-ink">账户信息</h3>
        <div class="mb-5 flex items-center gap-3">
          <div
            class="flex h-11 w-11 items-center justify-center rounded-full bg-primary/10
                   text-[15px] font-semibold text-primary"
          >
            {{ (auth.nickname || auth.username || '?').charAt(0) }}
          </div>
          <div>
            <div class="text-[13px] font-medium text-ink">{{ auth.nickname || auth.username }}</div>
            <div class="readout mt-0.5 text-[11px] text-faint">{{ auth.username }}</div>
          </div>
        </div>
        <el-form label-width="80px" label-position="left">
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" class="!w-60" placeholder="昵称" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSaveProfile">保存资料</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="app-card p-5">
        <h3 class="mb-4 text-[13px] font-semibold text-ink">修改密码</h3>
        <el-form label-width="80px" label-position="left">
          <el-form-item label="原密码">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password class="!w-60" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="passwordForm.newPassword" type="password" show-password class="!w-60" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password class="!w-60" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onChangePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </div>
    </section>

    <!-- 偏好 -->
    <section class="space-y-3">
      <h2 class="eyebrow">偏好</h2>

      <div class="app-card p-5">
        <h3 class="mb-4 text-[13px] font-semibold text-ink">基础设置</h3>
        <el-form label-width="100px" label-position="left">
          <el-form-item label="系统主题">
            <el-radio-group v-model="configForm.theme_mode" @change="saveConfig">
              <el-radio value="light">浅色</el-radio>
              <el-radio value="dark">深色</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="默认优先级">
            <el-select v-model="configForm.default_priority" @change="saveConfig" class="!w-40">
              <el-option v-for="(text, key) in PRIORITY_TEXT" :key="key" :value="key" :label="text" />
            </el-select>
          </el-form-item>
          <el-form-item label="时间格式">
            <el-input v-model="configForm.time_format" @change="saveConfig" class="!w-60" />
          </el-form-item>
          <el-form-item label="周起始日">
            <el-radio-group v-model="configForm.week_start" @change="saveConfig">
              <el-radio value="1">周一</el-radio>
              <el-radio value="7">周日</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="日历字段">
            <el-radio-group v-model="configForm.calendar_field" @change="saveConfig">
              <el-radio value="due_time">截止时间</el-radio>
              <el-radio value="start_time">开始时间</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </div>

      <div class="app-card p-5">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="text-[13px] font-semibold text-ink">标签管理</h3>
          <el-button size="small" type="primary" @click="openTagDialog(null)">新增标签</el-button>
        </div>
        <div class="space-y-1">
          <div
            v-for="tag in tags"
            :key="tag.id"
            class="flex items-center gap-3 rounded-card px-3 py-2 transition-colors hover:bg-rule/25"
          >
            <span class="h-2.5 w-2.5 shrink-0 rounded-full" :style="{ background: tag.color }" />
            <span class="flex-1 text-[13px] text-ink">{{ tag.name }}</span>
            <span class="readout text-[11px] text-faint">{{ tag.taskCount ?? 0 }} 个任务</span>
            <el-button text size="small" @click="openTagDialog(tag)">编辑</el-button>
            <!-- 容器已用 gap 控制间距，抵消组件库对相邻按钮的默认外边距，否则会叠成两倍 -->
            <el-button text size="small" class="!ml-0" @click="onDeleteTag(tag)">
              <span class="text-status-overdue">删除</span>
            </el-button>
          </div>
        </div>
      </div>
    </section>

    <!-- 数据 -->
    <section class="space-y-3">
      <h2 class="eyebrow">数据</h2>

      <div class="app-card p-5">
        <h3 class="mb-4 text-[13px] font-semibold text-ink">导出</h3>
        <el-button type="primary" @click="onExport">导出 JSON 备份</el-button>
        <p class="mt-2.5 text-[12px] text-faint">导出全部任务、标签与操作日志为 JSON 文件</p>
      </div>

      <div class="app-card p-5">
        <h3 class="mb-4 text-[13px] font-semibold text-ink">导入</h3>
        <el-upload
          :auto-upload="false"
          :limit="1"
          accept=".json"
          @change="onImportFileChange"
          :show-file-list="true"
        >
          <el-button>选择文件</el-button>
          <template #tip>
            <div class="text-[12px] text-faint">仅支持 JSON，最大 10MB；标题重复的任务会自动跳过</div>
          </template>
        </el-upload>
        <el-button class="mt-3" type="primary" :disabled="!importFile" @click="onImport">开始导入</el-button>
        <div v-if="importResult" class="mt-3">
          <el-tag :type="importResult.failCount > 0 ? 'warning' : 'success'" disable-transitions>
            {{ importResult.message }}
          </el-tag>
        </div>
      </div>

      <div class="app-card p-5">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="text-[13px] font-semibold text-ink">备份</h3>
          <el-button size="small" type="primary" :loading="backupLoading" @click="onCreateBackup">
            创建备份
          </el-button>
        </div>
        <p v-if="backups.length === 0" class="py-8 text-center text-[12px] text-faint">
          还没有备份，创建一份可以随时回滚
        </p>
        <div v-else class="space-y-1">
          <div
            v-for="backup in backups"
            :key="backup.id"
            class="flex items-center gap-3 rounded-card px-3 py-2 transition-colors hover:bg-rule/25"
          >
            <div class="min-w-0 flex-1">
              <div class="readout truncate text-[12px] text-ink">{{ backup.fileName }}</div>
              <div class="readout mt-0.5 text-[11px] text-faint">
                {{ formatFileSize(backup.fileSize) }} · {{ backup.taskCount }} 任务 ·
                {{ backup.logCount }} 日志 · {{ formatTime(backup.createTime) }}
              </div>
            </div>
            <el-button size="small" @click="onRestore(backup)">恢复</el-button>
            <el-button size="small" class="!ml-0" @click="onDeleteBackup(backup)">
              <span class="text-status-overdue">删除</span>
            </el-button>
          </div>
        </div>
      </div>

      <div class="app-card p-5">
        <h3 class="mb-4 text-[13px] font-semibold text-ink">会话</h3>
        <el-button @click="onLogout">退出登录</el-button>
      </div>
    </section>

    <!-- 危险操作 -->
    <section class="space-y-3">
      <h2 class="eyebrow">危险操作</h2>
      <div class="app-card border-status-overdue/40 p-5">
        <h3 class="mb-2 text-[13px] font-semibold text-status-overdue">清空所有数据</h3>
        <p class="mb-4 text-[12px] text-graphite">
          将删除全部任务、标签与操作日志，配置项会恢复为默认值。此操作不可撤销。
        </p>
        <el-button type="danger" @click="onClearData">清空所有数据</el-button>
      </div>
    </section>

    <!-- 标签弹窗 -->
    <el-dialog v-model="tagDialogVisible" :title="editingTag ? '编辑标签' : '新增标签'" width="400px">
      <el-form label-width="60px">
        <el-form-item label="名称">
          <el-input v-model="tagForm.name" maxlength="50" placeholder="标签名称" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="tagForm.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTag">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
