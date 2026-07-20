import api from './request'

// ===== 市场情报 =====
export function getMarketKeywords() {
  return api.get('/market/keywords')
}
export function getMarketTrend(keyword, days = 30) {
  return api.get(`/market/trend/${encodeURIComponent(keyword)}?days=${days}`)
}
export function getMarketDistribution(keyword, days = 7) {
  return api.get(`/market/distribution/${encodeURIComponent(keyword)}?days=${days}`)
}
export function getMarketLatest(keyword) {
  return api.get(`/market/latest/${encodeURIComponent(keyword)}`)
}
export function getItemHistory(itemId) {
  return api.get(`/market/item/${itemId}`)
}
export function computeDailyStat(keyword, date) {
  const params = new URLSearchParams()
  params.append('keyword', keyword)
  if (date) params.append('date', date)
  return api.post('/market/compute-daily', params)
}

// ===== 卖家画像 =====
export function fetchSeller(userId) {
  return api.post(`/market/seller-fetch/${userId}`)
}
export function getSeller(userId) {
  return api.get(`/market/seller/${userId}`)
}
export function searchSeller(keyword) {
  return api.get(`/market/seller-search?keyword=${encodeURIComponent(keyword)}`)
}

// ===== 买家画像 =====
export function getBuyerList(page = 0, size = 20, keyword = '') {
  return api.get(`/buyer/list?page=${page}&size=${size}&keyword=${encodeURIComponent(keyword)}`)
}
export function getBuyer(buyerId) {
  return api.get(`/buyer/${buyerId}`)
}
export function addBuyerTag(buyerId, tag) {
  return api.post(`/buyer/${buyerId}/tag?tag=${encodeURIComponent(tag)}`)
}
export function removeBuyerTag(buyerId, tag) {
  return api.delete(`/buyer/${buyerId}/tag?tag=${encodeURIComponent(tag)}`)
}
export function setBuyerNotes(buyerId, notes) {
  const params = new URLSearchParams()
  params.append('notes', notes)
  return api.post(`/buyer/${buyerId}/notes`, params)
}

// ===== 监控任务 =====
export function getTaskList(page = 0, size = 20, accountId = null) {
  let url = `/monitor/tasks?page=${page}&size=${size}`
  if (accountId) url += `&accountId=${accountId}`
  return api.get(url)
}
export function getTask(id) {
  return api.get(`/monitor/tasks/${id}`)
}
export function createTask(data) {
  return api.post('/monitor/tasks', data)
}
export function updateTask(id, data) {
  return api.put(`/monitor/tasks/${id}`, data)
}
export function pauseTask(id) {
  return api.post(`/monitor/tasks/${id}/pause`)
}
export function resumeTask(id) {
  return api.post(`/monitor/tasks/${id}/resume`)
}
export function deleteTask(id) {
  return api.delete(`/monitor/tasks/${id}`)
}
export function runTask(id) {
  return api.post(`/monitor/tasks/${id}/run`)
}
export function getTaskResults(taskId, limit = 20) {
  return api.get(`/monitor/results/recent?taskId=${taskId}&limit=${limit}`)
}
export function getResultStats(taskId = null) {
  let url = '/monitor/results/stats'
  if (taskId) url += `?taskId=${taskId}`
  return api.get(url)
}
