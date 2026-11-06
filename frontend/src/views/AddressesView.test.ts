import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createRenderer, h, type Component } from 'vue'
import AddressesView from './AddressesView.vue'
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('../components/AddressMap.vue', () => ({ default: { render: () => null } }))
const mocks = vi.hoisted(() => ({ regions: vi.fn(), list: vi.fn().mockResolvedValue([]) }))
vi.mock('../api/addresses', () => ({ addressesApi: mocks }))
const fixtures: Record<string, unknown[]> = {
  '': [{ code: '510000', name: '四川省' }],
  '510000': [{ code: '510300', name: '自贡市' }],
  '510300': [{ code: '510303', name: '贡井区' }],
  '510303': [{ code: '510303002', name: '贡井街道' }],
}
function mount() {
  let state: any
  const component = { ...AddressesView, render(this: any) { state = this.$.setupState; return h('div') } } as unknown as Component
  const renderer = createRenderer<any, any>({ createElement: () => ({}), createText: () => ({}), createComment: () => ({}), insert: () => {}, remove: () => {}, setText: () => {}, setElementText: () => {}, parentNode: () => null, nextSibling: () => null, patchProp: () => {} })
  const app = renderer.createApp(component)
  app.provide(Symbol.for('v-scx'), { modules: new Set() }); app.mount({})
  return { get state() { return state }, unmount: () => app.unmount() }
}
const flush = async () => { for (let i = 0; i < 100; i++) await Promise.resolve() }
beforeEach(() => { vi.clearAllMocks(); mocks.regions.mockImplementation(async (parent?: string) => fixtures[parent ?? ''] ?? []) })
afterEach(() => vi.unstubAllGlobals())
it('fills receiver, phone, detail and the four-level path from a text paste', async () => {
  const view = mount(); await flush()
  const preventDefault = vi.fn()
  view.state.paste({ preventDefault, clipboardData: { items: [], getData: () => '收件人：张三\n联系电话：+86 138 0013 8000\n收货地址：四川省自贡市贡井区贡井街道广场路12号' } })
  await flush()
  expect(preventDefault).toHaveBeenCalledOnce()
  expect(view.state.form).toMatchObject({ receiver: '张三', phone: '13800138000', districtCode: '510303', detail: '贡井街道广场路12号' })
  expect(view.state.path).toEqual(['510000', '510300', '510303', '510303002'])
  expect(view.state.notice).toContain('已识别并填入')
  view.unmount()
})
it('preserves existing form data on uncertain text and reports image recognition separately', async () => {
  const view = mount(); await flush()
  view.state.form.receiver = '李四'; view.state.form.detail = '原来的详细地址'
  await view.state.recognize('不完整的收货信息')
  expect(view.state.form.receiver).toBe('李四'); expect(view.state.form.detail).toBe('原来的详细地址')
  expect(view.state.notice).toContain('已填内容未修改')
  view.state.paste({ preventDefault: vi.fn(), clipboardData: { items: [{ type: 'image/png', getAsFile: () => ({}) }] } })
  expect(view.state.notice).toBe('图片识别功能开发中')
  view.unmount()
})
