import request from '@/api/request'

// 评价与信用 API — 对齐后台 ReviewController (/api/reviews)
export function reviewOrder(accountId, orderId, rating, content) {
  return request.post('/reviews/orders/' + orderId, null, {
    params: { accountId, rating, content }
  })
}

export function listReviews(accountId, buyerId, page = 1, pageSize = 20) {
  return request.get('/reviews', {
    params: { accountId, buyerId, page, pageSize }
  })
}

export function getCredit(accountId, userId) {
  return request.get('/reviews/credit', {
    params: { accountId, userId }
  })
}

export function applyRefund(accountId, orderId, reason, amount) {
  return request.post('/reviews/refunds', null, {
    params: { accountId, orderId, reason, amount }
  })
}

export function listRefunds(accountId, disputeStatus, page = 1, pageSize = 20) {
  return request.get('/reviews/refunds', {
    params: { accountId, disputeStatus, page, pageSize }
  })
}

export function getRefundDetail(accountId, refundId) {
  return request.get('/reviews/refunds/' + refundId, {
    params: { accountId }
  })
}
