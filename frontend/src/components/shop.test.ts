import { expect, it } from 'vitest'
import { priceRange, validQuantity, matchingSku } from './shop'
import type { Matrix } from '../api/mall'

it('validates a combined price range without treating zero or empty as invalid', () => {
  expect(priceRange('', '')).toEqual({ min: '', max: '', error: '' })
  expect(priceRange(' 0 ', '12.50')).toEqual({ min: '0', max: '12.5', error: '' })
  for (const value of ['-1', 'NaN', 'Infinity', '1e2', '1.001', 'abc'])
    expect(priceRange(value, '').error).toBeTruthy()
  expect(priceRange('20', '10').error).toContain('最低价')
})
it('rejects decimal, exponential, empty, negative and overstock quantities', () => {
  for (const value of ['', '0', '-1', '1.5', '1e2', '5', 'abc'])
    expect(validQuantity(value, 4)).toBe(false)
  expect(validQuantity('4', 4)).toBe(true)
  expect(validQuantity('1', 0)).toBe(false)
})
it('never resolves an incomplete, unavailable or ambiguous SKU selection', () => {
  const matrix: Matrix = {
    dimensions: [
      { name: '颜色', options: [] },
      { name: '容量', options: [] },
    ],
    selectedSkuId: '1',
    skus: [
      {
        id: '1',
        code: 'TEST',
        name: '测试规格',
        imageUrl: null,
        price: 10,
        originalPrice: null,
        availableStock: 2,
        reason: '',
        specs: { 颜色: '红', 容量: '小' },
        selectable: true,
      },
    ],
  }
  expect(matchingSku(matrix, { 颜色: '红' })).toBeUndefined()
  expect(matchingSku(matrix, { 颜色: '红', 容量: '小' })?.id).toBe('1')
  matrix.skus[0].selectable = false
  expect(matchingSku(matrix, { 颜色: '红', 容量: '小' })).toBeUndefined()
})
