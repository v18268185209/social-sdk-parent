import api from './request'

// ===== 规则列表 =====
export function listRules(params) {
  return api.get('/rules', { params })
}

// ===== 创建规则 =====
export function createRule(data) {
  return api.post('/rules', data)
}

// ===== 更新规则 =====
export function updateRule(id, data) {
  return api.put(`/rules/${id}`, data)
}

// ===== 删除规则 =====
export function deleteRule(id) {
  return api.delete(`/rules/${id}`)
}

// ===== 切换规则状态 =====
export function toggleRule(id, enabled) {
  return api.post(`/rules/${id}/toggle?enabled=${enabled}`)
}

// ===== 测试匹配 =====
export function testRuleMatch(data) {
  return api.post('/rules/test', data)
}

// ===== 读取配置 =====
export function getRuleConfig(accountId) {
  return api.get(`/rules/config/${accountId}`)
}

// ===== 保存配置 =====
export function saveRuleConfig(accountId, config) {
  return api.post(`/rules/config/${accountId}`, config)
}
