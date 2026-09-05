import request from '../utils/request'

/** 当前家庭信息 */
export const getFamily = () => request.get('/family')

/** 修改家庭名称/描述（仅管理员） */
export const updateFamily = (data) => request.put('/family', data)
