import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCartStore } from './cart'
import type { Product } from '../api/types'
const product = { id: '9007199254740993', name: '接口测试商品', price: 12 } as Product
beforeEach(() => setActivePinia(createPinia()))
afterEach(() => vi.unstubAllGlobals())
it('preserves Long IDs and uses the successful write response to update the cart count', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValue(
      new Response(
        JSON.stringify({ code: 200, data: { items: [], totalQuantity: 3, selectedAmount: 36 } }),
      ),
    )
  vi.stubGlobal('fetch', fetchMock)
  const store = useCartStore()
  await store.add(product, 3)
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
    productId: '9007199254740993',
    quantity: 3,
  })
  expect(store.count).toBe(3)
  expect(fetchMock).toHaveBeenCalledTimes(1)
})
it('does not submit SKU products or invalid quantities to the product-only cart', async () => {
  const fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  const store = useCartStore()
  await expect(store.add({ ...product, skuCount: 2 })).rejects.toThrow('暂不支持')
  for (const quantity of [0, -1, 1.5, 1000])
    await expect(store.add(product, quantity)).rejects.toThrow('整数')
  expect(fetchMock).not.toHaveBeenCalled()
})

const row = (id = '1', quantity = 1) => ({
  id,
  productId: id,
  productName: `商品${id}`,
  coverUrl: null,
  quantity,
  unitPrice: 10,
  availableStock: 20,
  selected: true,
  available: true,
  subtotal: quantity * 10,
  priceChanged: false,
})
const snapshot = (quantity = 1) => ({
  items: [row('1', quantity)],
  invalidItems: [],
  total: 1,
  page: 1,
  size: 100,
  totalQuantity: quantity,
  selectedQuantity: quantity,
  selectedAmount: quantity * 10,
})
const response = (data: unknown) => new Response(JSON.stringify({ code: 200, data }))

it('serializes quick quantity writes and applies the final authoritative result', async () => {
  let release!: (value: Response) => void
  const first = new Promise<Response>((resolve) => {
    release = resolve
  })
  const mock = vi
    .fn()
    .mockReturnValueOnce(first)
    .mockResolvedValueOnce(response(snapshot(4)))
  vi.stubGlobal('fetch', mock)
  const store = useCartStore()
  store.cart = snapshot()
  const a = store.setQuantity('1', 2),
    b = store.setQuantity('1', 4)
  await vi.waitFor(() => expect(mock).toHaveBeenCalledTimes(1))
  expect(store.busy).toBe(true)
  release(response(snapshot(2)))
  await Promise.all([a, b])
  expect(mock).toHaveBeenCalledTimes(2)
  expect(store.items[0]?.quantity).toBe(4)
  expect(store.cart.selectedAmount).toBe(40)
  expect(store.busy).toBe(false)
})

it('recovers authoritative state after a failed quantity write', async () => {
  const mock = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ code: 400, message: '库存变化' }), { status: 400 }),
    )
    .mockResolvedValueOnce(response(snapshot(1)))
  vi.stubGlobal('fetch', mock)
  const store = useCartStore()
  store.cart = snapshot(2)
  await expect(store.setQuantity('1', 3)).rejects.toThrow('库存变化')
  expect(store.items[0]?.quantity).toBe(1)
  expect(store.cart.selectedAmount).toBe(10)
  expect(store.loadError).toBe('')
})

it('loads every cart page and preserves string identifiers for deletion', async () => {
  const first = {
    ...snapshot(),
    total: 2,
    totalQuantity: 2,
    selectedQuantity: 2,
    selectedAmount: 20,
  }
  const mock = vi
    .fn()
    .mockResolvedValueOnce(response(first))
    .mockResolvedValueOnce(response({ ...first, page: 2, items: [row('2')] }))
    .mockResolvedValueOnce(response(null))
    .mockResolvedValueOnce(response(snapshot()))
  vi.stubGlobal('fetch', mock)
  const store = useCartStore()
  await store.refreshAll()
  expect(store.items.map((i) => i.id)).toEqual(['1', '2'])
  expect(mock.mock.calls[1][0]).toContain('page=2')
  await store.removeMany(['9007199254740993'])
  expect(JSON.parse(mock.mock.calls[2][1].body)).toEqual({ ids: ['9007199254740993'] })
})

it('ignores an outstanding read after logout', async () => {
  let release!: (value: Response) => void
  const mock = vi.fn().mockReturnValue(
    new Promise<Response>((resolve) => {
      release = resolve
    }),
  )
  vi.stubGlobal('fetch', mock)
  const store = useCartStore()
  const request = store.refreshAll()
  await vi.waitFor(() => expect(mock).toHaveBeenCalledTimes(1))
  store.reset()
  release(response(snapshot(4)))
  await request
  expect(store.count).toBe(0)
  expect(store.items).toEqual([])
})

it('does not restore deleted-session data when a mutation refresh completes late', async () => {
  let release!: (value: Response) => void
  const mock = vi
    .fn()
    .mockResolvedValueOnce(response(null))
    .mockReturnValueOnce(
      new Promise<Response>((resolve) => {
        release = resolve
      }),
    )
  vi.stubGlobal('fetch', mock)
  const store = useCartStore()
  store.cart = snapshot()
  const request = store.removeMany(['1'])
  await vi.waitFor(() => expect(mock).toHaveBeenCalledTimes(2))
  store.reset()
  release(response(snapshot(4)))
  await request
  expect(store.items).toEqual([])
  expect(store.count).toBe(0)
  expect(store.busy).toBe(false)
})
