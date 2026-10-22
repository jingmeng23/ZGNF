import { describe, expect, it } from 'vitest'
import { hasAdminAccess } from './access'

const token = (claims: Record<string, unknown>) =>
  `header.${btoa(JSON.stringify(claims)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')}.signature`
const expiresLater = Math.floor(Date.now() / 1000) + 3600

describe('admin navigation access', () => {
  it('accepts an unexpired admin access token using the backend role convention', () => {
    expect(hasAdminAccess(token({ type: 'access', role: 'admin', exp: expiresLater }))).toBe(true)
    expect(hasAdminAccess(token({ type: 'access', role: 'ADMIN', exp: expiresLater }))).toBe(true)
  })
  it('rejects ordinary users, refresh tokens and expired sessions', () => {
    expect(hasAdminAccess(token({ type: 'access', role: 'user', exp: expiresLater }))).toBe(false)
    expect(hasAdminAccess(token({ type: 'refresh', role: 'admin', exp: expiresLater }))).toBe(false)
    expect(hasAdminAccess(token({ type: 'access', role: 'admin', exp: 1 }))).toBe(false)
  })
  it('fails closed on invalid or incomplete payloads', () => {
    for (const candidate of [
      '',
      'invalid',
      token({ role: 'admin' }),
      token({ type: 'access', role: 7, exp: expiresLater }),
    ]) {
      expect(hasAdminAccess(candidate)).toBe(false)
    }
  })
})
