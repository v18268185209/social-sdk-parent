import request from '@/api/request'

// 擦亮 API — 调后台 PolishService（暂未独立 Controller，复用 product 域透出端点）
// 后台 ProductController 需新增 /api/products/polish 端点，见后台 PolishService
export function polishItem(accountId, itemId) {
  return request.post('/products/polish', null, { params: { accountId, itemId } })
}

export function batchPolish(accountId, itemIds) {
  return request.post('/products/polish/batch', { accountId, itemIds })
}

export function superPolish(accountId, itemId, times) {
  return request.post('/products/polish/super', null, { params: { accountId, itemId, times } })
}
