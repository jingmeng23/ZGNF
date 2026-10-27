// Replace this adapter with native secure storage when distributing the app.
// Refresh tokens are deliberately not persisted until the server implements refresh.
import { loginDisabled } from './testMode'
const KEY = 'zgnf.access-session'
interface Session { token: string; expiresAt: number }
export const session = {
  read(): Session | null {
    if (loginDisabled) return { token: 'temporary-test-session', expiresAt: Number.MAX_SAFE_INTEGER }
    try {
      const value = JSON.parse(sessionStorage.getItem(KEY) || 'null') as Session | null
      if (value && typeof value.token === 'string' && value.expiresAt > Date.now()) return value
    } catch { /* Invalid or unavailable browser storage is treated as signed out. */ }
    return null
  },
  write(token: string, expiresIn: number) {
    sessionStorage.setItem(KEY, JSON.stringify({ token, expiresAt: Date.now() + expiresIn * 1000 }))
  },
  clear() { sessionStorage.removeItem(KEY) },
}
