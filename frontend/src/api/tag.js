import request from './request'

/** 标签接口 */
export const listTags = () => request.get('/tags')

export const createTag = (data) => request.post('/tags', data)

export const updateTag = (id, data) => request.put(`/tags/${id}`, data)

export const deleteTag = (id) => request.delete(`/tags/${id}`)
