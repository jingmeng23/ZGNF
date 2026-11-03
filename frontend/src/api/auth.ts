import { request } from './http'
import type { CurrentUser, LoginResponse } from './types'

export interface RegistrationResult { userId: string; phoneLast4: string; status: string }

export const authApi = {
  login: (phone: string, password: string) => request<LoginResponse>('/users/login/password', { method: 'POST', body: JSON.stringify({ account: phone, password }) }),
  register: (phone: string, password: string, nickname?: string) => request<RegistrationResult>('/users/register', { method: 'POST', body: JSON.stringify({ phone, password, nickname }) }),
  me: () => request<CurrentUser>('/users/me'),
}
