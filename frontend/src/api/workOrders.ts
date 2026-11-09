import { request } from './http'
import type { PageResult, UserAddress } from './types'

export const workOrderTypes = { installation: '安装', repair: '维修', inspection: '安全检查' }
export const workOrderStatuses = { pending: '待派单', accepted: '已派单', processing: '处理中', completed: '已完结', cancelled: '已取消' }
export interface WorkOrderRecord {
  id: string; workOrderNo: string; userId: string; employeeId: string | null
  title: string; description: string; type: keyof typeof workOrderTypes; status: keyof typeof workOrderStatuses
  address: Pick<UserAddress, 'receiver' | 'phone' | 'province' | 'city' | 'district' | 'detail'> | null
  appointmentTime: string | null; acceptedTime: string | null; startedTime: string | null
  completedTime: string | null; result: string | null; createTime: string; updateTime: string
}
export interface WorkOrderLog { id: string; operatorId: string | null; operatorType: string | null; action: string; content: string; createTime: string }
export interface WorkOrderQuery { page: number; size: number; keyword: string; status: string; type: string; employeeId?: string; overdue?: string; sort?: string; order?: string }
export interface WorkOrderInput { type: string; title: string; description: string; addressId: string; appointmentTime: string; requestId: string }
export interface WorkOrderOptions { employees: { id: string; name: string }[] }
export interface WorkOrderProfile { employeeId: string; username: string; permissions: string[]; roles: string[] }
function params(query: object) { const result = new URLSearchParams(); for (const [key,value] of Object.entries(query)) if (value !== '' && value != null) result.set(key,String(value)); return result }
const root = '/admin/work-orders'
const detailPath = (id: string, mine = false) => `${root}${mine ? '/mine' : ''}/${encodeURIComponent(id)}`
export const adminWorkOrdersApi = {
  profile: () => request<WorkOrderProfile>('/admin/auth/me'),
  options: () => request<WorkOrderOptions>(`${root}/options`),
  list: (query: WorkOrderQuery, mine = false) => request<PageResult<WorkOrderRecord>>(`${root}${mine ? '/mine' : ''}?${params(query)}`),
  detail: (id: string, mine = false) => request<WorkOrderRecord>(detailPath(id,mine)),
  logs: (id: string, page = 1, mine = false) => request<PageResult<WorkOrderLog>>(`${detailPath(id,mine)}/logs?page=${page}&size=20`),
  assign: (id: string, input: { employeeId: string; remark: string }) => request<WorkOrderRecord>(`${detailPath(id)}/assign`, { method: 'PUT', body: JSON.stringify(input) }),
  start: (id: string, remark: string) => request<WorkOrderRecord>(`${detailPath(id)}/start`, { method: 'PUT', body: JSON.stringify({ remark }) }),
  complete: (id: string, remark: string) => request<WorkOrderRecord>(`${detailPath(id)}/complete`, { method: 'PUT', body: JSON.stringify({ remark }) }),
}
export const userWorkOrdersApi = {
  list: (query: WorkOrderQuery) => request<PageResult<WorkOrderRecord>>(`/work-orders?${params(query)}`),
  detail: (id: string) => request<WorkOrderRecord>(`/work-orders/${encodeURIComponent(id)}`),
  logs: (id: string, page = 1) => request<PageResult<WorkOrderLog>>(`/work-orders/${encodeURIComponent(id)}/logs?page=${page}&size=20`),
  create: (input: WorkOrderInput) => request<WorkOrderRecord>('/work-orders', { method: 'POST', body: JSON.stringify(input) }),
  cancel: (id: string, remark: string) => request<WorkOrderRecord>(`/work-orders/${encodeURIComponent(id)}/cancel`, { method: 'PUT', body: JSON.stringify({ remark }) }),
}
