import dayjs from 'dayjs'

/** 默认展示格式，使用 dayjs 记号 */
const DEFAULT_TIME_FORMAT = 'YYYY-MM-DD HH:mm'

let timeFormat = DEFAULT_TIME_FORMAT

/**
 * 把配置里的时间格式统一成 dayjs 记号。
 *
 * 历史配置存的是 Java 记号（yyyy-MM-dd，年与日为小写），dayjs 不认这些，
 * 会原样输出 yyyy，并把 dd 当成星期缩写（变成 Th）。这里做一次转换，
 * 对已经是 dayjs 记号的配置是幂等的（YYYY / DD 不含小写字母）。
 */
export function normalizeTimeFormat(format) {
  if (!format) return DEFAULT_TIME_FORMAT
  return format.replace(/y/g, 'Y').replace(/d/g, 'D')
}

export function setTimeFormat(format) {
  timeFormat = normalizeTimeFormat(format)
}

export function formatTime(value) {
  return value ? dayjs(value).format(timeFormat) : ''
}

export function formatDate(value) {
  return value ? dayjs(value).format('YYYY-MM-DD') : ''
}

/** 相对今天的自然语言描述 */
export function formatRelative(value) {
  if (!value) return ''
  const target = dayjs(value)
  const diffDays = target.startOf('day').diff(dayjs().startOf('day'), 'day')
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '明天'
  if (diffDays === -1) return '昨天'
  if (diffDays > 1) return `${diffDays} 天后`
  return `逾期 ${Math.abs(diffDays)} 天`
}

/**
 * 截止时间的紧凑表达，供列表与卡片使用。
 *
 * 这些位置宽度有限，直接摆完整时间戳（2026-09-20 14:00）会跟标题抢注意力；
 * 近处给相对描述、远处才退回日期，一眼就能判断轻重缓急。
 * 逾期沿用后端算好的天数，避免前后端口径不一致。
 *
 * @param {{dueTime?: string, overdue?: boolean, overdueDays?: number}} task
 */
export function formatDue(task) {
  if (!task?.dueTime) return '未设截止'
  if (task.overdue) return `逾期 ${task.overdueDays} 天`

  const due = dayjs(task.dueTime)
  const diff = due.startOf('day').diff(dayjs().startOf('day'), 'day')
  if (diff === 0) return `今天 ${due.format('HH:mm')}`
  if (diff === 1) return `明天 ${due.format('HH:mm')}`
  if (diff === -1) return `昨天 ${due.format('HH:mm')}`
  if (diff < 7) return `${diff} 天后`
  return due.format('MM-DD')
}

export function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

/**
 * 百分比展示。后端统一返回 0~100 的数值（如 85 表示 85%），
 * 分母为 0 时返回 null，此处展示「—」而非 0%，见设计方案 §4.7。
 */
export function formatPercent(value) {
  if (value === null || value === undefined) return '—'
  return `${Number(value).toFixed(1)}%`
}

/** 当前会话的时区标识，仅用于展示 */
export const TIMEZONE = 'Asia/Shanghai'
