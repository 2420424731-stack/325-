import request from '../utils/request'

/** 资产列表 */
export const listAssets = () => request.get('/assets')

/** 资产汇总（总资产/总贷款/净资产/按类型分布） */
export const assetSummary = () => request.get('/assets/summary')

export const createAsset = (data) => request.post('/assets', data)
export const updateAsset = (id, data) => request.put(`/assets/${id}`, data)
export const deleteAsset = (id) => request.delete(`/assets/${id}`)
