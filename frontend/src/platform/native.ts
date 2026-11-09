import { Capacitor } from '@capacitor/core'
import { App } from '@capacitor/app'
import { Network } from '@capacitor/network'
import { SplashScreen } from '@capacitor/splash-screen'
import { StatusBar, Style } from '@capacitor/status-bar'
import type { Router } from 'vue-router'

/** Web builds never register native listeners or change browser back behaviour. */
export async function initializeNativeApp(router: Router) {
  if (!Capacitor.isNativePlatform()) return
  // Never leave the user stuck on the splash if a native plugin fails.
  const splashFallback = window.setTimeout(() => {
    void SplashScreen.hide()
  }, 5000)
  document.documentElement.classList.add('native-app')
  const roots = new Set(['/', '/catalog', '/services', '/cart', '/account'])
  await App.addListener('backButton', ({ canGoBack }) => {
    // Let an open HTML dialog or a page-specific editor consume the back event first.
    if (!window.dispatchEvent(new Event('app:back', { cancelable: true }))) return
    const dialog = document.querySelector<HTMLDialogElement>('dialog[open]')
    if (dialog) {
      dialog.close()
      return
    }
    if (roots.has(router.currentRoute.value.path)) {
      if (window.confirm('确定退出自贡能发？')) void App.exitApp()
    } else if (canGoBack) router.back()
    else void router.replace('/')
  })
  const reportNetwork = (connected: boolean) => {
    document.documentElement.classList.toggle('app-offline', !connected)
    window.dispatchEvent(new CustomEvent('app:network', { detail: { connected } }))
  }
  await Network.addListener('networkStatusChange', ({ connected }) => reportNetwork(connected))
  reportNetwork((await Network.getStatus()).connected)
  await router.isReady()
  try {
    await StatusBar.setOverlaysWebView({ overlay: false })
    await StatusBar.setStyle({ style: Style.Light })
    await StatusBar.setBackgroundColor({ color: '#f6f8f6' })
  } finally {
    window.clearTimeout(splashFallback)
    await SplashScreen.hide()
  }
}
