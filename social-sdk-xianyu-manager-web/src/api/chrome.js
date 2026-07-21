import api from '@/api/request'

export function getChromeConfig() {
  return api.get('/chrome-config')
}

export function detectChrome() {
  return api.get('/chrome-config/detect')
}

export function detectAllChrome() {
  return api.get('/chrome-config/detect/all')
}

export function saveChromeConfig(data) {
  return api.post('/chrome-config/save', data)
}

export function downloadChrome() {
  return api.post('/chrome-config/download')
}

export function validateChromePath(path) {
  return api.post('/chrome-config/validate', { path })
}
