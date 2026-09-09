import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 统一封装（设计文档 7.5）：
 * - 请求自动携带 Sa-Token（Authorization 头）
 * - 响应统一解包 {code, message, data}，code=200 返回 data
 * - code=401（未登录/过期）清除凭证回登录页
 */
const request = axios.create({
  baseURL: '/api', // 统一接口前缀：开发环境下由 Vite 代理转发到后端，规避跨域
  timeout: 15000, // 单次请求 15 秒超时，防止接口卡死拖住页面
})

// ===== 请求拦截器：每次发请求前自动携带登录凭证 =====
request.interceptors.request.use((config) => {
  // 从 localStorage 取 token 放进 Authorization 头（不带 Bearer 前缀，与后端 Sa-Token 约定一致）
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

// ===== 响应拦截器：统一解包与异常兜底 =====
// 后端统一返回 { code, message, data }。code=200 时业务代码拿到的直接是 data；
// code=401 视为凭证失效；其余 code 与网络错误统一弹出 message 提示并 reject
request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code === 200) {
      return body.data
    }
    if (body && body.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      // 同步清空 Pinia 内存态（懒 import 避免 request → stores/user → api → request 循环依赖；
      // 已在 /login 页时不发生整页跳转，旧身份残留会一直保留到下次登录）
      import('../stores/user')
        .then(({ useUserStore }) => useUserStore().$reset())
        .catch(() => {})
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
