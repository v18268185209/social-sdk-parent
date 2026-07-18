import api from './request'

// ===== 通知通道（邮件 / Webhook）=====
export function listChannels(type) {
  return api.get('/notify/channels', { params: type ? { type } : {} })
}
export function getChannel(id) {
  return api.get(`/notify/channels/${id}`)
}
export function createChannel(data) {
  return api.post('/notify/channels', data)
}
export function updateChannel(id, data) {
  return api.put(`/notify/channels/${id}`, data)
}
export function deleteChannel(id) {
  return api.delete(`/notify/channels/${id}`)
}
export function toggleChannel(id, enabled) {
  return api.post(`/notify/channels/${id}/toggle?enabled=${enabled}`)
}
export function testChannel(id, payload) {
  return api.post(`/notify/channels/${id}/test`, payload)
}

// ===== 通知模板（按场景）=====
export function listTemplates() {
  return api.get('/notify/templates')
}
export function listScenarios() {
  return api.get('/notify/templates/scenarios')
}
export function upsertTemplate(data) {
  return api.post('/notify/templates', data)
}
export function deleteTemplate(id) {
  return api.delete(`/notify/templates/${id}`)
}

// ===== 订阅规则（场景 -> 通道 + 接收范围）=====
export function listSubscriptions(scenario) {
  return api.get('/notify/subscriptions', { params: scenario ? { scenario } : {} })
}
export function createSubscription(data) {
  return api.post('/notify/subscriptions', data)
}
export function updateSubscription(id, data) {
  return api.put(`/notify/subscriptions/${id}`, data)
}
export function deleteSubscription(id) {
  return api.delete(`/notify/subscriptions/${id}`)
}
export function toggleSubscription(id, enabled) {
  return api.post(`/notify/subscriptions/${id}/toggle?enabled=${enabled}`)
}

// ===== 投递日志 =====
export function listLogs(params) {
  return api.get('/notify/logs', { params })
}
export function recentLogs(limit = 20) {
  return api.get('/notify/logs/recent', { params: { limit } })
}

// ===== 站内通知收件箱 =====
export function listMessages(params) {
  return api.get('/notify/messages', { params })
}
export function unreadCount() {
  return api.get('/notify/messages/unread-count')
}
export function markRead(id) {
  return api.post(`/notify/messages/${id}/read`)
}
export function markAllRead() {
  return api.post('/notify/messages/read-all')
}

// ===== 每日摘要配置 =====
export function getDigestConfig() {
  return api.get('/notify/digest/config')
}
export function saveDigestConfig(data) {
  return api.put('/notify/digest/config', data)
}
export function sendDigestNow() {
  return api.post('/notify/digest/send-now')
}
