<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '@/api/auth'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()

const mode = ref('login')
const loading = ref(false)

const isLogin = computed(() => mode.value === 'login')

const form = reactive({
  username: '',
  password: '',
  nickname: ''
})

/** 记录本封面的日期。用原生 Date，不为一行格式化再引入依赖 */
const today = (() => {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()} / ${pad(now.getMonth() + 1)} / ${pad(now.getDate())} · 周${
    '日一二三四五六'[now.getDay()]
  }`
})()

async function onSubmit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  if (!isLogin.value && form.password.length < 6) {
    ElMessage.warning('密码长度不能少于 6 位')
    return
  }

  loading.value = true
  try {
    const data = isLogin.value
      ? await login({ username: form.username, password: form.password })
      : await register({
          username: form.username,
          password: form.password,
          nickname: form.nickname || undefined
        })
    auth.setLogin(data)
    ElMessage.success(isLogin.value ? '登录成功' : '注册成功')
    router.push('/dashboard')
  } catch {
    /* 失败提示由请求拦截器统一处理 */
  } finally {
    loading.value = false
  }
}

function switchMode() {
  mode.value = isLogin.value ? 'register' : 'login'
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-paper px-6 py-12">
    <div class="w-full max-w-[352px]">
      <!-- 标识 -->
      <div class="flex items-center gap-2">
        <el-icon :size="17" class="text-primary"><Checked /></el-icon>
        <span class="text-[14px] font-semibold tracking-tight text-ink">任务管理</span>
      </div>

      <!-- 封面：日期 + 双线 -->
      <div class="readout mt-5 text-[11px] tracking-[0.08em] text-faint">{{ today }}</div>
      <div class="mt-2.5 h-px bg-ink/60" />
      <div class="mt-[3px] h-px bg-ink/20" />

      <h1 class="mt-9 text-[22px] font-semibold tracking-tight text-ink">
        {{ isLogin ? '登录' : '创建账号' }}
      </h1>
      <p class="mt-1.5 text-[12px] text-graphite">
        {{ isLogin ? '打开你的工作记录' : '建立你自己的记录本' }}
      </p>

      <form class="mt-9 space-y-6" @submit.prevent="onSubmit">
        <div class="field-line">
          <label class="eyebrow" for="login-username">用户名</label>
          <el-input id="login-username" v-model="form.username" size="large" @keyup.enter="onSubmit" />
        </div>

        <div v-if="!isLogin" class="field-line">
          <label class="eyebrow" for="login-nickname">昵称（选填）</label>
          <el-input id="login-nickname" v-model="form.nickname" size="large" @keyup.enter="onSubmit" />
        </div>

        <div class="field-line">
          <label class="eyebrow" for="login-password">密码</label>
          <el-input
            id="login-password"
            v-model="form.password"
            type="password"
            size="large"
            show-password
            @keyup.enter="onSubmit"
          />
        </div>

        <el-button
          type="primary"
          size="large"
          class="!mt-8 w-full"
          :loading="loading"
          @click="onSubmit"
        >
          {{ isLogin ? '登录' : '创建账号' }}
        </el-button>
      </form>

      <div class="mt-6 flex items-center gap-1.5 text-[12px] text-faint">
        <span>{{ isLogin ? '还没有账号？' : '已有账号？' }}</span>
        <button
          type="button"
          class="text-primary transition-opacity hover:opacity-70"
          @click="switchMode"
        >
          {{ isLogin ? '创建账号' : '去登录' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/*
 * 填写栏：去掉 Element Plus 的圆角与边框，只留一条横线，
 * 让输入区看起来像记录本上待填的横线，而不是一个软件控件。
 * 聚焦时只换线的颜色、不改宽度，避免布局跳动。
 */
.field-line :deep(.el-input__wrapper) {
  padding-left: 0;
  padding-right: 0;
  background-color: transparent;
  /* rule-strong 在纸底上太淡，用 faint 才像记录本上印的横线 */
  border-bottom: 1px solid rgb(var(--c-faint));
  border-radius: 0;
  box-shadow: none;
  transition: border-color 0.15s ease;
}

.field-line :deep(.el-input__wrapper:hover) {
  border-bottom-color: rgb(var(--c-graphite));
}

.field-line :deep(.el-input__wrapper.is-focus) {
  border-bottom-color: rgb(var(--c-primary));
  box-shadow: none;
}

.field-line :deep(.el-input__inner) {
  font-family: 'IBM Plex Mono', ui-monospace, 'Cascadia Mono', Consolas, monospace;
}
</style>
