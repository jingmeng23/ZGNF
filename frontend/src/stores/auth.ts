import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '../api/auth'
import { session } from '../platform/session'
import type { CurrentUser } from '../api/types'
import { loginDisabled } from '../platform/testMode'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(session.read()?.token || '')
  const user = ref<CurrentUser | null>(loginDisabled ? { userId: '-1' } : null)
  const authenticated = computed(() => loginDisabled || (!!token.value && !!session.read()))
  async function applyLogin(result: { accessToken: string; expiresIn: number }) {
    session.write(result.accessToken, result.expiresIn)
    token.value = result.accessToken
    user.value = await authApi.me()
  }
  async function login(phone: string, password: string) { await applyLogin(await authApi.login(phone, password)) }
  function logout() { session.clear(); token.value = ''; user.value = null }
  return { token, user, authenticated, login, applyLogin, logout }
})
