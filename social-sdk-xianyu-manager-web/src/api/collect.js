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
