import { request } from './http'
import type { PageResult } from './types'
export interface Coupon { id: string; name: string; amount: number; minimumAmount: number; startTime: string; endTime: string; quantity: number; status: string; claimedQuantity: number; remainingQuantity: number }
export interface UserCoupon { id: string; couponId: string; name: string; amount: number; minimumAmount: number; startTime: string; expireTime: string; status: string; usedOrderId: string | null }
export type CouponInput = Pick<Coupon, 'name' | 'amount' | 'minimumAmount' | 'startTime' | 'endTime' | 'quantity' | 'status'>
const wireTime = (value: string) => {
  const time = value.replace('T', ' ')
  return time.length === 16 ? `${time}:00` : time
}
export const couponsApi = {
  available: (page = 1, keyword = '') => request<PageResult<Coupon>>(`/coupons?${new URLSearchParams({ page: String(page), size: '12', keyword })}`),
  mine: (page = 1, status = '', orderAmount?: number) => request<PageResult<UserCoupon>>(`/user-coupons?${new URLSearchParams({ page: String(page), size: '12', ...(status ? { status } : {}), ...(orderAmount !== undefined ? { orderAmount: orderAmount.toFixed(2) } : {}) })}`),
  claim: (id: string) => request<UserCoupon>(`/coupons/${encodeURIComponent(id)}/claim`, { method: 'POST' }),
  adminList: (page = 1, keyword = '', status = '') => request<PageResult<Coupon>>(`/admin/coupons?${new URLSearchParams({ page: String(page), size: '12', keyword, ...(status ? { status } : {}) })}`),
  detail: (id: string) => request<Coupon>(`/admin/coupons/${encodeURIComponent(id)}`),
  save: (id: string | null, input: CouponInput) => request<Coupon>(id ? `/admin/coupons/${encodeURIComponent(id)}` : '/admin/coupons', { method: id ? 'PUT' : 'POST', body: JSON.stringify({ ...input, startTime: wireTime(input.startTime), endTime: wireTime(input.endTime) }) }),
  remove: (id: string) => request<void>(`/admin/coupons/${encodeURIComponent(id)}`, { method: 'DELETE' }),
}
