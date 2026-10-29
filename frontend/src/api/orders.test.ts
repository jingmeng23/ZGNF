import { afterEach, expect, it, vi } from 'vitest'
import { adminOrdersApi, userOrdersApi } from './orders'

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
it('encodes admin order search, including date range and status', async () => {
  const fetchMock = mockFetch()
  await adminOrdersApi.list({
    page: 2,
    size: 20,
    orderNo: 'GN/01',
    status: 'paid',
    userId: '9007199254740993',
    startTime: '2026-10-28T08:30',
    endTime: '2026-10-28T09:30',
  })
  const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost')
  expect(url.pathname).toBe('/api/v1/admin/orders')
  expect(Object.fromEntries(url.searchParams)).toEqual({
    page: '2',
    size: '20',
    orderNo: 'GN/01',
    status: 'paid',
    userId: '9007199254740993',
    startTime: '2026-10-28 08:30:00',
    endTime: '2026-10-28 09:30:00',
  })
})
it('connects order detail, process, close, shipment, notes and operations', async () => {
  const fetchMock = mockFetch()
  await adminOrdersApi.detail('9007199254740995')
  await adminOrdersApi.process('42')
  await adminOrdersApi.cancel('42', '客户要求关闭')
  await adminOrdersApi.ship('42', { carrier: '测试物流', trackingNo: 'ABC-123' })
  await adminOrdersApi.note('42', '已电话确认')
  await adminOrdersApi.operations('42')
  expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
    '/api/v1/admin/orders/9007199254740995',
    '/api/v1/admin/orders/42/process',
    '/api/v1/admin/orders/42/cancel',
    '/api/v1/admin/orders/42/shipment',
    '/api/v1/admin/orders/42/notes',
    '/api/v1/admin/orders/42/operations',
  ])
  expect(fetchMock.mock.calls[1][1].method).toBe('PUT')
  expect(fetchMock.mock.calls[2][1].method).toBe('PUT')
  expect(fetchMock.mock.calls[3][1].method).toBe('PUT')
  expect(fetchMock.mock.calls[4][1].method).toBe('POST')
  expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({ remark: '客户要求关闭' })
  expect(JSON.parse(fetchMock.mock.calls[3][1].body)).toEqual({
    carrier: '测试物流',
    trackingNo: 'ABC-123',
  })
  expect(JSON.parse(fetchMock.mock.calls[4][1].body)).toEqual({ remark: '已电话确认' })
})
it('connects user order list, detail, cancellation and receipt confirmation', async () => {
  const fetchMock = mockFetch()
  await userOrdersApi.list({ page: 1, size: 10, orderNo: '', status: '' })
  await userOrdersApi.detail('42')
  await userOrdersApi.cancel('42')
  await userOrdersApi.confirmReceipt('42')
  expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
    '/api/v1/orders?page=1&size=10',
    '/api/v1/orders/42',
    '/api/v1/orders/42/cancel',
    '/api/v1/orders/42/confirm-receipt',
  ])
})
