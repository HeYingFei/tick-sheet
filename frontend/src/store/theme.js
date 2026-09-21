import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getConfig, updateConfig } from '@/api/config'
import { setTimeFormat } from '@/utils/format'

const STORAGE_KEY = 'todo_theme_mode'

/**
 * 主题与系统配置。
 *
 * 策略：本地 localStorage 优先保证首屏不闪白，随后从服务端同步；
 * 修改时先本地生效再回写服务端，服务端不可用时不影响本地使用。
 */
export const useThemeStore = defineStore('theme', () => {
  const mode = ref(localStorage.getItem(STORAGE_KEY) || 'light')
  const timeFormat = ref('YYYY-MM-DD HH:mm')
  const defaultPriority = ref(3)
  const weekStart = ref(1)
  const calendarField = ref('due_time')
  const loaded = ref(false)

  function apply() {
    document.documentElement.classList.toggle('dark', mode.value === 'dark')
  }

  function setMode(next) {
    mode.value = next
    localStorage.setItem(STORAGE_KEY, next)
    apply()
    updateConfig({ theme_mode: next }).catch(() => {
      // 服务端暂不可用时忽略，本地主题仍然生效
    })
  }

  function toggleMode() {
    setMode(mode.value === 'dark' ? 'light' : 'dark')
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
