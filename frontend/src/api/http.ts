import type { ApiResult } from './types'
import { session } from '../platform/session'

export class ApiError extends Error {
  constructor(
    message: string,
    public code: number,
    public traceId?: string,
  ) {
    super(message)
  }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 15000)
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body) headers.set('Content-Type', 'application/json')
  const current = session.read()
  if (current) headers.set('Authorization', `Bearer ${current.token}`)
  try {
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || '/api/v1'}${path}`, {
      ...options,
      headers,
      signal: controller.signal,
    })
    let body: Partial<ApiResult<T>> | undefined
    try {
      body = (await response.json()) as Partial<ApiResult<T>>
    } catch {
      /* Handle gateway HTML and empty responses below. */
    }
    if (response.status === 401) {
      const isAuthEndpoint = /^\/auth\//.test(path) || /^\/users\/(register|login\/password)$/.test(path)
      if (!isAuthEndpoint && current) {
        session.clear()
        window.dispatchEvent(new Event('auth:expired'))
        throw new ApiError('登录已过期，请重新登录', 401001, body?.traceId)
      }
      throw new ApiError(body?.message || '手机号或密码错误', body?.code || 401001, body?.traceId)
    }
    if (!response.ok || body?.code !== 200) {
      const fallback =
        response.status === 403
          ? '没有管理员权限，请联系管理员授权'
          : response.status === 404
            ? '该业务接口尚未开放'
            : !body
              ? `服务返回了无效响应（HTTP ${response.status}）`
              : '请求失败，请稍后重试'
      throw new ApiError(body?.message || fallback, body?.code || response.status, body?.traceId)
    }
    return body.data as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(
      controller.signal.aborted ? '请求超时，请重试' : '无法连接服务，请检查网络或稍后重试',
      0,
    )
  } finally {
    clearTimeout(timeout)
  }
}
