import { ApiError, request } from './http'
import type { PageResult } from './types'
import type { AdminRow } from '../admin/adminData'

export const adminApi = {
  list: async (
    endpoint: string,
    params: { page: number; size: number; keyword?: string; filter?: string },
  ) => {
    const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
    if (params.keyword) query.set('keyword', params.keyword)
    if (params.filter) query.set('filter', params.filter)
    const result = await request<PageResult<AdminRow>>(`${endpoint}?${query.toString()}`)
    if (!result || !Array.isArray(result.list) || typeof result.total !== 'number') {
      throw new ApiError('分页数据格式不正确，请检查接口返回值', 0)
    }
    return result
  },
}

export const adminDemoMode = import.meta.env.VITE_DEMO_MODE === 'true'
