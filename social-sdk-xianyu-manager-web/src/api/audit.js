import api from './request'

// ===== 审计日志列表 =====
export function listAuditLogs(params) {
  return api.get('/audit/logs', { params })
}
