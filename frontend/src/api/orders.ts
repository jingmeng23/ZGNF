import { request } from './http'
import type { PageResult } from './types'

export type OrderStatus =
  'pending_payment' | 'paid' | 'processing' | 'completed' | 'cancelled' | 'refunded'
export const orderStatusLabels: Record<OrderStatus, string> = {
  pending_payment: '待付款',
  paid: '已付款待处理',
  processing: '履约处理中',
  completed: '已完成',
  cancelled: '已关闭',
  refunded: '已退款',
}
export interface OrderRecord {
  id: string
  userId: string
  orderNo: string
  status: OrderStatus
  totalAmount: number
  payAmount: number
  discountAmount: number
  freightAmount: number
  fullReductionAmount: number
  couponDiscountAmount: number
  userCouponId: string | null
  remark: string | null
  createTime: string
  expireTime: string | null
  address: {
    receiver: string
    phone: string
    province: string
    city: string
    district: string
    detail: string
  } | null
  items: {
    productId: string
    productName: string
    productImage: string | null
    quantity: number
    unitPrice: number
    amount: number
    discountAmount: number
    payAmount: number
  }[]
  timeline: {
    id: string
    fromStatus: OrderStatus | null
    toStatus: OrderStatus
    occurredAt: string
  }[]
  payments: {
    paymentNo: string
    channel: string
    status: string
    amount: number
    paidTime: string | null
  }[]
  logistics?: { carrier: string; trackingNo: string; summary: string } | null
}
export interface OrderOperation {
  id: string
  operatorId: string
  action: string
  remark: string
  occurredAt: string
}
export interface OrderListQuery {
  page: number
  size: number
  orderNo: string
  status: string
  userId?: string
  startTime?: string
  endTime?: string
}
function params(query: OrderListQuery) {
  const result = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value == null || value === '') continue
    let encoded = String(value)
    if (key === 'startTime' || key === 'endTime') {
      encoded = encoded.replace('T', ' ')
      if (encoded.length === 16) encoded += ':00'
    }
    result.set(key, encoded)
  }
  return result
}
const idPath = (id: string) => `/admin/orders/${encodeURIComponent(id)}`
export const adminOrdersApi = {
  list: (query: OrderListQuery) =>
    request<PageResult<OrderRecord>>(`/admin/orders?${params(query)}`),
  detail: (id: string) => request<OrderRecord>(idPath(id)),
  operations: (id: string) => request<OrderOperation[]>(`${idPath(id)}/operations`),
  process: (id: string) => request<OrderRecord>(`${idPath(id)}/process`, { method: 'PUT' }),
  cancel: (id: string, remark: string) =>
    request<OrderRecord>(`${idPath(id)}/cancel`, {
      method: 'PUT',
      body: JSON.stringify({ remark }),
    }),
  ship: (id: string, input: { carrier: string; trackingNo: string }) =>
    request<OrderRecord>(`${idPath(id)}/shipment`, { method: 'PUT', body: JSON.stringify(input) }),
  note: (id: string, remark: string) =>
    request<OrderOperation[]>(`${idPath(id)}/notes`, {
      method: 'POST',
      body: JSON.stringify({ remark }),
    }),
}
export const userOrdersApi = {
  list: (query: OrderListQuery) => request<PageResult<OrderRecord>>(`/orders?${params(query)}`),
  detail: (id: string) => request<OrderRecord>(`/orders/${encodeURIComponent(id)}`),
  cancel: (id: string) =>
    request<OrderRecord>(`/orders/${encodeURIComponent(id)}/cancel`, { method: 'PUT' }),
  confirmReceipt: (id: string) =>
    request<OrderRecord>(`/orders/${encodeURIComponent(id)}/confirm-receipt`, { method: 'PUT' }),
}
