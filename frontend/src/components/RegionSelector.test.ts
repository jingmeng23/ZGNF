import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, nextTick, reactive, type Component } from 'vue'
import RegionSelector from './RegionSelector.vue'
import type { Region } from '../api/addresses'
const row = (code: string, name: string, level: number, parentCode: string | null = null): Region => ({ code, name, level, parentCode, active: true, replacementCode: null, version: 'test' })
const data: Record<string, Region[]> = {
  '': [row('510000', '四川省', 1), row('440000', '广东省', 1)],
  '510000': [row('510300', '自贡市', 2, '510000')],
  '510300': [row('510304', '贡井区', 3, '510300')],
  '510304': [row('510304001', '贡井街道', 4, '510304')],
  '440000': [row('440300', '深圳市', 2, '440000')],
}
const flush = async () => { for (let i = 0; i < 80; i++) await Promise.resolve(); await nextTick() }
function mount(values: (string | number)[] = [], loader = vi.fn(async (parent?: string) => data[parent ?? ''] ?? [])) {
  let state: any
  const model = reactive({ value: values })
  const changes = vi.fn()
  const component = { ...RegionSelector, render(this: any) { state = this.$.setupState; return h('div') } } as unknown as Component
  const renderer = createRenderer<any, any>({ createElement: () => ({}), createText: () => ({}), createComment: () => ({}), insert: () => {}, remove: () => {}, setText: () => {}, setElementText: () => {}, parentNode: () => null, nextSibling: () => null, patchProp: () => {} })
  const app = renderer.createApp({ render: () => h(component, { modelValue: model.value, loadRegions: loader, 'onUpdate:modelValue': (v: (string | number)[]) => { model.value = v }, onChange: changes }) })
  app.provide(Symbol.for('v-scx'), { modules: new Set() }); app.mount({})
  return { get state() { return state }, model, loader, changes, unmount: () => app.unmount() }
}
beforeEach(() => vi.stubGlobal('document', { addEventListener: vi.fn(), removeEventListener: vi.fn() }))
afterEach(() => vi.unstubAllGlobals())
describe('four-level region selector', () => {
  it('loads only provinces initially and clears all descendants when switching province', async () => {
    const view = mount(); await flush()
    expect(view.loader).toHaveBeenCalledTimes(1)
    expect(view.state.columns.slice(1)).toEqual([[], [], []])
    await view.state.choose(0, data['']![0]); await flush()
    await view.state.choose(1, data['510000']![0]); await flush()
    await view.state.choose(2, data['510300']![0]); await flush()
    await view.state.choose(3, data['510304']![0]); await flush()
    expect(view.state.label).toBe('四川省/自贡市/贡井区/贡井街道')
    await view.state.choose(0, data['']![1]); await flush()
    expect(view.model.value).toEqual(['440000'])
    expect(view.state.columns[1][0].name).toBe('深圳市')
    expect(view.state.columns.slice(2)).toEqual([[], []])
    view.unmount()
  })
  it('restores numeric v-model values and responds to external reset', async () => {
    const view = mount([510000, 510300, 510304, 510304001]); await flush()
    expect(view.state.chosen.map((r: Region) => r.code)).toEqual(['510000', '510300', '510304', '510304001'])
    expect(view.changes).not.toHaveBeenCalled()
    view.model.value = []; await nextTick(); await flush()
    expect(view.state.label).toBe(''); expect(view.state.columns.slice(1)).toEqual([[], [], []])
    view.unmount()
  })
  it('ignores late city responses from a previously selected province', async () => {
    let resolve!: (rows: Region[]) => void
    const loader = vi.fn((parent?: string): Promise<Region[]> => parent === '510000' ? new Promise(done => { resolve = done }) : Promise.resolve(data[parent ?? ''] ?? []))
    const view = mount([], loader); await flush()
    const first = view.state.choose(0, data['']![0]); await flush()
    await view.state.choose(0, data['']![1]); await flush()
    resolve(data['510000']!); await first; await flush()
    expect(view.state.columns[1][0].name).toBe('深圳市')
    view.unmount()
  })
  it('offers retry after loading fails', async () => {
    const loader = vi.fn(async (parent?: string) => data[parent ?? ''] ?? []).mockRejectedValueOnce(new Error('offline'))
    const view = mount([], loader); await flush()
    expect(view.state.errors[0]).toContain('重试')
    await view.state.loadColumn(0, view.state.revision); await flush()
    expect(view.state.columns[0]).toHaveLength(2)
    expect(view.state.errors[0]).toBe('')
    view.unmount()
  })
})
