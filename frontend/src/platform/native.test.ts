import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { initializeNativeApp } from './native'
import type { Router } from 'vue-router'

const mocks = vi.hoisted(() => ({
  native: false,
  listeners: {} as Record<string, (...args: any[]) => void>,
  exit: vi.fn(),
  hide: vi.fn(),
}))
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => mocks.native } }))
vi.mock('@capacitor/app', () => ({
  App: {
    addListener: vi.fn(async (name, cb) => {
      mocks.listeners[name] = cb
    }),
    exitApp: mocks.exit,
  },
}))
vi.mock('@capacitor/network', () => ({
  Network: {
    addListener: vi.fn(async (name, cb) => {
      mocks.listeners[name] = cb
    }),
    getStatus: async () => ({ connected: false }),
  },
}))
vi.mock('@capacitor/splash-screen', () => ({ SplashScreen: { hide: mocks.hide } }))
vi.mock('@capacitor/status-bar', () => ({
  StatusBar: {
    setOverlaysWebView: vi.fn(),
    setStyle: vi.fn(),
    setBackgroundColor: vi.fn(),
  },
  Style: { Light: 'LIGHT' },
}))
let router: Router
let toggle: ReturnType<typeof vi.fn>
beforeEach(() => {
  vi.clearAllMocks()
  mocks.native = true
  mocks.listeners = {}
  toggle = vi.fn()
  const target = new EventTarget()
  Object.assign(target, { setTimeout, clearTimeout, confirm: vi.fn(() => false) })
  vi.stubGlobal('window', target)
  vi.stubGlobal('document', {
    documentElement: { classList: { add: vi.fn(), toggle } },
    querySelector: () => null,
  })
  router = {
    currentRoute: { value: { path: '/' } },
    back: vi.fn(),
    replace: vi.fn(),
    isReady: async () => {},
  } as unknown as Router
})
afterEach(() => vi.unstubAllGlobals())
describe('Android lifecycle', () => {
  it('leaves browser navigation untouched', async () => {
    mocks.native = false
    await initializeNativeApp(router)
    expect(Object.keys(mocks.listeners)).toEqual([])
    expect(mocks.hide).not.toHaveBeenCalled()
  })
  it('reports offline and recovery, then hides the splash', async () => {
    await initializeNativeApp(router)
    expect(toggle).toHaveBeenCalledWith('app-offline', true)
    mocks.listeners.networkStatusChange({ connected: true })
    expect(toggle).toHaveBeenLastCalledWith('app-offline', false)
    expect(mocks.hide).toHaveBeenCalled()
  })
  it('does not exit on a cancelled confirmation and returns from detail pages', async () => {
    await initializeNativeApp(router)
    mocks.listeners.backButton({ canGoBack: true })
    expect(mocks.exit).not.toHaveBeenCalled()
    router.currentRoute.value.path = '/products/1'
    mocks.listeners.backButton({ canGoBack: true })
    expect(router.back).toHaveBeenCalledOnce()
  })
  it('lets the current page consume back before leaving an editor', async () => {
    await initializeNativeApp(router)
    window.addEventListener('app:back', (event) => event.preventDefault())
    router.currentRoute.value.path = '/addresses'
    mocks.listeners.backButton({ canGoBack: true })
    expect(router.back).not.toHaveBeenCalled()
  })
})
