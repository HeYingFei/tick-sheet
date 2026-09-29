import request from './request'

/**
 * 用户管理接口，仅超级管理员可用。
 *
 * 整条 /api/users 路径由后端 AdminOnlyInterceptor 拦截，
 * 前端的菜单与路由限制只是体验优化，不是安全边界。
 */
export const listUsers = (params) => request.get('/users', { params })

export const createUser = (data) => request.post('/users', data)

export const updateUser = (id, data) => request.put(`/users/${id}`, data)

/** 管理员重置密码，不校验原密码 */
export const resetUserPassword = (id, data) => request.put(`/users/${id}/password`, data)

/** status: 0-禁用 1-启用 */
export const changeUserStatus = (id, status) => request.patch(`/users/${id}/status`, { status })
