import { request } from './http'
import type { PageResult } from './types'

export interface EmployeeInput {
  username: string
  name: string
  phone: string
  storeId: string | null
  departmentId: string | null
  positionId: string | null
  status: 'active' | 'inactive'
}
export interface EmployeeRecord extends EmployeeInput {
  id: string
  roleIds: string[]
  createTime: string
  updateTime: string
}
export interface OrganizationOption { id: string; name: string; status: string }
export interface EmployeeOptions {
  stores: OrganizationOption[]
  departments: OrganizationOption[]
  positions: OrganizationOption[]
  roles: OrganizationOption[]
  canWrite: boolean
  canManageRoles: boolean
  currentEmployeeId: string
}
export interface EmployeeQuery {
  page: number; size: number; keyword: string; status: string; storeId: string; roleId: string
}
const root = '/admin/employees'
const path = (id: string) => `${root}/${encodeURIComponent(id)}`
export const employeesApi = {
  options: () => request<EmployeeOptions>(`${root}/options`),
  list: (query: EmployeeQuery) => {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(query)) if (value !== '') params.set(key, String(value))
    return request<PageResult<EmployeeRecord>>(`${root}?${params}`)
  },
  detail: (id: string) => request<EmployeeRecord>(path(id)),
  createEmployee: (employee: EmployeeInput, password: string) => request<EmployeeRecord>(root, { method: 'POST', body: JSON.stringify({ employee, password }) }),
  update: (id: string, employee: EmployeeInput) => request<EmployeeRecord>(path(id), { method: 'PUT', body: JSON.stringify(employee) }),
  status: (id: string, status: EmployeeInput['status']) => request<EmployeeRecord>(`${path(id)}/status`, { method: 'PUT', body: JSON.stringify({ status }) }),
  roles: (id: string, ids: string[]) => request<EmployeeRecord>(`${path(id)}/roles`, { method: 'PUT', body: JSON.stringify({ ids }) }),
  resetPassword: (id: string, password: string) => request<void>(`${path(id)}/password`, { method: 'PUT', body: JSON.stringify({ password }) }),
  deleteEmployee: (id: string) => request<void>(path(id), { method: 'DELETE' }),
}
