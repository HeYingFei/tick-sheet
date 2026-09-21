<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  planned: { type: Array, default: () => [] }
})
</script>

<template>
  <div class="mx-auto max-w-3xl">
    <div class="app-card p-8">
      <h2 class="mb-3 text-lg font-semibold">{{ title }}</h2>
      <p v-if="description" class="mb-5 text-sm text-gray-500 dark:text-gray-400">
        {{ description }}
      </p>

      <div v-if="planned.length" class="mb-5">
        <div class="mb-2 text-xs font-medium text-gray-500 dark:text-gray-400">本页待实现内容</div>
        <ul class="space-y-1.5">
          <li
            v-for="item in planned"
            :key="item"
            class="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-300"
          >
            <el-icon class="mt-0.5 shrink-0 text-primary" :size="14"><Right /></el-icon>
            <span>{{ item }}</span>
          </li>
        </ul>
      </div>

      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          <span class="text-sm">工程脚手架占位页</span>
        </template>
        <template #default>
          <span class="text-xs">
            路由、布局、深浅主题、Axios 请求封装、领域常量均已就绪，页面主体将在后续实现。
          </span>
        </template>
      </el-alert>
    </div>
  </div>
</template>
