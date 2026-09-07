import request from '../utils/request'

/** 分类树（type: 1 收入 / 2 支出） */
export const categoryTree = (type) => request.get('/categories/tree', { params: { type } })
export const addCategory = (data) => request.post('/categories', data)
export const updateCategory = (id, data) => request.put(`/categories/${id}`, data)
export const deleteCategory = (id) => request.delete(`/categories/${id}`)
