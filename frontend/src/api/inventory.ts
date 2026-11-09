import { request } from './http'
import type { PageResult } from './types'

export interface InventoryRow {
  id: string
  productId: string
  productName: string
  quantity: number
  lockedQuantity: number
  availableQuantity: number
  version: number
  createTime: string
  updateTime: string
}
export interface InventoryLog {
  id: string
  inventoryId: string | null
  productId: string
  changeType: string
  changeQuantity: number
  quantityBefore: number | null
  quantityAfter: number | null
  lockedBefore: number | null
  lockedAfter: number | null
  operatorId: string | null
  requestId: string | null
  referenceNo: string | null
  remark: string | null
  createTime: string
}
export interface InventoryQuery {
  page: number
  size: number
  keyword: string
  availableMax: string
}
export interface InventoryLogQuery {
  page: number
  size: number
  changeType: string
  referenceNo: string
  inventoryId: string
}
export interface InventoryOptions {
  products: { id: string; name: string }[]
}
export interface InventoryMutation {
  quantity: number
  requestId: string
  remark: string
}

function params(query: object) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) if (value !== '') search.set(key, String(value))
  return search
}
export const inventoryApi = {
  detail: (id: string) => request<InventoryRow>(`/admin/inventory/${encodeURIComponent(id)}`),
  options: () => request<InventoryOptions>('/admin/inventory/options'),
  list: (query: InventoryQuery) =>
    request<PageResult<InventoryRow>>(`/admin/inventory?${params(query)}`),
  logs: (query: InventoryLogQuery) =>
    request<PageResult<InventoryLog>>(`/admin/inventory/logs?${params(query)}`),
  create: (input: { productId: string } & InventoryMutation) =>
    request<InventoryLog>('/admin/inventory', { method: 'POST', body: JSON.stringify(input) }),
  inbound: (id: string, input: InventoryMutation) =>
    request<InventoryLog>(`/admin/inventory/${encodeURIComponent(id)}/inbound`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  outbound: (id: string, input: InventoryMutation) =>
    request<InventoryLog>(`/admin/inventory/${encodeURIComponent(id)}/outbound`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  adjust: (id: string, input: InventoryMutation) =>
    request<InventoryLog>(`/admin/inventory/${encodeURIComponent(id)}/adjust`, {
      method: 'PUT',
      body: JSON.stringify(input),
    }),
}
