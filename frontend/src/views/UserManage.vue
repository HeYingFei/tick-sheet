<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, updateUser, resetUserPassword, changeUserStatus } from '@/api/user'
import { useAuthStore } from '@/store/auth'
import { formatTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'

const auth = useAuthStore()

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 20 })

const readings = computed(() => [{ label: '用户总数', value: total.value }])

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ username: '', nickname: '', password: '' })

const editVisible = ref(false)
const editForm = reactive({ id: null, username: '', nickname: '' })

const resetVisible = ref(false)
const resetForm = reactive({ id: null, username: '', password: '' })

async function load() {
  loading.value = true
  try {
    const data = await listUsers({
      keyword: query.keyword.trim() || undefined,
      page: query.page,
      size: query.size
    })
    records.value = data.records || []
    total.value = data.total || 0
  } catch {
    /* 失败提示由请求拦截器统一处理 */
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

// ---- 新建 ----

function openCreate() {
  Object.assign(createForm, { username: '', nickname: '', password: '' })
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.username.trim() || !createForm.password) {
    ElMessage.warning('请填写用户名和初始密码')
    return
  }
  if (createForm.password.length < 6) {
    ElMessage.warning('初始密码不能少于 6 位')
    return
  }

  creating.value = true
  try {
    await createUser({
      username: createForm.username.trim(),
      password: createForm.password,
      nickname: createForm.nickname.trim() || undefined
    })
    ElMessage.success('用户已创建')
    createVisible.value = false
    onSearch()
  } catch {
    /* ignore */
  } finally {
    creating.value = false
  }
}

// ---- 改昵称 ----

function openEdit(row) {
  Object.assign(editForm, { id: row.id, username: row.username, nickname: row.nickname })
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  try {
    await updateUser(editForm.id, { nickname: editForm.nickname.trim() })
    ElMessage.success('昵称已更新')
    editVisible.value = false
    load()
  } catch {
    /* ignore */
  }
}

// ---- 重置密码 ----

function openReset(row) {
  Object.assign(resetForm, { id: row.id, username: row.username, password: '' })
  resetVisible.value = true
}

async function submitReset() {
  if (resetForm.password.length < 6) {
    ElMessage.warning('密码不能少于 6 位')
    return
  }
  try {
    await resetUserPassword(resetForm.id, { password: resetForm.password })
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } catch {
    /* ignore */
  }
}

// ---- 启用 / 禁用 ----

async function toggleStatus(row, next) {
  const action = next === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      next === 1
        ? `确定要启用账号「${row.username}」吗？`
        : `确定要禁用账号「${row.username}」吗？禁用后该账号会立即无法登录。`,
      `${action}账号`,
      { type: next === 1 ? 'info' : 'warning' }
    )
  } catch {
    return // 用户取消
  }

  try {
    await changeUserStatus(row.id, next)
    ElMessage.success(`已${action}`)
    load()
  } catch {
    /* ignore */
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-5">
    <PageHeader title="用户管理" :readings="readings" />

    <section class="app-card">
      <header class="flex flex-wrap items-center justify-between gap-3 border-b border-rule px-5 py-3">
        <el-input
          v-model="query.keyword"
          placeholder="搜索用户名或昵称"
          clearable
          class="!w-64"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" @click="openCreate">新建用户</el-button>
      </header>

      <el-table v-loading="loading" :data="records" class="w-full">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <span :class="row.role === 'admin' ? 'text-primary' : 'text-graphite'">
              {{ row.role === 'admin' ? '超级管理员' : '普通用户' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span :class="row.status === 1 ? 'text-status-done' : 'text-status-canceled'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="readout text-[12px] text-graphite">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="240" align="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row)">改昵称</el-button>
            <el-button text size="small" @click="openReset(row)">重置密码</el-button>
            <!-- 管理员自身不能禁用：这里隐藏即可少一次无谓报错，服务端同样会拒绝 -->
            <el-button
              v-if="row.id !== auth.user?.id"
              text
              size="small"
              @click="toggleStatus(row, row.status === 1 ? 0 : 1)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <footer v-if="total > query.size" class="flex justify-end border-t border-rule px-5 py-3">
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="load"
        />
      </footer>
    </section>

    <!-- 新建用户 -->
    <el-dialog v-model="createVisible" title="新建用户" width="440px">
      <el-form label-width="90px" label-position="left">
        <el-form-item label="用户名">
          <el-input v-model="createForm.username" placeholder="2-50 个字符" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createForm.nickname" placeholder="留空则与用户名相同" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input
            v-model="createForm.password"
            type="password"
            show-password
            placeholder="至少 6 位"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 改昵称 -->
    <el-dialog v-model="editVisible" title="修改昵称" width="440px">
      <el-form label-width="90px" label-position="left">
        <el-form-item label="用户名">
          <span class="readout text-[13px] text-graphite">{{ editForm.username }}</span>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetVisible" title="重置密码" width="440px">
      <p class="mb-3 text-[13px] leading-relaxed text-graphite">
        为「{{ resetForm.username }}」设置新密码，无需原密码。
        对方已登录的会话不会因此失效，需要立即踢下线请改用禁用。
      </p>
      <el-input v-model="resetForm.password" type="password" show-password placeholder="至少 6 位" />
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReset">重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>
