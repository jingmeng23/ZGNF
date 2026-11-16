import type { CartItem } from '../api/types'
import type { UserCoupon } from '../api/coupons'

// Parse decimal amounts as integer cents before arithmetic.
export function cents(value: number | string) {
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(String(value))
  if (!match) throw new Error('金额格式无效，请刷新后重试')
  const result = Number(match[1]) * 100 + Number((match[2] || '').padEnd(2, '0'))
  if (!Number.isSafeInteger(result)) throw new Error('金额超出可计算范围')
  return result
}
export function selectionState(items: CartItem[]) {
  const valid = items.filter((i) => i.available)
  const selected = valid.filter((i) => i.selected)
  return {
    all: valid.length > 0 && selected.length === valid.length,
    mixed: selected.length > 0 && selected.length < valid.length,
    kinds: selected.length,
    quantity: selected.reduce((sum, i) => sum + i.quantity, 0),
  }
}
export function cartSignature(items: CartItem[]) {
  return JSON.stringify(
    items
      .filter((i) => i.selected && i.available)
      .map((i) => [i.id, i.productId, i.quantity, cents(i.unitPrice), i.availableStock])
      .sort((a, b) => String(a[0]).localeCompare(String(b[0]))),
  )
}
export function changedProducts(before: CartItem[], after: CartItem[]) {
  return before
    .filter(
      (i) =>
        i.selected &&
        i.available &&
        !after.some(
          (j) =>
            j.id === i.id &&
            j.available &&
            j.selected &&
            j.quantity === i.quantity &&
            cents(j.unitPrice) === cents(i.unitPrice) &&
            j.availableStock === i.availableStock &&
            j.availableStock >= j.quantity,
        ),
    )
    .map((i) => i.productName)
}
export function couponReason(
  coupon: UserCoupon,
  amount: number,
  eligible: Set<string>,
  now = Date.now(),
) {
  if (coupon.status !== 'unused')
    return (
      ({ locked: '已被其他订单占用', used: '已使用', expired: '已过期' } as Record<string, string>)[
        coupon.status
      ] || '暂不可用'
    )
  const parse = (time: string) =>
    Date.parse(time.replace(' ', 'T') + (/[zZ]|[+-]\d{2}:\d{2}$/.test(time) ? '' : '+08:00'))
  if (parse(coupon.startTime) > now) return '尚未到使用时间'
  if (parse(coupon.expireTime) <= now) return '已过期'
  if (amount < cents(coupon.minimumAmount))
    return `未达到满 ¥${Number(coupon.minimumAmount).toFixed(2)} 的条件`
  if (amount <= cents(coupon.amount)) return '商品金额须大于券面额'
  return eligible.has(coupon.id) ? '' : '当前订单条件不适用'
}
