import { request } from './http'
import type { CurrentUser, LoginResponse } from './types'

export const authApi = {
  sendCode: (phone: string) => request<null>('/auth/sms-code', { method: 'POST', body: JSON.stringify({ phone }) }),
  login: (phone: string, code: string) => request<LoginResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ phone, code }) }),
  me: () => request<CurrentUser>('/users/me'),
}
