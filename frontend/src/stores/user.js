import { defineStore } from 'pinia'
import * as authApi from '../api/auth'

/**
 * 用户全局状态（设计文档 7.5）：
 * token 持久化到 localStorage，家庭/成员供全站下拉使用
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    family: null,
    members: [],
  }),
  getters: {
    isAdmin: (s) => s.user?.role === 'ADMIN',
    /** 成员 id → 姓名 映射，供列表展示 */
    memberMap: (s) => Object.fromEntries((s.members || []).map((m) => [m.id, m.name])),
    memberOptions: (s) =>
      (s.members || []).map((m) => ({ value: m.id, label: m.name })),
  },
  actions: {
    async login(payload) {
      const data = await authApi.login(payload)
      this.applyLogin(data)
    },
    async register(payload) {
      const data = await authApi.register(payload)
      this.applyLogin(data)
    },
    applyLogin(data) {
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    /** 拉取用户上下文（家庭 + 成员列表） */
    async fetchContext() {
      const data = await authApi.me()
      this.user = data.user
      this.family = data.family
      this.members = data.members || []
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    async logout() {
      try {
        await authApi.logout()
      } catch {
        /* 忽略退出接口失败（如 token 已失效） */
      }
      // 必须先清 localStorage 再 $reset：$reset 会重新执行 state()（内部读取 localStorage），
      // 顺序颠倒会让内存态回填刚删过的旧身份，造成「退出后 store 仍残留登录态」
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      this.$reset()
    },
  },
})
