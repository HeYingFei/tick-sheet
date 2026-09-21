import request from './request'

/** 系统配置接口 */
export const getConfig = () => request.get('/config')

export const updateConfig = (data) => request.put('/config', data)
