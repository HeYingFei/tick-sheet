import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一请求封装。
 *
 * 后端约定：
 * - 成功：HTTP 200，body = { code: 200, msg, data, timestamp }
 * - 失败：HTTP 状态码与 body.code 一致（400/401/404/409/413/415/422/500）
 *
 * 因此成功分支直接返回 data；失败分支统一弹错误提示并 reject。
 */
/**
 * 后端地址。
 *
 * 前端与后端不同源，直接按当前页面所在主机拼后端端口，不依赖 Vite 代理：
 * 无论用 localhost、127.0.0.1、[::1] 还是局域网 IP 打开页面，
 * 都能连到同一台机器上的后端。跨域由后端 app.cors.allowed-origin-patterns 放行。
 *
 * 需要指向别的后端时，用 VITE_API_BASE_URL 覆盖即可。
 */
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  `${window.location.protocol}//${window.location.hostname}:8080/api`

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000
})

// 请求拦截：自动注入 Authorization
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data

    // 401 未登录或登录过期，跳转登录页
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      // 使用 window.location 跳转，避免 router 未初始化的问题
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      ElMessage.warning(body?.msg || '未登录或登录已过期')
      return Promise.reject(error)
    }

    const msg = body?.msg || error.message || '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request

/** 触发浏览器下载，用于导出接口 */
export function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
