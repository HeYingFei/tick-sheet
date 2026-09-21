import request from './request'

/** 数据迁移接口，见设计方案 §3.7 */

/** 导出。后端返回 JSON 结构体，由调用方组装成文件下载 */
export const exportData = () => request.get('/migration/export')

/** 导入。返回成功/重复/失败统计与异常明细 */
export const importData = (file, onProgress) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/migration/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}

export const createBackup = () => request.post('/migration/backup')

export const listBackups = () => request.get('/migration/backups')

export const restoreBackup = (id) => request.post(`/migration/backups/${id}/restore`)

export const deleteBackup = (id) => request.delete(`/migration/backups/${id}`)

/** 清空业务数据，需前端二次确认 */
export const clearData = () => request.delete('/migration/data')
