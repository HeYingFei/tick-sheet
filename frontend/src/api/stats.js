import request from './request'

/** 日历接口 */
export const getCalendarTasks = (params) => request.get('/calendar/tasks', { params })

export const getCalendarHeatmap = (params) => request.get('/calendar/heatmap', { params })

/** 时间线接口 */
export const getTimelineLogs = (params) => request.get('/timeline/logs', { params })

/** 统计接口，口径见设计方案 §4.7 */
export const getOverview = () => request.get('/stats/overview')

export const getTrend = (days = 7) => request.get('/stats/trend', { params: { days } })

export const getQuadrantStats = () => request.get('/stats/quadrant')

export const getTagStats = () => request.get('/stats/tags')

export const getCompletion = (params) => request.get('/stats/completion', { params })
