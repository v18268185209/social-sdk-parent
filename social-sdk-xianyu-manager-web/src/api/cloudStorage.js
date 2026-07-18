import api from './request'

// ============== 网盘账号 ==============
export function listStorageAccounts(accountId) {
  return api.get('/cloud-storage/accounts', { params: accountId ? { accountId } : {} })
}
export function getStorageAccount(id) {
  return api.get(`/cloud-storage/accounts/${id}`)
}
export function updateStorageAccount(id, data) {
  return api.put(`/cloud-storage/accounts/${id}`, data)
}
export function deleteStorageAccount(id) {
  return api.delete(`/cloud-storage/accounts/${id}`)
}

// ============== OAuth ==============
export function getAuthUrl(provider, redirectUri) {
  return api.get('/cloud-storage/auth-url', { params: { provider, redirectUri } })
}
export function handleOAuthCallback(provider, code, state, accountId) {
  return api.post('/cloud-storage/callback', null, {
    params: { provider, code, state, accountId }
  })
}

// ============== 文件 ==============
export function listStorageFiles(storageAccountId) {
  return api.get('/cloud-storage/files', { params: storageAccountId ? { storageAccountId } : {} })
}
export function uploadStorageFile(storageAccountId, file) {
  const fd = new FormData()
  fd.append('file', file)
  return api.post(`/cloud-storage/accounts/${storageAccountId}/files`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export function shareStorageFile(fileId) {
  return api.post(`/cloud-storage/files/${fileId}/share`)
}
export function cancelShareStorageFile(fileId) {
  return api.delete(`/cloud-storage/files/${fileId}/share`)
}
