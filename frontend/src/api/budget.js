import request from '../utils/request'

/** 某月预算列表（month: yyyy-MM） */
export const listBudgets = (month) => request.get('/budgets', { params: { month } })

/** 某月预算执行率 */
export const budgetExecution = (month) => request.get('/budgets/execution', { params: { month } })

export const createBudget = (data) => request.post('/budgets', data)
export const updateBudget = (id, data) => request.put(`/budgets/${id}`, data)
export const deleteBudget = (id) => request.delete(`/budgets/${id}`)
