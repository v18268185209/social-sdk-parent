import api from './request'

// ============== OpenList 管理 ==============
export function getOpenListStatus() {
  return api.get('/cloud-storage/openlist/status')
}

export function installOpenList() {
  return api.post('/cloud-storage/openlist/install')
}

export function startOpenList() {
  return api.post('/cloud-storage/openlist/start')
}

export function stopOpenList() {
  return api.post('/cloud-storage/openlist/stop')
}

export function restartOpenList() {
  return api.post('/cloud-storage/openlist/restart')
}

export function getOpenListProgress() {
  return api.get('/cloud-storage/openlist/progress')
}

export function getOpenListInfo() {
  return api.get('/cloud-storage/openlist/info')
}

// ============== 网盘账号 ==============
export function listStorageAccounts(accountId) {
  return api.get('/cloud-storage/accounts', { params: accountId ? { accountId } : {} })
}

export function getStorageAccount(id) {
  return api.get(`/cloud-storage/accounts/${id}`)
}

export function deleteStorageAccount(id) {
  return api.delete(`/cloud-storage/accounts/${id}`)
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
