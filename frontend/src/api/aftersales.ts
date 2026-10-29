import { request } from './http'
import type { PageResult } from './types'

export const aftersaleStatusLabels = {
  pending: '待审核',
  approved: '待退款登记',
  rejected: '已拒绝 / 已撤销',
  completed: '退款已登记',
}
export type AftersaleStatus = keyof typeof aftersaleStatusLabels
export function aftersaleLabel(
  row: Pick<AftersaleRecord, 'status' | 'reviewRemark' | 'reviewedBy'>,
) {
  return row.status === 'rejected' && row.reviewedBy == null && row.reviewRemark === '用户撤销申请'
    ? '用户已撤销'
    : row.status === 'rejected'
      ? '审核未通过'
      : aftersaleStatusLabels[row.status]
}
export interface AftersaleRecord {
  id: string
  aftersaleNo: string
  orderId: string
  orderNo: string | null
  amount: number
  status: AftersaleStatus
  reason: string
  reviewRemark: string | null
  refundReference: string | null
  createTime: string
  updateTime: string
  reviewedBy: string | null
  completionRemark: string | null
  completedTime: string | null
}
export interface AftersaleOperation {
  id: string
  action: string
  fromStatus: string | null
  toStatus: string
  operatorId: string
  operatorType: string
  remark: string
  occurredAt: string
}
export interface AftersaleQuery {
  page: number
  size: number
  status: string
  aftersaleNo: string
  orderNo: string
}
function params(query: AftersaleQuery) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) if (value !== '') search.set(key, String(value))
  return search
}
const adminPath = (id: string) => `/admin/aftersales/${encodeURIComponent(id)}`
const userPath = (id: string) => `/aftersales/${encodeURIComponent(id)}`
export const adminAftersalesApi = {
  list: (query: AftersaleQuery) =>
    request<PageResult<AftersaleRecord>>(`/admin/aftersales?${params(query)}`),
  detail: (id: string) => request<AftersaleRecord>(adminPath(id)),
  operations: (id: string) => request<AftersaleOperation[]>(`${adminPath(id)}/operations`),
  review: (id: string, status: 'approved' | 'rejected', remark: string) =>
    request<AftersaleRecord>(`${adminPath(id)}/review`, {
      method: 'PUT',
      body: JSON.stringify({ status, remark }),
    }),
  complete: (id: string, refundReference: string, remark: string) =>
    request<AftersaleRecord>(`${adminPath(id)}/complete`, {
      method: 'PUT',
      body: JSON.stringify({ refundReference, remark }),
    }),
}
export const userAftersalesApi = {
  list: (query: AftersaleQuery) =>
    request<PageResult<AftersaleRecord>>(`/aftersales?${params(query)}`),
  detail: (id: string) => request<AftersaleRecord>(userPath(id)),
  operations: (id: string) => request<AftersaleOperation[]>(`${userPath(id)}/operations`),
  create: (input: { orderId: string; reason: string; requestId: string }) =>
    request<AftersaleRecord>('/aftersales', { method: 'POST', body: JSON.stringify(input) }),
  cancel: (id: string) => request<AftersaleRecord>(`${userPath(id)}/cancel`, { method: 'PUT' }),
}
