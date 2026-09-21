import request from './request'

/** 任务接口，见设计方案 §6.4 */

export const pageTasks = (params) => request.get('/tasks', { params })

export const getTask = (id) => request.get(`/tasks/${id}`)

export const createTask = (data) => request.post('/tasks', data)

export const updateTask = (id, data) => request.put(`/tasks/${id}`, data)

export const deleteTask = (id) => request.delete(`/tasks/${id}`)

/** 状态流转，非法流转后端返回 422 */
export const changeStatus = (id, status) => request.patch(`/tasks/${id}/status`, { status })

/** 象限变更（拖拽）。响应含 suggestPriority 提示值，见设计方案 §4.3 */
export const changeQuadrant = (id, data) => request.patch(`/tasks/${id}/quadrant`, data)

export const changeOrder = (id, sortOrder) => request.patch(`/tasks/${id}/order`, { sortOrder })

export const batchStatus = (ids, status) => request.post('/tasks/batch/status', { ids, status })

export const batchDelete = (ids) => request.post('/tasks/batch/delete', { ids })

export const batchTags = (ids, tagIds, mode) => request.post('/tasks/batch/tags', { ids, tagIds, mode })

/** 四象限看板 */
export const getQuadrantBoard = () => request.get('/quadrant/board')
