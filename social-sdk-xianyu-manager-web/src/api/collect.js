import api from './request'

// ===== 收藏列表 =====
export function listCollects(params) {
  return api.get('/collect', { params })
}

// ===== 添加收藏 =====
export function addCollect(data) {
  return api.post('/collect', data)
}

// ===== 移除收藏 =====
export function removeCollect(id) {
  return api.delete(`/collect/${id}`)
}

// ===== 同步闲鱼收藏列表 =====
export function syncCollects(accountId) {
  return api.post('/collect/sync', null, { params: { accountId } })
}

// ===== 搜索闲鱼商品（用于添加收藏弹窗） =====
export function searchCollectItems(accountId, keyword) {
  return api.get('/collect/search', { params: { accountId, keyword } })
}
