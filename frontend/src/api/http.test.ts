import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { request } from './http'
import { session } from '../platform/session'
import { adminApi } from './admin'

beforeEach(() => {
  const values = new Map<string, string>()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  })
  vi.stubGlobal('window', new EventTarget())
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
const response = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })

describe('backend response handling', () => {
  it('sends multipart uploads with authentication and lets fetch set the boundary', async () => {
    session.write('admin-token', 3600)
    const body = new FormData()
    body.append('file', new File(['image'], 'cover.png', { type: 'image/png' }))
    const fetchMock = vi
      .fn()
      .mockResolvedValue(response({ code: 200, data: { url: '/api/v1/media/products/cover.png' } }))
    vi.stubGlobal('fetch', fetchMock)
    await request('/admin/products/images', { method: 'POST', body })
    const options = fetchMock.mock.calls[0][1]
    expect(options.body).toBe(body)
    expect(options.headers.get('Authorization')).toBe('Bearer admin-token')
    expect(options.headers.has('Content-Type')).toBe(false)
  })
  it('sends the access token and keeps Java Long IDs as strings', async () => {
    session.write('access-token', 3600)
    const fetchMock = vi
      .fn()
      .mockResolvedValue(response({ code: 200, data: { id: '9223372036854775806' } }))
    vi.stubGlobal('fetch', fetchMock)
    expect(await request('/users/me')).toEqual({ id: '9223372036854775806' })
    expect(fetchMock.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer access-token')
  })
  it('clears an expired session, signals the router, and preserves the trace ID', async () => {
    session.write('expired-token', 3600)
    const expired = vi.fn()
    window.addEventListener('auth:expired', expired)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(response({ code: 401001, traceId: 'trace-401' }, 401)),
    )
    await expect(request('/admin/orders')).rejects.toMatchObject({
      code: 401001,
      traceId: 'trace-401',
    })
    expect(session.read()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
  })
  it('distinguishes an unavailable endpoint from a network error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>Not found</html>', { status: 404 })),
    )
    await expect(request('/admin/orders')).rejects.toMatchObject({
      code: 404,
      message: '该业务接口尚未开放',
    })
  })
  it('preserves permission failures without clearing the session', async () => {
    session.write('user-token', 3600)
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          response({ code: 403001, message: '无权访问', traceId: 'trace-403' }, 403),
        ),
    )
    await expect(request('/admin/orders')).rejects.toMatchObject({
      code: 403001,
      traceId: 'trace-403',
    })
    expect(session.read()?.token).toBe('user-token')
  })
  it('aborts stalled requests after 15 seconds', async () => {
    vi.useFakeTimers()
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_path, options: RequestInit) =>
          new Promise((_resolve, reject) => {
            options.signal?.addEventListener('abort', () => reject(new Error('aborted')))
          }),
      ),
    )
    const pending = expect(request('/admin/orders')).rejects.toMatchObject({
      message: '请求超时，请重试',
    })
    await vi.advanceTimersByTimeAsync(15000)
    await pending
  })
  it('sends server pagination and rejects an invalid page envelope', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        response({ code: 200, data: { list: [], total: 120, page: 3, size: 20, pages: 6 } }),
      )
    vi.stubGlobal('fetch', fetchMock)
    expect(
      (
        await adminApi.list('/admin/orders', {
          page: 3,
          size: 20,
          keyword: '燃气',
          filter: '待付款',
        })
      ).total,
    ).toBe(120)
    const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      page: '3',
      size: '20',
      keyword: '燃气',
      filter: '待付款',
    })
    fetchMock.mockResolvedValue(response({ code: 200, data: null }))
    await expect(adminApi.list('/admin/orders', { page: 1, size: 20 })).rejects.toThrow(
      '分页数据格式不正确',
    )
  })
})
