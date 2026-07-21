import api from '@/api/request'

/** 获取代理池全局状态（实时指标、余额） */
export function getProxyStatus() {
  return api.get('/proxy/status')
}

/** 获取供应商配置列表 */
export function listProxyConfigs() {
  return api.get('/proxy/config')
}

/** 保存供应商配置 */
export function saveProxyConfig(providerType, data) {
  return api.post('/proxy/config', { providerType, config: data })
}

/** 删除供应商配置 */
export function deleteProxyConfig(providerType) {
  return api.delete(`/proxy/config/${providerType}`)
}

/** 触发运行时 reload */
export function reloadProxy() {
  return api.post('/proxy/reload')
}

/** 手动解绑账号 */
export function unbindAccount(accountId) {
  return api.delete(`/proxy/bindings/${accountId}`)
}

/** 手动触发一次全量健康检查 */
export function triggerHealthCheck() {
  return api.get('/proxy/health-check')
}
