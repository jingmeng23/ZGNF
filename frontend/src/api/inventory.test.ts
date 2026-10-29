import { afterEach, expect, it, vi } from 'vitest'
import { inventoryApi } from './inventory'

afterEach(() => vi.unstubAllGlobals())
function mockFetch() {
  const fetchMock = vi
    .fn()
    .mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify({ code: 200, data: {} }))),
    )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
it('connects stock filters, including zero available stock and headquarters=false', async () => {
  const fetchMock = mockFetch()
  await inventoryApi.list({
    page: 2,
    size: 10,
    keyword: '燃气 & 灶',
    headquarters: 'false',
    availableMax: '0',
  })
  const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost')
  expect(url.pathname).toBe('/api/v1/admin/inventory')
  expect(Object.fromEntries(url.searchParams)).toEqual({
    page: '2',
    size: '10',
    keyword: '燃气 & 灶',
    headquarters: 'false',
    availableMax: '0',
  })
})
it('omits unset optional filters and loads inventory selectors', async () => {
  const fetchMock = mockFetch()
  await inventoryApi.list({ page: 1, size: 20, keyword: '', headquarters: '', availableMax: '' })
  await inventoryApi.options()
  await inventoryApi.detail('9007199254740993')
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/inventory?page=1&size=20')
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/inventory/options')
  expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/admin/inventory/9007199254740993')
})
it('keeps Long identifiers as strings and sends server audit fields on each mutation', async () => {
  const fetchMock = mockFetch()
  const input = { quantity: 3, requestId: 'stock-request', remark: '采购入库' }
  await inventoryApi.create({ ...input, productId: '9007199254740993', storeId: null })
  await inventoryApi.inbound('9007199254740995', input)
  await inventoryApi.outbound('9007199254740995', input)
  await inventoryApi.adjust('9007199254740995', { ...input, quantity: 0 })
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
    ...input,
    productId: '9007199254740993',
    storeId: null,
  })
  for (const [index, action] of ['inbound', 'outbound', 'adjust'].entries()) {
    expect(fetchMock.mock.calls[index + 1][0]).toBe(
      `/api/v1/admin/inventory/9007199254740995/${action}`,
    )
    expect(fetchMock.mock.calls[index + 1][1].method).toBe(action === 'adjust' ? 'PUT' : 'POST')
    expect(JSON.parse(fetchMock.mock.calls[index + 1][1].body)).toEqual({
      ...input,
      quantity: action === 'adjust' ? 0 : 3,
    })
  }
})
it('filters audit history by inventory, order number and movement type', async () => {
  const fetchMock = mockFetch()
  await inventoryApi.logs({
    page: 1,
    size: 20,
    inventoryId: '9007199254740993',
    changeType: 'order_lock',
    referenceNo: 'ORDER-123',
  })
  const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost')
  expect(url.pathname).toBe('/api/v1/admin/inventory/logs')
  expect(Object.fromEntries(url.searchParams)).toEqual({
    page: '1',
    size: '20',
    inventoryId: '9007199254740993',
    changeType: 'order_lock',
    referenceNo: 'ORDER-123',
  })
})
