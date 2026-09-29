<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
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

  loading.value = true
  try {
    const data = await login({ username: form.username, password: form.password })
    auth.setLogin(data)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch {
    /* 失败提示由请求拦截器统一处理 */
  } finally {
    loading.value = false
  }
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

      <h1 class="mt-9 text-[22px] font-semibold tracking-tight text-ink">登录</h1>
      <p class="mt-1.5 text-[12px] text-graphite">打开你的工作记录</p>

      <form class="mt-9 space-y-6" @submit.prevent="onSubmit">
        <div class="field-line">
          <label class="eyebrow" for="login-username">用户名</label>
          <el-input id="login-username" v-model="form.username" size="large" @keyup.enter="onSubmit" />
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
          登录
        </el-button>
      </form>

      <!-- 注册入口已关闭：账号统一由管理员在「用户管理」里创建 -->
      <p class="mt-6 text-[12px] leading-relaxed text-faint">
        账号由管理员创建。忘记密码请联系管理员重置。
      </p>
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
