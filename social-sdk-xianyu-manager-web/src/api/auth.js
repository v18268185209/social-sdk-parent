import api from '@/api/request'

export function login(data) {
  return api.post('/auth/login', data)
}

export function getProfile() {
  return api.get('/auth/profile')
}
