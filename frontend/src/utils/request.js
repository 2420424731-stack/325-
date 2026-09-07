import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 统一封装（设计文档 7.5）：
 * - 请求自动携带 Sa-Token（Authorization 头）
 * - 响应统一解包 {code, message, data}，code=200 返回 data
 * - code=401（未登录/过期）清除凭证回登录页
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code === 200) {
      return body.data
    }
    if (body && body.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    }
    ElMessage.error(body?.message || '请求失败')
    return Promise.reject(new Error(body?.message || '请求失败'))
  },
  (err) => {
    const msg = err?.response?.data?.message || '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(err)
  },
)

export default request
