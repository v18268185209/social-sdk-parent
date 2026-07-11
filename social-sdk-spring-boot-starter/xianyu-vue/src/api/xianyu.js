import { apiCall } from './http'

export const xianyuApi = {
  health: () => apiCall({ method: 'GET', url: '/health' }),

  listAccounts: () => apiCall({ method: 'GET', url: '/accounts' }),
  loginByCookies: (data) => apiCall({ method: 'POST', url: '/accounts/login', data }),
  refreshProfile: (id) => apiCall({ method: 'GET', url: `/accounts/${id}/profile` }),
  updateCookies: (id, data) => apiCall({ method: 'PUT', url: `/accounts/${id}/cookies`, data }),
  updateAccountStatus: (id, data) => apiCall({ method: 'PUT', url: `/accounts/${id}/status`, data }),
  deleteAccount: (id) => apiCall({ method: 'DELETE', url: `/accounts/${id}` }),
  createHeadlessQr: (data) => apiCall({ method: 'POST', url: '/accounts/login/headless-qr/create', data }),
  getHeadlessQrStatus: (sessionId) => apiCall({ method: 'GET', url: `/accounts/login/headless-qr/status/${sessionId}` }),
  invalidateHeadlessQr: (sessionId) => apiCall({ method: 'DELETE', url: `/accounts/login/headless-qr/${sessionId}` }),

  sendMessage: (data) => apiCall({ method: 'POST', url: '/messages/send', data }),
  getTimeline: (accountId, limit = 20) => apiCall({ method: 'GET', url: `/messages/timeline/${accountId}`, params: { limit } }),
  startChatTakeover: (data) => apiCall({ method: 'POST', url: '/chats/takeover/start', data }),
  stopChatTakeover: (accountId) => apiCall({ method: 'POST', url: `/chats/takeover/stop/${accountId}` }),
  getChatTakeoverStatus: (accountId) => apiCall({ method: 'GET', url: '/chats/takeover/status', params: { accountId } }),
  listChatEvents: (accountId) => apiCall({ method: 'GET', url: `/chats/events/${accountId}` }),

  listProducts: (accountId) => apiCall({ method: 'GET', url: '/products', params: { accountId } }),
  createProduct: (data) => apiCall({ method: 'POST', url: '/products', data }),
  updateProduct: (id, data) => apiCall({ method: 'PUT', url: `/products/${id}`, data }),
  deleteProduct: (id) => apiCall({ method: 'DELETE', url: `/products/${id}` }),

  listRules: (accountId) => apiCall({ method: 'GET', url: '/rules', params: { accountId } }),
  createRule: (data) => apiCall({ method: 'POST', url: '/rules', data }),
  updateRule: (id, data) => apiCall({ method: 'PUT', url: `/rules/${id}`, data }),
  deleteRule: (id) => apiCall({ method: 'DELETE', url: `/rules/${id}` }),
  matchRule: (data) => apiCall({ method: 'POST', url: '/rules/match', data })
}
