import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createRenderer, nextTick, ssrContextKey } from 'vue'
import AdminAftersales from './AdminAftersales.vue'
import { adminAftersalesApi, type AftersaleRecord } from '../api/aftersales'
import { can } from './permissions'

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ query: {} }),
}))

vi.mock('../api/aftersales', async (original) => ({
  ...(await original<typeof import('../api/aftersales')>()),
  adminAftersalesApi: {
    list: vi.fn(),
    detail: vi.fn(),
    operations: vi.fn(),
    review: vi.fn(),
    complete: vi.fn(),
    wechatRefund: vi.fn(),
  },
}))
vi.mock('./permissions', () => ({ can: vi.fn(() => true) }))

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
const record = (overrides: Partial<AftersaleRecord> = {}): AftersaleRecord => ({
  id: '9007199254740993',
  aftersaleNo: 'AS202611120001',
  orderId: '9007199254740994',
  orderNo: 'GN202611120001',
  amount: 299.5,
  status: 'pending',
  reason: '商品质量问题',
  reviewRemark: null,
  refundReference: null,
  createTime: '2026-11-12T09:00:00',
  updateTime: '2026-11-12T09:00:00',
  reviewedBy: null,
  completionRemark: null,
  completedTime: null,
  ...overrides,
})
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
async function settle() {
  for (let i = 0; i < 12; i++) await Promise.resolve()
  await nextTick()
}
async function pageState() {
  const app = renderer.createApp({ ...AdminAftersales, render: () => null })
  app.provide(ssrContextKey, {})
  apps.push(app)
  app.mount({})
  await settle()
  return (app as any)._instance.setupState
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(can).mockReturnValue(true)
  vi.mocked(adminAftersalesApi.list).mockResolvedValue({
    list: [],
    total: 0,
    page: 1,
    size: 10,
    pages: 0,
  })
  vi.mocked(adminAftersalesApi.detail).mockResolvedValue(record())
  vi.mocked(adminAftersalesApi.operations).mockResolvedValue([])
  vi.stubGlobal('document', { body: { style: { overflow: '' } } })
  vi.stubGlobal('window', { confirm: vi.fn(() => true) })
})
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})

it('applies trimmed filters on search and keeps unsent edits out of pagination requests', async () => {
  const state = await pageState()
  Object.assign(state.filters, { aftersaleNo: ' AS123 ', orderNo: ' GN456 ' })
  vi.mocked(adminAftersalesApi.list).mockResolvedValue({
    list: [],
    total: 30,
    page: 1,
    size: 10,
    pages: 3,
  })
  state.search()
  await settle()
  expect(adminAftersalesApi.list).toHaveBeenLastCalledWith(
    expect.objectContaining({ page: 1, aftersaleNo: 'AS123', orderNo: 'GN456' }),
  )
  state.filters.orderNo = '未提交的条件'
  state.page(1)
  await settle()
  expect(adminAftersalesApi.list).toHaveBeenLastCalledWith(
    expect.objectContaining({ page: 2, orderNo: 'GN456' }),
  )
  state.sortChoice = 'amount-asc'
  state.changeSort()
  await settle()
  expect(adminAftersalesApi.list).toHaveBeenLastCalledWith(
    expect.objectContaining({ page: 1, sort: 'amount', order: 'asc' }),
  )
  state.resetFilters()
  await settle()
  expect(state.filters).toEqual({ aftersaleNo: '', orderNo: '' })
  expect(adminAftersalesApi.list).toHaveBeenLastCalledWith(
    expect.objectContaining({
      page: 1,
      status: '',
      aftersaleNo: '',
      orderNo: '',
      sort: 'createTime',
      order: 'desc',
    }),
  )
})

it('fetches the last valid page when a status change removes the last row on a later page', async () => {
  const state = await pageState()
  state.query.page = 3
  vi.mocked(adminAftersalesApi.list)
    .mockResolvedValueOnce({ list: [], total: 11, pages: 2, page: 3, size: 10 })
    .mockResolvedValueOnce({ list: [record()], total: 11, pages: 2, page: 2, size: 10 })
  await state.load()
  expect(state.query.page).toBe(2)
  expect(state.rows).toHaveLength(1)
  expect(state.rangeStart).toBe(11)
  expect(state.rangeEnd).toBe(11)
})

it('ignores old search responses and clears stale rows after a failed request', async () => {
  const state = await pageState()
  const older = deferred<any>()
  vi.mocked(adminAftersalesApi.list).mockReturnValueOnce(older.promise)
  const first = state.load()
  vi.mocked(adminAftersalesApi.list).mockResolvedValueOnce({
    list: [record({ id: 'latest' })],
    total: 1,
    pages: 1,
    page: 1,
    size: 10,
  })
  await state.load()
  older.resolve({ list: [record({ id: 'old' })], total: 1, pages: 1 })
  await first
  expect(state.rows[0].id).toBe('latest')
  vi.mocked(adminAftersalesApi.list).mockRejectedValueOnce(new Error('网络中断'))
  await state.load()
  expect(state.rows).toEqual([])
  expect(state.listError).toBe('网络中断')
})

it('uses unfiltered totals for overview cards and does not turn failed totals into zero', async () => {
  const state = await pageState()
  Object.assign(state.query, { status: 'rejected', aftersaleNo: 'AS123' })
  vi.mocked(adminAftersalesApi.list).mockImplementation(async (query) => {
    expect(query.aftersaleNo).toBe('')
    expect(query.orderNo).toBe('')
    expect(query.size).toBe(1)
    if (query.status === 'approved') throw new Error('暂时不可用')
    return { list: [], total: 12, pages: 12, page: 1, size: 1 }
  })
  await state.loadSummary()
  expect(state.counts.all).toBe(12)
  expect(state.counts.approved).toBeNull()
  expect(state.summaryError).toBe(true)
})

it('keeps details usable when history fails, preserves a draft on refresh, and resets it for another record', async () => {
  const state = await pageState()
  vi.mocked(adminAftersalesApi.operations).mockRejectedValueOnce(new Error('记录加载失败'))
  await state.open('9007199254740993')
  expect(state.selected.id).toBe('9007199254740993')
  expect(state.historyError).toBe('记录加载失败')
  expect(state.detailError).toBe('')
  state.decision = 'rejected'
  state.reviewRemark = '尚在核实'
  await state.open(state.selected.id)
  expect(state.reviewRemark).toBe('尚在核实')
  state.closeDetail()
  vi.mocked(adminAftersalesApi.detail).mockResolvedValueOnce(record({ id: 'another' }))
  await state.open('another')
  expect(state.decision).toBe('approved')
  expect(state.reviewRemark).toBe('')
})

it('ignores a detail response after closing and restores background scrolling', async () => {
  const state = await pageState()
  const pending = deferred<AftersaleRecord>()
  vi.mocked(adminAftersalesApi.detail).mockReturnValueOnce(pending.promise)
  const opening = state.open('9007199254740993')
  await settle()
  expect(document.body.style.overflow).toBe('hidden')
  state.closeDetail()
  pending.resolve(record())
  await opening
  expect(state.selected).toBeNull()
  expect(state.detailVisible).toBe(false)
  expect(document.body.style.overflow).toBe('')
})

it('requires review reasons, respects cancellation, and blocks writes without permission', async () => {
  const state = await pageState()
  await state.open('9007199254740993')
  state.reviewRemark = '   '
  await state.act('review')
  expect(adminAftersalesApi.review).not.toHaveBeenCalled()
  state.reviewRemark = '符合退款条件'
  vi.mocked(window.confirm).mockReturnValueOnce(false)
  await state.act('review')
  expect(adminAftersalesApi.review).not.toHaveBeenCalled()
  vi.mocked(can).mockReturnValue(false)
  // Re-evaluate the component under the read-only profile.
  const readOnly = await pageState()
  await readOnly.open('9007199254740993')
  readOnly.reviewRemark = '符合退款条件'
  await readOnly.act('review')
  expect(adminAftersalesApi.review).not.toHaveBeenCalled()
})

it('keeps a successful review successful when the subsequent history refresh fails', async () => {
  const state = await pageState()
  await state.open('9007199254740993')
  state.reviewRemark = '  核实后同意退款  '
  vi.mocked(adminAftersalesApi.review).mockResolvedValueOnce(
    record({ status: 'approved', reviewRemark: '核实后同意退款' }),
  )
  vi.mocked(adminAftersalesApi.operations).mockRejectedValueOnce(new Error('历史服务暂时不可用'))
  await state.act('review')
  expect(adminAftersalesApi.review).toHaveBeenCalledWith(
    '9007199254740993',
    'approved',
    '核实后同意退款',
  )
  expect(state.selected.status).toBe('approved')
  expect(state.notice).toContain('审核已通过')
  expect(state.actionError).toBe('')
  expect(state.historyError).toBe('历史服务暂时不可用')
  expect(state.reviewRemark).toBe('')
})

it('validates manual refunds and retains entered values when saving fails', async () => {
  const state = await pageState()
  vi.mocked(adminAftersalesApi.detail).mockResolvedValueOnce(record({ status: 'approved' }))
  await state.open('9007199254740993')
  state.refundMethod = 'manual'
  state.refundReference = ' BANK-123 '
  state.completionRemark = '银行渠道已退'
  await state.act('complete')
  expect(adminAftersalesApi.complete).not.toHaveBeenCalled()
  state.confirmed = true
  vi.mocked(adminAftersalesApi.complete).mockRejectedValueOnce(new Error('流水号未核实'))
  await state.act('complete')
  expect(adminAftersalesApi.complete).toHaveBeenCalledWith(
    '9007199254740993',
    'BANK-123',
    '银行渠道已退',
  )
  expect(state.refundReference).toBe(' BANK-123 ')
  expect(state.confirmed).toBe(true)
  expect(state.actionError).toBe('流水号未核实')
  expect(state.busy).toBe(false)
})

it('uses the WeChat refund endpoint and only shows completion after the server confirms it', async () => {
  const state = await pageState()
  vi.mocked(adminAftersalesApi.detail).mockResolvedValueOnce(record({ status: 'approved' }))
  await state.open('9007199254740993')
  vi.mocked(adminAftersalesApi.wechatRefund).mockRejectedValueOnce(
    new Error('微信退款处理中，请稍后查询'),
  )
  await state.act('wechat')
  expect(state.selected.status).toBe('approved')
  expect(state.notice).toBe('')
  expect(state.actionError).toContain('处理中')
  vi.mocked(adminAftersalesApi.wechatRefund).mockResolvedValueOnce(
    record({ status: 'completed', refundReference: 'WXAS9007199254740993' }),
  )
  await state.act('wechat')
  expect(state.selected.status).toBe('completed')
  expect(state.notice).toContain('微信已确认退款成功')
  expect(adminAftersalesApi.complete).not.toHaveBeenCalled()
  expect(window.confirm).toHaveBeenLastCalledWith(expect.stringContaining('¥299.50'))
})
