import request from '@/api/request'

// 自动回复日志 — AutoReplyLogController (/api/reply-logs)
// GET /api/reply-logs?page&size&accountId&replyType&matched
export function listReplyLogs(params) {
  return request.get('/reply-logs', { params })
}
