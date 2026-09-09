import request from '../utils/request'

/**
 * 资产接口模块：非现金资产（房产/车辆/存款等）的增删改查与汇总。
 * 全部由 Assets.vue（资产管理页）调用：summary 供给顶部统计卡与分布饼图，其余为列表/编辑操作。
 */

/** 资产列表 */
export const listAssets = () => request.get('/assets')

/** 资产汇总（总资产/总贷款/净资产/按类型分布） */
export const assetSummary = () => request.get('/assets/summary')

/** 新增一条资产 */
export const createAsset = (data) => request.post('/assets', data)
/** 修改资产（如价值变动）/ 删除资产 */
export const updateAsset = (id, data) => request.put(`/assets/${id}`, data)
export const deleteAsset = (id) => request.delete(`/assets/${id}`)
