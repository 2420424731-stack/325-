import request from '../utils/request'

/** 资产列表（含持仓明细） */
export const listAssets = () => request.get('/assets')

/** 资产汇总（总资产/总贷款/净资产/按类型分布） */
export const assetSummary = () => request.get('/assets/summary')

export const createAsset = (data) => request.post('/assets', data)
export const updateAsset = (id, data) => request.put(`/assets/${id}`, data)
export const deleteAsset = (id) => request.delete(`/assets/${id}`)

/** 持仓增删改 + 更新现价（仅股票基金类资产） */
export const addPosition = (assetId, data) => request.post(`/assets/${assetId}/positions`, data)
export const updatePosition = (positionId, data) => request.put(`/assets/positions/${positionId}`, data)
export const deletePosition = (positionId) => request.delete(`/assets/positions/${positionId}`)
export const updatePositionPrice = (positionId, currentPrice) =>
  request.put(`/assets/positions/${positionId}/price`, { currentPrice })
