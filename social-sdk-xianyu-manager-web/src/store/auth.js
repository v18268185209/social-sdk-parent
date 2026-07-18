import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getProfile } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref('')
  const user = ref(null)

  // 从 localStorage 恢复
  const savedToken = localStorage.getItem('token')
  if (savedToken) {
    token.value = savedToken
  }

  const isLoggedIn = computed(() => !!token.value)

  async function login(username, password) {
    const res = await loginApi({ username, password })
    if (res.success && res.data) {
      token.value = res.data.token
      localStorage.setItem('token', res.data.token)
      user.value = res.data.user
      return true
    }
    return false
  }

  async function fetchProfile() {
    try {
      const res = await getProfile()
      if (res.success) {
        user.value = res.data
      }
    } catch (e) {
      // ignore
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
  }

  return { token, user, isLoggedIn, login, fetchProfile, logout }
})
