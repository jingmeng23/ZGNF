import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.zgnf.shop',
  appName: '自贡能发',
  webDir: 'dist',
  android: { backgroundColor: '#f6f8f6' },
  plugins: {
    CapacitorHttp: { enabled: true },
    SplashScreen: { launchAutoHide: false, backgroundColor: '#173e35', showSpinner: false },
    StatusBar: { backgroundColor: '#f6f8f6', style: 'LIGHT', overlaysWebView: false },
    Keyboard: { resizeOnFullScreen: true },
  },
}
export default config
