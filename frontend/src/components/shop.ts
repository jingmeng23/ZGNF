import type { Matrix } from '../api/mall'

export const catalogScroll = new Map<string, number>()

export const formatPrice = (value: number) =>
  `¥${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export function priceRange(min: string, max: string) {
  const values = [min.trim(), max.trim()]
  if (
    values.some(
      (value) =>
        value &&
        (!/^\d+(\.\d{1,2})?$/.test(value) ||
          !Number.isFinite(Number(value)) ||
          Number(value) > 999999999.99),
    )
  )
    return { error: '请输入不小于 0 的有效价格，最多保留两位小数' }
  if (values[0] && values[1] && Number(values[0]) > Number(values[1]))
    return { error: '最低价不能高于最高价' }
  return {
    min: values[0] ? String(Number(values[0])) : '',
    max: values[1] ? String(Number(values[1])) : '',
    error: '',
  }
}

export function matchingSku(matrix: Matrix | undefined, selection: Record<string, string>) {
  if (!matrix || matrix.dimensions.some((d) => !selection[d.name])) return undefined
  if (!matrix.dimensions.length && matrix.skus.length !== 1) return undefined
  return matrix.skus.find(
    (s) => s.selectable && matrix.dimensions.every((d) => s.specs[d.name] === selection[d.name]),
  )
}

export function validQuantity(value: string, maximum: number) {
  return (
    /^\d+$/.test(value) &&
    Number.isSafeInteger(Number(value)) &&
    Number(value) >= 1 &&
    Number(value) <= maximum
  )
}
