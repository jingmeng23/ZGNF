import { request } from './http'
import type { PageResult } from './types'

export interface ManagedUser {
  id: string
  nickname: string | null
  phoneLast4: string | null
  status: string
  memberLevel: string
  points: number
  createTime: string
  avatar?: string | null
  email?: string | null
  gender?: string | null
  birthday?: string | null
  updateTime?: string
}
const root = '/admin/users'
export const usersApi = {
  list: (q: { page: number; size: number; keyword: string; status: string }) => {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(q)) if (value !== '') params.set(key, String(value))
    return request<PageResult<ManagedUser>>(`${root}?${params}`)
  },
  detail: (id: string) => request<ManagedUser>(`${root}/${encodeURIComponent(id)}`),
  status: (id: string, status: string) => request<void>(`${root}/${encodeURIComponent(id)}/status`, { method: 'PUT', body: JSON.stringify({ status }) }),
  remove: (id: string) => request<void>(`${root}/${encodeURIComponent(id)}`, { method: 'DELETE' }),
}
