import { afterEach, expect, it, vi } from 'vitest'
import { adminAftersalesApi, userAftersalesApi, aftersaleLabel } from './aftersales'

afterEach(() => vi.unstubAllGlobals())
it('distinguishes legacy user cancellations from administrator rejections', () => {
  expect(
    aftersaleLabel({ status: 'rejected', reviewRemark: '用户撤销申请', reviewedBy: null }),
  ).toBe('用户已撤销')
  expect(
    aftersaleLabel({ status: 'rejected', reviewRemark: '用户撤销申请', reviewedBy: '7' }),
  ).toBe('审核未通过')
  expect(aftersaleLabel({ status: 'approved', reviewRemark: '通过', reviewedBy: '7' })).toBe(
    '待退款登记',
  )
})
function mockFetch() {
  const fetchMock = vi
    .fn()
    .mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify({ code: 200, data: {} }))),
    )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
it('connects admin filters and encodes order and aftersale numbers', async () => {
  const fetchMock = mockFetch()
  await adminAftersalesApi.list({
    page: 2,
    size: 10,
    status: 'approved',
    aftersaleNo: 'AS-1&2',
    orderNo: 'ORDER+123',
  })
  const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost')
  expect(url.pathname).toBe('/api/v1/admin/aftersales')
  expect(Object.fromEntries(url.searchParams)).toEqual({
    page: '2',
    size: '10',
    status: 'approved',
    aftersaleNo: 'AS-1&2',
    orderNo: 'ORDER+123',
  })
})
it('loads own applications without sending empty status filters', async () => {
  const fetchMock = mockFetch()
  await userAftersalesApi.list({ page: 1, size: 20, status: '', aftersaleNo: '', orderNo: '' })
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/aftersales?page=1&size=20')
})
it('preserves Long order IDs and the application idempotency key, never specifying a refund amount', async () => {
  const fetchMock = mockFetch()
  const input = { orderId: '9007199254740993', reason: '质量问题', requestId: 'apply-123' }
  await userAftersalesApi.create(input)
  await userAftersalesApi.create(input)
  for (const call of fetchMock.mock.calls) {
    expect(call[0]).toBe('/api/v1/aftersales')
    expect(call[1].method).toBe('POST')
    expect(JSON.parse(call[1].body)).toEqual(input)
  }
})
it('sends review decisions, remarks and external refund references to the matching endpoints', async () => {
  const fetchMock = mockFetch()
  await adminAftersalesApi.review('9007199254740993', 'rejected', '请核实订单信息')
  await adminAftersalesApi.complete('9007199254740993', 'WX-REF-123', '渠道已退款')
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/aftersales/9007199254740993/review')
  expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
    status: 'rejected',
    remark: '请核实订单信息',
  })
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/aftersales/9007199254740993/complete')
  expect(fetchMock.mock.calls[1][1].method).toBe('PUT')
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
    refundReference: 'WX-REF-123',
    remark: '渠道已退款',
  })
})
it('separates customer and administrative detail/history and exposes customer cancellation', async () => {
  const fetchMock = mockFetch()
  await adminAftersalesApi.detail('123')
  await adminAftersalesApi.operations('123')
  await userAftersalesApi.detail('123')
  await userAftersalesApi.operations('123')
  await userAftersalesApi.cancel('123')
  expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
    '/api/v1/admin/aftersales/123',
    '/api/v1/admin/aftersales/123/operations',
    '/api/v1/aftersales/123',
    '/api/v1/aftersales/123/operations',
    '/api/v1/aftersales/123/cancel',
  ])
  expect(fetchMock.mock.calls[4][1].method).toBe('PUT')
})
