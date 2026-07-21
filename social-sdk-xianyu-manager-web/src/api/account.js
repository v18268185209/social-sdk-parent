import api from '@/api/request'

export function listAccounts() {
  return api.get('/accounts')
}
