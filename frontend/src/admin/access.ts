import { session } from '../platform/session'

// Only a navigation hint. The server must verify the signature and ADMIN role.
export function hasAdminAccess(token = session.read()?.token): boolean {
  if (!token) return false
  try {
    const payload = token.split('.')[1].replaceAll('-', '+').replaceAll('_', '/')
    const claims = JSON.parse(atob(payload)) as { type?: string; role?: string; exp?: number }
    return (
      claims.type === 'access' &&
      claims.role?.toLowerCase() === 'admin' &&
      typeof claims.exp === 'number' &&
      claims.exp * 1000 > Date.now()
    )
  } catch {
    return false
  }
}
