import request from '../utils/request'

/**
 * 收支分类接口模块：分类是一棵二级树（大分类下挂小分类，type 1=收入 / 2=支出）。
 * categoryTree 被 Categories.vue（分类管理页）及 Transactions.vue / Budgets.vue
 * （表单里的分类级联选择）调用；增删改主要由 Categories.vue 触发。
 */

/** 分类树（type: 1 收入 / 2 支出） */
export const categoryTree = (type) => request.get('/categories/tree', { params: { type } })
/** 新增分类（父分类下加子分类时传 parentId） */
export const addCategory = (data) => request.post('/categories', data)
/** 修改分类名称等 */
export const updateCategory = (id, data) => request.put(`/categories/${id}`, data)
/** 删除分类（如分类下还有流水，是否允许删除由后端规则决定） */
export const deleteCategory = (id) => request.delete(`/categories/${id}`)
