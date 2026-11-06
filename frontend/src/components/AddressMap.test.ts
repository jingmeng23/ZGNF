import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, type Component } from 'vue'
import AddressMap from './AddressMap.vue'
const mocks = vi.hoisted(() => ({ load: vi.fn(), resize: vi.fn(), destroy: vi.fn(), markerPosition: vi.fn(), move: vi.fn(), handlers: {} as Record<string, (event: any) => void>, mapOptions: {} as any }))
vi.mock('@amap/amap-jsapi-loader', () => ({ default: { load: mocks.load } }))
function mount(props: Record<string, unknown> = {}) {
  const renderer = createRenderer<any, any>({
    createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
    insert: () => {}, remove: () => {}, setText: () => {}, setElementText: () => {},
    parentNode: () => null, nextSibling: () => null, patchProp: () => {},
  })
  // Node-mode SFCs have an SSR template; supply a minimal host for the SDK container.
  const component = { ...AddressMap, render(this: any) { return h('div', { ref: (element: any) => { this.$.setupState.container = element } }) } } as unknown as Component
  const app = renderer.createApp({ render: () => h(component, { ...props, ref: (value: any) => { exposed = value } }) })
  app.provide(Symbol.for('v-scx'), { modules: new Set() })
  let exposed: any
  app.mount({})
  return { get exposed() { return exposed }, unmount: () => app.unmount() }
}
beforeEach(() => {
  vi.stubGlobal('window', {})
  vi.stubEnv('VITE_AMAP_KEY', 'test-key')
  mocks.handlers = {}; vi.clearAllMocks()
  mocks.load.mockResolvedValue({
    Map: class {
      constructor(_container: unknown, options: unknown) { mocks.mapOptions = options }
      on(event: string, cb: (event: any) => void) { mocks.handlers[event] = cb }
      resize = mocks.resize; destroy = mocks.destroy; setZoomAndCenter = mocks.move
    },
    Marker: class {
      on(event: string, cb: (event: any) => void) { mocks.handlers[event] = cb }
      setPosition = mocks.markerPosition
    },
    Geocoder: class {
      getAddress(_point: unknown, callback: Function) {
        callback('complete', { regeocode: { formattedAddress: '北京市朝阳区望京街道1号', addressComponent: { province: '北京市', city: [], district: '朝阳区', adcode: '110105' } } })
      }
      getLocation(_city: string, callback: Function) { callback('complete', { geocodes: [{ location: [116.4, 39.9] }] }) }
    },
  })
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })
describe('address map lifecycle and selection', () => {
  it('waits for dialog opened initialization, retains edited coordinates, resizes and destroys', async () => {
    const wrapper = mount({ latitude: 29.35, longitude: 104.78 })
    expect(mocks.load).not.toHaveBeenCalled()
    await wrapper.exposed.initialize()
    expect(mocks.mapOptions.center).toEqual([104.78, 29.35])
    expect(mocks.resize).toHaveBeenCalledOnce()
    wrapper.exposed.destroy()
    expect(mocks.destroy).toHaveBeenCalledOnce()
    wrapper.unmount()
    expect(mocks.destroy).toHaveBeenCalledOnce()
  })
  it('reverse geocodes map clicks and marker drags, with municipality fallback', async () => {
    const select = vi.fn(), wrapper = mount({ onSelect: select })
    await wrapper.exposed.initialize()
    const lnglat = { getLng: () => 116.4, getLat: () => 39.9 }
    mocks.handlers.click!({ lnglat }); mocks.handlers.dragend!({ lnglat })
    expect(select).toHaveBeenCalledTimes(2)
    expect(select).toHaveBeenLastCalledWith(expect.objectContaining({ city: '朝阳区', adcode: '110105', detail: '北京市朝阳区望京街道1号', latitude: 39.9, longitude: 116.4 }))
    wrapper.exposed.flyTo('北京市')
    expect(mocks.move).toHaveBeenCalledWith(12, [116.4, 39.9], false, 600)
    wrapper.unmount()
  })
  it('handles SDK rejection and ignores SDK completion after close', async () => {
    mocks.load.mockRejectedValueOnce(new Error('invalid key'))
    const first = mount(); await expect(first.exposed.initialize()).resolves.toBeUndefined(); first.unmount()
    let complete!: (value: any) => void
    mocks.load.mockReturnValueOnce(new Promise(resolve => { complete = resolve }))
    const second = mount(), pending = second.exposed.initialize()
    second.exposed.destroy(); complete({})
    await pending
    expect(mocks.resize).not.toHaveBeenCalled()
    second.unmount()
  })
})
