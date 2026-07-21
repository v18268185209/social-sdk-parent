import api from './request'

// ===== 钱包概览 =====
export function getWallet(accountId) {
  return api.get(`/wallet/${accountId}`)
}

// ===== 交易记录（全部） =====
export function getTransactions(accountId) {
  return api.get(`/wallet/${accountId}/transactions`)
}

// ===== 最近交易 =====
export function getRecentTransactions(accountId, limit = 20) {
  return api.get(`/wallet/${accountId}/recent`, { params: { limit } })
}

// ===== 同步钱包 =====
export function syncWallet(accountId) {
  return api.post(`/wallet/${accountId}/sync`)
}

// ===== 调试原始响应 =====
export function debugWallet(accountId) {
  return api.get(`/wallet/${accountId}/debug`)
}

// ===== MTOP 探测 =====
// 后端要求 @RequestParam String api（必填），@RequestParam(required=false, defaultValue="1.0") String version
export function probeWallet(accountId, apiName, version = '1.0') {
  return api.post(`/wallet/${accountId}/probe`, null, { params: { api: apiName, version } })
}

// ===== 当前生效接口名 =====
export function getApiNames() {
  return api.get('/wallet/api-names')
}
