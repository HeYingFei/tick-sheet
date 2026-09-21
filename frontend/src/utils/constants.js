/**
 * 领域常量。与设计方案 §4 的规则保持一致，禁止在页面内硬编码枚举值。
 */

/** 任务状态：逾期不落库，为查询时派生状态（§4.2） */
export const TASK_STATUS = {
  TODO: 0,
  DOING: 1,
  DONE: 2,
  CANCELED: 3
}

export const TASK_STATUS_TEXT = {
  0: '待办',
  1: '进行中',
  2: '已完成',
  3: '已取消'
}

/** Element Plus tag 类型 */
export const TASK_STATUS_TAG = {
  0: 'info',
  1: 'primary',
  2: 'success',
  3: 'info'
}

/** 状态流转白名单，见设计方案 §4.1 */
export const STATUS_TRANSITIONS = {
  0: [1, 2, 3],
  1: [0, 2, 3],
  2: [0],
  3: [0]
}

export const PRIORITY_TEXT = {
  1: '极高',
  2: '高',
  3: '中',
  4: '低'
}

export const PRIORITY_TAG = {
  1: 'danger',
  2: 'warning',
  3: 'primary',
  4: 'info'
}

/**
 * 四象限定义，见设计方案 §3.3。
 *
 * 颜色取印刷颜料色系（砖红 / 普鲁士蓝 / 赭石 / 灰绿），
 * 并用 CSS 变量表达，以便深浅主题各自取值。
 */
export const QUADRANTS = [
  { key: 'q1', important: true, urgent: true, name: '重要且紧急', action: '立即处理', color: 'var(--c-q1)', suggestPriority: 1 },
  { key: 'q2', important: true, urgent: false, name: '重要不紧急', action: '重点规划', color: 'var(--c-q2)', suggestPriority: 2 },
  { key: 'q3', important: false, urgent: true, name: '紧急不重要', action: '简化处理', color: 'var(--c-q3)', suggestPriority: 3 },
  { key: 'q4', important: false, urgent: false, name: '不重要不紧急', action: '闲置舍弃', color: 'var(--c-q4)', suggestPriority: 4 }
]

/** 根据重要/紧急属性定位象限 */
export function resolveQuadrant(isImportant, isUrgent) {
  return QUADRANTS.find((q) => q.important === !!isImportant && q.urgent === !!isUrgent) ?? QUADRANTS[3]
}

/** 操作类型，见设计方案 §5.5 */
export const OPERATE_TYPE_TEXT = {
  1: '创建',
  2: '修改',
  3: '开始',
  4: '完成',
  5: '取消',
  6: '删除',
  7: '恢复'
}

export const OPERATE_TYPE_COLOR = {
  1: 'var(--c-primary)', // 创建
  2: 'var(--c-q3)', // 修改
  3: 'var(--c-violet)', // 开始
  4: 'var(--c-status-done)', // 完成
  5: 'var(--c-status-canceled)', // 取消
  6: 'var(--c-q1)', // 删除
  7: 'var(--c-teal)' // 恢复
}
