import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createRenderer, nextTick, ssrContextKey } from 'vue'
import AdminOverview from './AdminOverview.vue'
import {
  localizedDailyCsv,
  statisticsApi,
  type BusinessDaily,
  type BusinessMetrics,
  type BusinessOverview,
} from '../api/statistics'
import { can, canVisit } from './permissions'

vi.mock('../api/statistics', () => ({
  localizedDailyCsv: vi.fn((value: string) => `translated: ${value}`),
  statisticsApi: {
    overview: vi.fn(),
    trend: vi.fn(),
    products: vi.fn(),
    daily: vi.fn(),
    exportDaily: vi.fn(),
  },
}))
vi.mock('./permissions', () => ({ can: vi.fn(() => true), canVisit: vi.fn(() => true) }))

const renderer = createRenderer<any, any>({
  createElement: () => ({}),
  createText: () => ({}),
  createComment: () => ({}),
  insert() {},
  remove() {},
  setText() {},
  setElementText() {},
  patchProp() {},
  parentNode: () => null,
  nextSibling: () => null,
})
const apps: ReturnType<typeof renderer.createApp>[] = []
const metrics = (overrides: Partial<BusinessMetrics> = {}): BusinessMetrics => ({
  createdOrders: 12,
  paidOrders: 9,
  receivedAmount: 900,
  refundedOrders: 2,
  refundAmount: 250,
  netReceivedAmount: 650,
  newCustomers: 3,
  ...overrides,
})
const report = (days = 3): BusinessDaily[] =>
  Array.from({ length: days }, (_, index) => ({
    date: `2026-11-${String(10 + index).padStart(2, '0')}`,
    metrics: metrics({
      createdOrders: index + 1,
      receivedAmount: (index + 1) * 300,
      refundAmount: index ? 150 : 0,
      netReceivedAmount: index ? index * 150 : 300,
    }),
  }))
const overview = (overrides: Partial<BusinessOverview> = {}): BusinessOverview => ({
  startDate: '2026-11-10',
  endDate: '2026-11-12',
  previousStartDate: '2026-11-07',
  previousEndDate: '2026-11-09',
  current: metrics(),
  previous: metrics({
    createdOrders: 8,
    paidOrders: 6,
    receivedAmount: 600,
    refundedOrders: 0,
    refundAmount: 0,
    netReceivedAmount: 600,
    newCustomers: 2,
  }),
  receivedGrowthPercent: 50,
  orderGrowthPercent: 50,
  pendingWorkOrders: 4,
  overdueWorkOrders: 2,
  orderStatuses: {
    pending_payment: 2,
    paid: 3,
    processing: 1,
    completed: 5,
    cancelled: 1,
    refunded: 0,
  },
  recentOrders: [
    {
      id: '9007199254740993',
      orderNo: 'GN-123',
      status: 'paid',
      payAmount: 99.5,
      createTime: '2026-11-12T12:30:00',
    },
  ],
  todos: { paidOrders: 3, pendingAftersales: 2, pendingRefunds: 1 },
  ...overrides,
})
async function settle() {
  for (let i = 0; i < 15; i++) await Promise.resolve()
  await nextTick()
}
async function pageState() {
  const app = renderer.createApp({ ...AdminOverview, render: () => null })
  app.provide(ssrContextKey, {})
  apps.push(app)
  app.mount({})
  await settle()
  return (app as any)._instance.setupState
}
beforeEach(() => {
  vi.useRealTimers()
  vi.resetAllMocks()
  vi.mocked(can).mockReturnValue(true)
  vi.mocked(canVisit).mockReturnValue(true)
  vi.stubGlobal('document', { createElement: () => ({ click() {}, href: '', download: '' }) })
  vi.stubGlobal('URL', {
    createObjectURL: vi.fn(() => 'blob:overview-test'),
    revokeObjectURL: vi.fn(),
  })
  vi.stubGlobal(
    'Blob',
    class {
      constructor(
        public parts: unknown[],
        public options: unknown,
      ) {}
    },
  )
  vi.stubGlobal('setTimeout', vi.fn())
  vi.mocked(statisticsApi.overview).mockResolvedValue(overview())
  vi.mocked(statisticsApi.trend).mockResolvedValue(report())
  vi.mocked(statisticsApi.products).mockResolvedValue([
    {
      productId: '9007199254740994',
      productName: '燃气热水器',
      paidOrders: 5,
      quantity: 8,
      merchandiseAmount: 2400,
    },
  ])
  vi.mocked(statisticsApi.daily).mockResolvedValue({
    list: report(),
    total: 3,
    page: 1,
    size: 7,
    pages: 1,
  })
  vi.mocked(statisticsApi.exportDaily).mockResolvedValue('date,created_orders')
  vi.mocked(localizedDailyCsv).mockImplementation((value) => `translated: ${value}`)
})
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

it('loads all full-range overview, trend, product and paged-daily endpoints on mount', async () => {
  await pageState()
  const requests = [
    statisticsApi.overview,
    statisticsApi.trend,
    statisticsApi.products,
    statisticsApi.daily,
  ]
  requests.forEach((api) => expect(api).toHaveBeenCalledOnce())
  for (const api of requests.slice(0, 3))
    expect(api).toHaveBeenCalledWith(
      expect.objectContaining({ startDate: expect.any(String), endDate: expect.any(String) }),
    )
  expect(statisticsApi.daily).toHaveBeenCalledWith(
    expect.objectContaining({ startDate: expect.any(String), endDate: expect.any(String) }),
    1,
    7,
  )
})

it('validates a custom inclusive date range before calling each statistics endpoint', async () => {
  const state = await pageState()
  state.dates.startDate = '2026-11-12'
  state.dates.endDate = '2026-11-10'
  state.applyRange()
  expect(state.dateError).toContain('晚于')
  expect(statisticsApi.overview).toHaveBeenCalledTimes(1)
  state.dates.startDate = '2026-11-01'
  state.dates.endDate = '2026-11-14'
  state.applyRange()
  await settle()
  expect(state.range).toEqual({ startDate: '2026-11-01', endDate: '2026-11-14' })
  for (const api of [statisticsApi.overview, statisticsApi.trend, statisticsApi.products])
    expect(api).toHaveBeenLastCalledWith({ startDate: '2026-11-01', endDate: '2026-11-14' })
  expect(statisticsApi.daily).toHaveBeenLastCalledWith(
    { startDate: '2026-11-01', endDate: '2026-11-14' },
    1,
    7,
  )
})

it('keeps date edits separate from applied reports and can discard edits without another request', async () => {
  const state = await pageState()
  const applied = { ...state.range }
  expect(state.datesDirty).toBe(false)
  state.dates.startDate = '2026-10-01'
  state.customDates()
  expect(state.datesDirty).toBe(true)
  expect(state.preset).toBe('custom')
  expect(state.range).toEqual(applied)
  expect(statisticsApi.overview).toHaveBeenCalledOnce()
  state.resetDates()
  expect(state.dates).toEqual(applied)
  expect(state.datesDirty).toBe(false)
  expect(state.dateError).toBe('')
  expect(state.preset).toBe('7')
  for (const api of [statisticsApi.overview, statisticsApi.trend, statisticsApi.daily])
    expect(api).toHaveBeenCalledOnce()
})

it('recognizes a manually entered preset and resets daily pagination only after valid submission', async () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-11-12T04:00:00Z'))
  const state = await pageState()
  state.dailyPage = 3
  state.dates.startDate = '2026-11-12'
  state.dates.endDate = '2026-11-10'
  state.customDates()
  state.applyRange()
  expect(state.dailyPage).toBe(3)
  expect(state.datesDirty).toBe(true)
  state.dates.endDate = '2026-11-12'
  state.applyRange()
  await settle()
  expect(state.datesDirty).toBe(false)
  expect(state.dateError).toBe('')
  expect(state.preset).toBe('today')
  expect(state.dailyPage).toBe(1)
  expect(statisticsApi.daily).toHaveBeenLastCalledWith(
    { startDate: '2026-11-12', endDate: '2026-11-12' },
    1,
    7,
  )
})

it('switches the trend total and units from the loaded daily series without refetching', async () => {
  const state = await pageState()
  expect(state.trendTotal).toBe(1800)
  expect(state.activeMetric.currency).toBe(true)
  state.metric = 'createdOrders'
  expect(state.trendTotal).toBe(6)
  expect(state.activeMetric.currency).toBe(false)
  state.metric = 'netReceivedAmount'
  expect(state.trendTotal).toBe(750)
  expect(state.activeMetric.currency).toBe(true)
  expect(statisticsApi.trend).toHaveBeenCalledOnce()
})

it('keeps comparison nulls distinct from zero and clears stale KPI and trend data on request failure', async () => {
  vi.mocked(statisticsApi.overview).mockResolvedValueOnce(
    overview({
      receivedGrowthPercent: null,
      orderGrowthPercent: -40,
      current: metrics({ netReceivedAmount: -35 }),
    }),
  )
  const state = await pageState()
  expect(state.growthText(null)).toContain('基数为 0')
  expect(state.stats[1].growth).toBe(-40)
  vi.mocked(statisticsApi.overview).mockRejectedValueOnce(new Error('统计接口不可用'))
  vi.mocked(statisticsApi.trend).mockRejectedValueOnce(new Error('趋势加载失败'))
  await state.load()
  expect(state.overview).toBeNull()
  expect(state.trend).toEqual([])
  expect(state.overviewError).toBe('统计接口不可用')
  expect(state.trendError).toBe('趋势加载失败')
  expect(state.loading).toBe(false)
})

it('uses exact counts, permissions and the selected date/status for actionable work queues', async () => {
  const state = await pageState()
  expect(state.tasks.map((task: { value: number }) => task.value)).toEqual([3, 2, 1, 4])
  expect(state.orderTarget('GN-123', 'paid')).toEqual({
    path: '/admin/orders',
    query: {
      startTime: `${state.range.startDate}T00:00:00`,
      endTime: `${state.range.endDate}T23:59:59`,
      orderNo: 'GN-123',
      status: 'paid',
    },
  })
  expect(state.orderTarget(undefined, 'refunded').query.status).toBe('refunded')
  vi.mocked(canVisit).mockImplementation((path) => path === '/admin/aftersales')
  await state.load()
  expect(state.tasks.map((task: { allowed: boolean }) => task.allowed)).toEqual([
    false,
    true,
    true,
    true,
  ])
})

it('keeps ranking, recent orders, and daily tables independent when one optional endpoint fails', async () => {
  vi.mocked(statisticsApi.products).mockRejectedValueOnce(new Error('排行服务不可用'))
  vi.mocked(statisticsApi.daily).mockRejectedValueOnce(new Error('日报服务不可用'))
  const state = await pageState()
  expect(state.overview.current.receivedAmount).toBe(900)
  expect(state.trend).toHaveLength(3)
  expect(state.products).toEqual([])
  expect(state.productError).toBe('排行服务不可用')
  expect(state.dailyError).toBe('日报服务不可用')
  expect(state.dailyRows).toEqual([])
})

it('rejects an export without permission and exports the complete current date range', async () => {
  vi.mocked(can).mockImplementation((permission) => permission === 'statistics:read')
  const readOnlyState = await pageState()
  await readOnlyState.exportDaily()
  expect(statisticsApi.exportDaily).not.toHaveBeenCalled()
  vi.mocked(can).mockReturnValue(true)
  const state = await pageState()
  state.dates.startDate = '2026-11-01'
  state.dates.endDate = '2026-11-14'
  state.applyRange()
  await settle()
  await state.exportDaily()
  expect(statisticsApi.exportDaily).toHaveBeenCalledOnce()
  expect(statisticsApi.exportDaily).toHaveBeenCalledWith({
    startDate: '2026-11-01',
    endDate: '2026-11-14',
  })
  expect(state.notice).toContain('全部经营日报')
  expect(state.exporting).toBe(false)
})

it('preserves failed export errors and refuses a CSV that does not match the authorized report', async () => {
  const state = await pageState()
  vi.mocked(statisticsApi.exportDaily).mockRejectedValueOnce(new Error('无导出权限'))
  await state.exportDaily()
  expect(state.exportError).toBe('无导出权限')
  vi.mocked(statisticsApi.exportDaily).mockResolvedValueOnce('invalid-csv')
  const { localizedDailyCsv } = await import('../api/statistics')
  vi.mocked(localizedDailyCsv).mockImplementationOnce(() => {
    throw new Error('日报导出格式不正确，请重试')
  })
  await state.exportDaily()
  expect(state.exportError).toContain('格式不正确')
  expect(state.notice).toBe('')
  expect(state.exporting).toBe(false)
})

it('keeps reports in the real Shanghai current year rather than a hard-coded demo month', async () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-11-11T16:15:00.000Z'))
  try {
    const state = await pageState()
    expect(statisticsApi.overview).toHaveBeenCalledWith({
      startDate: '2026-11-06',
      endDate: '2026-11-12',
    })
    expect(state.range.endDate).toBe('2026-11-12')
  } finally {
    vi.useRealTimers()
  }
})
