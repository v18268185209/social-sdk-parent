import api from './request'

// ============== OpenList 管理 API ==============
export const getOpenListStatus = () => api.get('/cloud-storage/openlist/status')
export const installOpenList = () => api.post('/cloud-storage/openlist/install')
export const startOpenList = () => api.post('/cloud-storage/openlist/start')
export const stopOpenList = () => api.post('/cloud-storage/openlist/stop')
export const restartOpenList = () => api.post('/cloud-storage/openlist/restart')
export const getOpenListProgress = () => api.get('/cloud-storage/openlist/progress')

// ============== 存储挂载管理 API ==============
export const listStorages = () => api.get('/cloud-storage/openlist/storages')
export const createStorage = (data) => api.post('/cloud-storage/openlist/storages', data)
export const updateStorage = (data) => api.post('/cloud-storage/openlist/storages/update', data)
export const deleteStorage = (id) => api.post(`/cloud-storage/openlist/storages/delete?id=${id}`)
export const enableStorage = (id) => api.post(`/cloud-storage/openlist/storages/enable?id=${id}`)
export const disableStorage = (id) => api.post(`/cloud-storage/openlist/storages/disable?id=${id}`)
export const getDriverInfo = (driver) => api.get('/cloud-storage/openlist/drivers', { params: { driver } })
export const listDrivers = () => api.get('/cloud-storage/openlist/drivers/list')

// ============== 文件管理 API (通过 OpenList) ==============
export const listOpenListFiles = (path) => api.get('/cloud-storage/openlist/files', { params: { path } })
export const uploadOpenListFile = (path, file) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('path', path)
  return api.post('/cloud-storage/openlist/files/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const deleteOpenListFile = (path, filename) => {
  return api.post('/cloud-storage/openlist/files/delete', { path, filename })
}

// 文件下载：后端 OpenListController 暂无 download 端点， 文件通过 OpenList 自身 HTTP 服务直接访问（cloudStorage/Index.vue 已用 /d/ 路径实现下载）

// ============== 账号管理 API ==============
export const listStorageAccounts = (accountId) => api.get('/cloud-storage/accounts', { params: accountId ? { accountId } : {} })
export const getStorageAccount = (id) => api.get(`/cloud-storage/accounts/${id}`)
export const deleteStorageAccount = (id) => api.delete(`/cloud-storage/accounts/${id}`)

// ============== 文件管理 API (旧接口，保留兼容) ==============
export const listStorageFiles = (storageAccountId) => api.get('/cloud-storage/files', { params: storageAccountId ? { storageAccountId } : {} })
export const uploadStorageFile = (storageAccountId, file) => {
  const fd = new FormData()
  fd.append('file', file)
  return api.post(`/cloud-storage/accounts/${storageAccountId}/files`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const shareStorageFile = (fileId) => api.post(`/cloud-storage/files/${fileId}/share`)
export const cancelShareStorageFile = (fileId) => api.delete(`/cloud-storage/files/${fileId}/share`)
