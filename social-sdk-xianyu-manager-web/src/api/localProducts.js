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

export function previewLocalProductImport(params) {
  const fd = new FormData()
  fd.append('file', params.file)
  if (params.deduplicate != null) fd.append('deduplicate', params.deduplicate)
  if (params.overwriteDuplicate != null) fd.append('overwriteDuplicate', params.overwriteDuplicate)
  if (params.defaultGoodsType) fd.append('defaultGoodsType', params.defaultGoodsType)
  if (params.imageStoragePath) fd.append('imageStoragePath', params.imageStoragePath)
  if (params.deliverContentSeparator) fd.append('deliverContentSeparator', params.deliverContentSeparator)
  return api.post('/local-products/import/preview', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function confirmLocalProductImport(params) {
  const fd = new FormData()
  fd.append('file', params.file)
  if (params.deduplicate != null) fd.append('deduplicate', params.deduplicate)
  if (params.overwriteDuplicate != null) fd.append('overwriteDuplicate', params.overwriteDuplicate)
  if (params.defaultGoodsType) fd.append('defaultGoodsType', params.defaultGoodsType)
  if (params.imageStoragePath) fd.append('imageStoragePath', params.imageStoragePath)
  if (params.deliverContentSeparator) fd.append('deliverContentSeparator', params.deliverContentSeparator)
  return api.post('/local-products/import/confirm', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
