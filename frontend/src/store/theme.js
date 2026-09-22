import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getConfig, updateConfig } from '@/api/config'
import { setTimeFormat } from '@/utils/format'
import { supportsLiquidRefraction } from '@/utils/liquidGlass'

const STORAGE_KEY = 'todo_theme_mode'

/** 可选主题。顺序即侧栏按钮的循环顺序 */
export const THEME_MODES = ['light', 'dark', 'glass', 'glass-dark']

/**
 * 主题与系统配置。
 *
 * 策略：本地 localStorage 优先保证首屏不闪白，随后从服务端同步；
 * 修改时先本地生效再回写服务端，服务端不可用时不影响本地使用。
 */
export const useThemeStore = defineStore('theme', () => {
  // localStorage 中可能是旧版本写入的值，不在白名单内时回落到浅色
  const stored = localStorage.getItem(STORAGE_KEY)
  const mode = ref(THEME_MODES.includes(stored) ? stored : 'light')
  const timeFormat = ref('YYYY-MM-DD HH:mm')
  const defaultPriority = ref(3)
  const weekStart = ref(1)
  const calendarField = ref('due_time')
  const loaded = ref(false)

  function apply() {
    const root = document.documentElement
    // glass-dark 是组合态：glass 与 dark 两个类都挂，CSS 侧对应 html.glass.dark
    const isDark = mode.value === 'dark' || mode.value === 'glass-dark'
    const isGlass = mode.value === 'glass' || mode.value === 'glass-dark'
    root.classList.toggle('dark', isDark)
    root.classList.toggle('glass', isGlass)
    // SVG 折射增强两种玻璃都适用；能力检测失败时 CSS 材质栈已足够
    root.classList.toggle('glass-refraction', isGlass && supportsLiquidRefraction())
  }

  function setMode(next) {
    mode.value = next
    localStorage.setItem(STORAGE_KEY, next)
    apply()
    updateConfig({ theme_mode: next }).catch(() => {
      // 服务端暂不可用时忽略，本地主题仍然生效
    })
  }

  /** 按 light → dark → glass → glass-dark 循环 */
  function toggleMode() {
    const next = (THEME_MODES.indexOf(mode.value) + 1) % THEME_MODES.length
    setMode(THEME_MODES[next])
  }

  async function loadFromServer() {
    try {
      const config = await getConfig()
      if (config?.theme_mode) {
        mode.value = config.theme_mode
        localStorage.setItem(STORAGE_KEY, mode.value)
      }
      if (config?.time_format) {
        timeFormat.value = config.time_format
        setTimeFormat(config.time_format)
      }
      if (config?.default_priority) defaultPriority.value = Number(config.default_priority)
      if (config?.week_start) weekStart.value = Number(config.week_start)
      if (config?.calendar_field) calendarField.value = config.calendar_field
      loaded.value = true
    } catch {
      // 后端未启动时保持本地默认值
    }
    apply()
  }

  return {
    mode,
    timeFormat,
    defaultPriority,
    weekStart,
    calendarField,
    loaded,
    apply,
    setMode,
    toggleMode,
    loadFromServer
  }
})
