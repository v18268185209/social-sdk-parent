import api from './request'
import { listStorageAccounts, listStorageFiles, shareStorageFile, uploadStorageFile } from './cloudStorage'

// ============== 虚拟发货任务 ==============
export function listVirtualShipTasks(params) {
  return api.get('/virtual-ship/tasks', { params })
}
export function triggerVirtualShip(id) {
  return api.post(`/virtual-ship/tasks/${id}/trigger`)
}

// ============== 自动发货配置 ==============
export function getVirtualShipConfig(accountId) {
  return api.get('/virtual-ship/config', { params: { accountId } })
}
export function saveVirtualShipConfig(data) {
  return api.post('/virtual-ship/config', data)
}

// ============== 卡密池 ==============
export function listVirtualCards(params) {
  return api.get('/virtual-ship/cards', { params })
}

/** 批量导入卡密 — POST /api/virtual-ship/cards/import  { productId, cards:[] } */
export function importVirtualCards(data) {
  return api.post('/virtual-ship/cards/import', data)
}

/** 批量删除卡密 — POST /api/virtual-ship/cards/batch  { cardIds:[] } */
export function batchDeleteVirtualCards(cardIds) {
  return api.post('/virtual-ship/cards/batch', { cardIds })
}

export function deleteVirtualCard(id) {
  return api.delete(`/virtual-ship/cards/${id}`)
}

// 文件管理复用 cloudStorage.js 导出
export { listStorageAccounts, listStorageFiles, shareStorageFile, uploadStorageFile }
