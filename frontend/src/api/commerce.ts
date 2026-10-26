import { request } from './http'
import type { Cart, Order, PageResult, Product, UserAddress } from './types'

export const cartApi = {
  get: () => request<Cart>('/cart'),
  add: (productId: string, quantity = 1) => request<Cart>('/cart/items', { method: 'POST', body: JSON.stringify({ productId: Number(productId), quantity }) }),
  quantity: (id: string, quantity: number) => request<Cart>(`/cart/items/${id}`, { method: 'PUT', body: JSON.stringify({ quantity }) }),
  remove: (id: string) => request<void>(`/cart/items/${id}`, { method: 'DELETE' }),
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
