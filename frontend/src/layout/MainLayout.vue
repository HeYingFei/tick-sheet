<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { menuRoutes } from '@/router'
import { useThemeStore } from '@/store/theme'
import { useAuthStore } from '@/store/auth'
import TaskFormDialog from '@/components/TaskFormDialog.vue'
import { applyLiquidGlassAll, supportsLiquidRefraction } from '@/utils/liquidGlass'

const NAV_COLLAPSED_KEY = 'todo_nav_collapsed'

const route = useRoute()
const router = useRouter()
const theme = useThemeStore()
const auth = useAuthStore()
/**
 * 侧栏主题按钮的图标与提示。
 * 图标反映当前主题，提示说明点击后会切到哪里——三态循环只看图标不易自明。
 */
const THEME_META = {
  light: { icon: 'Sunny', label: '切换到深色' },
  dark: { icon: 'Moon', label: '切换到液态玻璃' },
  glass: { icon: 'MagicStick', label: '切换到深色玻璃' },
  'glass-dark': { icon: 'MoonNight', label: '切换到浅色' }
}
const themeMeta = computed(() => THEME_META[theme.mode] || THEME_META.light)

const dialogVisible = ref(false)

/**
 * 侧栏收缩状态。
 * 首次访问按窗口宽度决定默认值（窄屏默认收起），之后以用户的选择为准并持久化。
 */
const collapsed = ref(readInitialCollapsed())

function readInitialCollapsed() {
  const saved = localStorage.getItem(NAV_COLLAPSED_KEY)
  if (saved !== null) return saved === '1'
  return window.innerWidth < 768
}

function toggleNav() {
  collapsed.value = !collapsed.value
  localStorage.setItem(NAV_COLLAPSED_KEY, collapsed.value ? '1' : '0')
}

let stopLiquid = () => {}

/**
 * Chromium 上给玻璃面板挂 SVG 折射；其它环境走 CSS 回落。
 * 参数对齐 archisvaze/liquid-glass：低模糊、高 IOR，边缘才有透镜感。
 */
async function mountLiquidGlass() {
  stopLiquid()
  await nextTick()
  if (!theme.mode.startsWith('glass') || !supportsLiquidRefraction()) return
  stopLiquid = applyLiquidGlassAll(
    '.app-card, aside, .el-dialog, .el-message-box',
    {
      thickness: 80,
      bezel: 12,
      ior: 2.4,
      blur: 0.4,
      scaleRatio: 1,
      specularOpacity: 0.5,
      specularSaturation: 4
    }
  )
}

watch(() => theme.mode, mountLiquidGlass)

onMounted(() => {
  theme.apply()
  theme.loadFromServer().finally(mountLiquidGlass)
})

onUnmounted(() => stopLiquid())

function onAddTask() {
  dialogVisible.value = true
}

function onTaskSaved() {
  window.dispatchEvent(new CustomEvent('task-saved'))
}

function onLogout() {
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="flex h-full">
    <aside
      class="relative flex shrink-0 flex-col border-r border-rule bg-surface
             transition-[width] duration-200 ease-out"
      :class="collapsed ? 'w-14' : 'w-[196px]'"
    >
      <!-- 收缩开关：贴在侧栏右边缘，不占用栏内空间 -->
      <button
        class="absolute -right-3 top-[18px] z-20 flex h-6 w-6 items-center justify-center rounded-full
               border border-rule bg-surface text-graphite shadow-card transition-colors
               hover:border-graphite hover:text-ink"
        :title="collapsed ? '展开导航' : '收起导航'"
        :aria-label="collapsed ? '展开导航' : '收起导航'"
        :aria-expanded="!collapsed"
        @click="toggleNav"
      >
        <el-icon :size="12">
          <ArrowRight v-if="collapsed" />
          <ArrowLeft v-else />
        </el-icon>
      </button>

      <div class="flex items-center gap-2 px-3 py-4" :class="collapsed ? 'justify-center' : 'md:px-4'">
        <el-icon :size="17" class="shrink-0 text-primary"><Checked /></el-icon>
        <span v-if="!collapsed" class="truncate text-[13px] font-semibold tracking-tight text-ink">
          任务管理
        </span>
      </div>

      <nav class="flex flex-1 flex-col gap-0.5 px-2">
        <router-link
          v-for="item in menuRoutes"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ 'nav-item-active': route.path === item.path, 'justify-center': collapsed }"
          :title="item.meta.title"
        >
          <el-icon :size="15" class="shrink-0"><component :is="item.meta.icon" /></el-icon>
          <span v-if="!collapsed" class="truncate">{{ item.meta.title }}</span>
        </router-link>
      </nav>

      <div class="mt-4 border-t border-rule p-2">
        <div class="flex items-center gap-1" :class="collapsed ? 'flex-col' : ''">
          <el-tooltip :content="themeMeta.label" placement="right">
            <el-button text circle size="small" :aria-label="themeMeta.label" @click="theme.toggleMode()">
              <el-icon :size="15"><component :is="themeMeta.icon" /></el-icon>
            </el-button>
          </el-tooltip>

          <!-- 收起时只留首字母，点击进入设置页，保证账户入口不丢失 -->
          <el-tooltip v-if="collapsed" :content="auth.nickname || '账户设置'" placement="right">
            <el-button text circle size="small" @click="router.push('/settings')">
              <span class="text-[11px] font-semibold">{{ (auth.nickname || auth.username || '?').charAt(0) }}</span>
            </el-button>
          </el-tooltip>

          <el-dropdown v-else trigger="click" class="min-w-0 flex-1">
            <div
              class="flex cursor-pointer items-center gap-1.5 rounded-card px-1.5 py-1 text-[13px]
                     text-graphite transition-colors hover:text-ink"
            >
              <el-icon :size="14"><User /></el-icon>
              <span class="truncate">{{ auth.nickname }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/settings')">
                  <el-icon><Setting /></el-icon>系统设置
                </el-dropdown-item>
                <el-dropdown-item divided @click="onLogout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </aside>

    <main class="flex-1 overflow-auto">
      <div class="mx-auto max-w-[1280px] px-6 py-7 lg:px-9">
        <router-view :key="route.fullPath" />
      </div>
    </main>

    <!-- 全局新建：带文字说明动作，比纯图标按钮更明确 -->
    <el-button type="primary" class="!fixed bottom-7 right-7 z-50 shadow-card-hover" @click="onAddTask">
      <el-icon class="mr-1.5"><Plus /></el-icon>新建任务
    </el-button>

    <TaskFormDialog v-model:visible="dialogVisible" :task="null" @saved="onTaskSaved" />
  </div>
</template>
