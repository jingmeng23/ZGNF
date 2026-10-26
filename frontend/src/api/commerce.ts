import { request } from './http'
import type { Cart, Order, PageResult, Product, UserAddress } from './types'

export const cartApi = {
  get: (page = 1, size = 20) => request<Cart>(`/cart?page=${page}&size=${size}`),
  add: (productId: string, quantity = 1) => request<Cart>('/cart/items', { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ productId: Number(productId), quantity }) }),
  quantity: (id: string, quantity: number) => request<Cart>(`/cart/items/${id}`, { method: 'PUT', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ quantity }) }),
  remove: (id: string) => request<void>(`/cart/items/${id}`, { method: 'DELETE' }),
  removeMany: (ids: string[]) => request<void>('/cart/items', { method: 'DELETE', body: JSON.stringify({ ids: ids.map(Number) }) }),
  removeSelected: () => request<void>('/cart/selected', { method: 'DELETE' }),
  clear: () => request<void>('/cart', { method: 'DELETE' }),
  select: (id: string, selected: boolean) => request<Cart>(`/cart/items/${id}/selection`, { method: 'PUT', body: JSON.stringify({ selected }) }),
  selectAll: (selected: boolean) => request<Cart>('/cart/selection', { method: 'PUT', body: JSON.stringify({ selected }) }),
}

export const addressApi = {
  list: () => request<UserAddress[]>('/users/me/addresses'),
}

export const orderApi = {
  list: (page = 1, size = 20) => request<PageResult<Order>>(`/orders?page=${page}&size=${size}`),
  create: (addressId: string) => request<Order>('/orders', {
    method: 'POST',
    body: JSON.stringify({ addressId: Number(addressId), requestId: crypto.randomUUID() }),
  }),
}

export type { Cart, Order, Product }
