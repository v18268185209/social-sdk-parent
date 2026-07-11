import axios from 'axios'

const http = axios.create({
  baseURL: '/api/social-sdk/xianyu',
  timeout: 30000
})

export async function apiCall(config) {
  const response = await http.request(config)
  const payload = response?.data
  if (!payload?.success) {
    const message = payload?.message || 'Request failed'
    const error = new Error(message)
    error.payload = payload
    throw error
  }
  return payload.data
}

export default http
