import request from '../utils/request'

/** 注册（自动建家庭、户主成员与内置分类，成功后自动登录） */
export const register = (data) => request.post('/auth/register', data)

/** 登录 */
export const login = (data) => request.post('/auth/login', data)

/** 当前用户上下文：用户 + 家庭 + 成员列表 */
export const me = () => request.get('/auth/me')

/** 退出登录 */
export const logout = () => request.post('/auth/logout')
