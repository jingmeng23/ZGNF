import { request } from './http'

export interface AdminLoginResponse {
  accessToken: string
  expiresIn: number
  admin: {
    employeeId: string
    username: string
    roles: string[]
    permissions: string[]
  }
}

export const adminAuthApi = {
  login: (username: string, password: string) =>
    request<AdminLoginResponse>('/admin/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
}
