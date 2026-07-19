import api from './request'

// ===== 仪表盘统计 =====
export function getDashboard() {
  return api.get('/monitor/dashboard')
}

// ===== 账号维度统计 =====
export function getAccountStats() {
  return api.get('/monitor/accounts')
}

// ===== 清缓存 =====
export function clearCache() {
  return api.post('/monitor/cache/clear')
}
