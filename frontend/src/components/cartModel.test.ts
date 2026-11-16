import { describe, expect, it } from 'vitest'
import type { CartItem } from '../api/types'
import { cartSignature, cents, changedProducts, couponReason, selectionState } from './cartModel'

const item = (overrides: Partial<CartItem> = {}): CartItem => ({
  id: '1',
  productId: '10',
  productName: '测试商品',
  coverUrl: null,
  quantity: 2,
  unitPrice: 12.35,
  availableStock: 5,
  selected: true,
  available: true,
  subtotal: 24.7,
  priceChanged: false,
  ...overrides,
})

describe('cart model helpers', () => {
  it('uses integer cents and rejects malformed amounts', () => {
    expect(cents('12.3')).toBe(1230)
    expect(cents(0.29) + cents(0.01)).toBe(30)
    expect(() => cents('-1')).toThrow()
    expect(() => cents('1.234')).toThrow()
  })

  it('counts selected valid lines and excludes invalid selections', () => {
    expect(
      selectionState([
        item(),
        item({ id: '2', productId: '20', selected: false }),
        item({ id: '3', available: false }),
      ]),
    ).toEqual({ all: false, mixed: true, kinds: 1, quantity: 2 })
  })

  it('detects changes to a selected line before checkout', () => {
    expect(cartSignature([item()])).not.toBe(cartSignature([item({ unitPrice: 13 })]))
    expect(changedProducts([item()], [item({ availableStock: 1 })])).toEqual(['测试商品'])
    expect(changedProducts([item()], [item({ selected: false })])).toEqual(['测试商品'])
  })

  it('reports coupon threshold, face value and server eligibility distinctly', () => {
    const coupon = {
      id: 'c1',
      couponId: 'coupon1',
      name: '订单券',
      amount: 10,
      minimumAmount: 10,
      startTime: '2026-01-01 00:00:00',
      expireTime: '2026-12-01 00:00:00',
      status: 'unused',
      usedOrderId: null,
    }
    expect(
      couponReason(coupon, 900, new Set(['c1']), Date.parse('2026-06-01T00:00:00+08:00')),
    ).toContain('满')
    expect(
      couponReason(coupon, 1000, new Set(['c1']), Date.parse('2026-06-01T00:00:00+08:00')),
    ).toContain('券面额')
    expect(
      couponReason(coupon, 4000, new Set(), Date.parse('2026-06-01T00:00:00+08:00')),
    ).toContain('不适用')
    expect(
      couponReason(coupon, 4000, new Set(['c1']), Date.parse('2026-06-01T00:00:00+08:00')),
    ).toBe('')
  })
})
