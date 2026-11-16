import { afterEach, expect, it, vi } from 'vitest'
import { localizedDailyCsv, statisticsApi } from './statistics'
import { session } from '../platform/session'

afterEach(() => vi.unstubAllGlobals())
function csvResponse(csv: string, status = 200, contentType = 'text/csv;charset=UTF-8') {
  return new Response(csv, { status, headers: { 'Content-Type': contentType } })
}

it('requests overview and trend using the selected inclusive Shanghai dates', async () => {
  const calls: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string) => {
      calls.push(input)
      return new Response(
        JSON.stringify({
          code: 200,
          data: input.includes('/overview')
            ? { startDate: '2026-11-10', current: { receivedAmount: 28.5 } }
            : [{ date: '2026-11-10', metrics: { netReceivedAmount: -3 } }],
        }),
      )
    }),
  )
  const range = { startDate: '2026-11-10', endDate: '2026-11-12' }
  await statisticsApi.overview(range)
  await statisticsApi.trend(range)
  expect(new URL(calls[0], 'http://localhost').pathname).toBe('/api/v1/admin/statistics/overview')
  expect(Object.fromEntries(new URL(calls[0], 'http://localhost').searchParams)).toEqual(range)
  expect(new URL(calls[1], 'http://localhost').pathname).toBe('/api/v1/admin/statistics/trend')
  expect(new URL(calls[1], 'http://localhost').searchParams.get('endDate')).toBe(range.endDate)
})

it('requests a bounded top-five ranking and paginates newest-first daily reports', async () => {
  const calls: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string) => {
      calls.push(input)
      return new Response(
        JSON.stringify({
          code: 200,
          data: input.includes('/reports/daily')
            ? { list: [], total: 8, page: 2, size: 7, pages: 2 }
            : [{ productId: '9007199254740993', quantity: 3, merchandiseAmount: 29.5 }],
        }),
      )
    }),
  )
  const range = { startDate: '2026-06-01', endDate: '2026-06-30' }
  const products = await statisticsApi.products(range)
  const report = await statisticsApi.daily(range, 2, 7)
  expect(products[0].productId).toBe('9007199254740993')
  expect(new URL(calls[0], 'http://localhost').searchParams.get('limit')).toBe('5')
  expect(report.total).toBe(8)
  expect(Object.fromEntries(new URL(calls[1], 'http://localhost').searchParams)).toEqual({
    ...range,
    page: '2',
    size: '7',
    sort: 'date',
    order: 'desc',
  })
})

it('downloads the complete authorized report as CSV without changing the JSON default', async () => {
  vi.stubGlobal('sessionStorage', {
    getItem: () => JSON.stringify({ token: 'statistics-admin', expiresAt: Date.now() + 60_000 }),
    setItem() {},
    removeItem() {},
  })
  const calls: Array<{ url: string; options: RequestInit }> = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, options: RequestInit) => {
      calls.push({ url, options })
      return csvResponse(
        '\uFEFFdate,created_orders,paid_orders,received_amount,refunded_orders,refund_amount,net_received_amount,new_customers\r\n2026-11-12,2,1,10.00,1,12.00,-2.00,3',
      )
    }),
  )
  const csv = await statisticsApi.exportDaily({ startDate: '2026-11-01', endDate: '2026-11-12' })
  expect(calls[0].url).toContain('/reports/daily/export?startDate=2026-11-01&endDate=2026-11-12')
  expect(new Headers(calls[0].options.headers).get('Authorization')).toBe('Bearer statistics-admin')
  expect(calls[0].options.method).toBeUndefined()
  expect(localizedDailyCsv(csv)).toContain(
    '\uFEFF日期,新建订单数,支付订单数,实收金额,退款订单数,退款金额,净收款,新增客户数',
  )
})

it('rejects non-CSV and malformed report responses and labels only a known report header', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(csvResponse('{"code":200}', 200, 'application/json'))
      .mockResolvedValueOnce(csvResponse('bad,header')),
  )
  await expect(
    statisticsApi.exportDaily({ startDate: '2026-11-12', endDate: '2026-11-12' }),
  ).rejects.toThrow('导出数据格式不正确')
  await expect(
    statisticsApi.exportDaily({ startDate: '2026-11-12', endDate: '2026-11-12' }),
  ).resolves.toBe('bad,header')
  expect(() => localizedDailyCsv('bad,header')).toThrow('日报导出格式不正确')
})

it('preserves the exact inclusive date range and permits a single-day report', () => {
  const range = { startDate: '2026-11-12', endDate: '2026-11-12' }
  const csv =
    '\uFEFFdate,created_orders,paid_orders,received_amount,refunded_orders,refund_amount,net_received_amount,new_customers\r\n2026-11-12,1,1,10.00,0,0.00,10.00,1'
  expect(localizedDailyCsv(csv)).toBe(
    '\uFEFF日期,新建订单数,支付订单数,实收金额,退款订单数,退款金额,净收款,新增客户数\r\n2026-11-12,1,1,10.00,0,0.00,10.00,1',
  )
  expect(range.startDate).toBe(range.endDate)
})
