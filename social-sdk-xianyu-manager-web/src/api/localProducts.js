import api from '@/api/request'

export function listLocalProducts(params) {
  return api.get('/local-products', { params })
}

export function getLocalProduct(id) {
  return api.get(`/local-products/${id}`)
}

export function saveLocalProduct(data) {
  return api.post('/local-products', data)
}

export function updateLocalProduct(id, data) {
  return api.put(`/local-products/${id}`, data)
}

export function deleteLocalProduct(id) {
  return api.delete(`/local-products/${id}`)
}

export function publishLocalProduct(id) {
  return api.post(`/local-products/${id}/publish`)
}

export function batchPublishLocalProducts(data) {
  return api.post('/local-products/batch-publish', data)
}
