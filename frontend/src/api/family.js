import request from '../utils/request'

/**
 * 家庭接口模块：当前家庭信息的查看与修改。
 * 由 Profile.vue（个人中心）调用：展示家庭卡片，管理员可改名称/描述。
 * 家庭成员相关操作见 member.js；家庭设置权限由 user store 的 isAdmin 控制。
 */

/** 当前家庭信息 */
export const getFamily = () => request.get('/family')

/** 修改家庭名称/描述（仅管理员） */
export const updateFamily = (data) => request.put('/family', data)
