import { afterEach, expect, it, vi } from 'vitest'
import { couponsApi } from './coupons'
afterEach(() => vi.unstubAllGlobals())
it('connects coupon browsing, claims, personal status and admin CRUD to their distinct endpoints', async () => {
  const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ code: 200, data: {} }), { status: 200 })))
  vi.stubGlobal('fetch', fetchMock)
  await couponsApi.available(2, 'summer')
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/coupons?page=2&size=12&keyword=summer')
  await couponsApi.claim('9007199254740993')
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/coupons/9007199254740993/claim')
  expect(fetchMock.mock.calls[1][1].method).toBe('POST')
  await couponsApi.mine(1, 'locked')
  expect(fetchMock.mock.calls[2][0]).toContain('status=locked')
  const input = { name: 'test', amount: 10, minimumAmount: 100, quantity: 20, startTime: '2026-10-28T00:00:00', endTime: '2026-11-28T00:00:00', status: 'active' }
  await couponsApi.save(null, input)
  expect(fetchMock.mock.calls[3][0]).toBe('/api/v1/admin/coupons')
  expect(fetchMock.mock.calls[3][1].method).toBe('POST')
  await couponsApi.save('42', input)
  expect(fetchMock.mock.calls[4][1].method).toBe('PUT')
  expect(JSON.parse(fetchMock.mock.calls[4][1].body)).toEqual({ ...input, startTime: '2026-10-28 00:00:00', endTime: '2026-11-28 00:00:00' })
  await couponsApi.remove('42')
  expect(fetchMock.mock.calls[5][1].method).toBe('DELETE')
})

it('passes the selected goods amount to the server when filtering checkout coupons', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 200, data: { list: [], pages: 0 } })))
  vi.stubGlobal('fetch', fetchMock)
  await couponsApi.mine(2, 'unused', 123.5)
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/user-coupons?page=2&size=12&status=unused&orderAmount=123.50')
})

it('normalizes datetime-local minute precision to the backend date format', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 200, data: {} })))
  vi.stubGlobal('fetch', fetchMock)
  await couponsApi.save(null, { name: 'test', amount: 10, minimumAmount: 100, quantity: 20, startTime: '2026-10-28T08:30', endTime: '2026-11-28T08:30', status: 'active' })
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({ startTime: '2026-10-28 08:30:00', endTime: '2026-11-28 08:30:00' })
})
