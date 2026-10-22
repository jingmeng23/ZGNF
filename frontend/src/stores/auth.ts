import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '../api/auth'
import { session } from '../platform/session'
import type { CurrentUser } from '../api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(session.read()?.token || '')
  const user = ref<CurrentUser | null>(null)
  const authenticated = computed(() => !!token.value && !!session.read())
  async function login(phone: string, code: string) {
    const result = await authApi.login(phone, code)
    session.write(result.accessToken, result.expiresIn)
    token.value = result.accessToken
    user.value = await authApi.me()
  }
  function logout() { session.clear(); token.value = ''; user.value = null }
  return { token, user, authenticated, login, logout }
})
