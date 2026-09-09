import request from '../utils/request'

/**
 * 预算接口模块：按“家庭 + 分类 + 月份”设定预算上限，并查看执行率。
 * 全部由 Budgets.vue（预算管理页）调用：listBudgets/budgetExecution 用于列表与进度展示，
 * 增删改对应页面上的新建/编辑/删除操作。
 */

/** 某月预算列表（month: yyyy-MM） */
export const listBudgets = (month) => request.get('/budgets', { params: { month } })

/** 某月预算执行率 */
export const budgetExecution = (month) => request.get('/budgets/execution', { params: { month } })

/** 新增预算（某分类某月限额） */
export const createBudget = (data) => request.post('/budgets', data)
/** 修改预算 / 删除预算 */
export const updateBudget = (id, data) => request.put(`/budgets/${id}`, data)
export const deleteBudget = (id) => request.delete(`/budgets/${id}`)
