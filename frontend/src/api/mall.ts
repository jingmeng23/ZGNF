import { request } from './http'
import type { PageResult, Product } from './types'

export interface MallQuery {
  page?: number
  size?: number
  keyword?: string
  categoryId?: string
  brandId?: string
  minPrice?: string
  maxPrice?: string
  inStock?: boolean
  sort?: string
  order?: string
  attributes?: Record<string, string>
}
export interface Sku {
  id: string
  code: string
  name: string
  imageUrl: string | null
  specs: Record<string, string>
  price: number
  originalPrice: number | null
  availableStock: number
  selectable: boolean
  reason: string
}
export interface Matrix {
  dimensions: { name: string; options: { value: string; disabled: boolean }[] }[]
  skus: Sku[]
  selectedSkuId: string | null
}
export interface Detail {
  product: Product
  mainImages: string[]
  description: string | null
  detailImages: string[]
  servicePromises: string[]
  review: { count: number; positiveCount: number; averageRating: number }
  attributes: Record<string, string[]>
  matrix: Matrix
}
export interface SearchResult {
  results: PageResult<Product>
  recommendations: Product[]
  degraded: boolean
  message: string
}
export interface PersonalEntry {
  productId: string
  time: string
  product: Product | null
  unavailable: boolean
}
export interface Floor {
  id: string
  type: string
  title: string
  items: { title: string; imageUrl: string | null; path: string | null; productId: string | null }[]
}
export function mallParams(query: MallQuery): URLSearchParams {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (key === 'attributes') {
      for (const [name, attr] of Object.entries(value as Record<string, string>))
        if (attr) params.set(`attributes[${name}]`, attr)
    } else if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return params
}
export const mallApi = {
  list: (query: MallQuery) => request<PageResult<Product>>(`/mall/products?${mallParams(query)}`),
  search: (query: MallQuery) => request<SearchResult>(`/mall/search?${mallParams(query)}`),
  detail: (id: string) => request<Detail>(`/mall/products/${encodeURIComponent(id)}`),
  matrix: (id: string, selection: Record<string, string>) =>
    request<Matrix>(
      `/mall/products/${encodeURIComponent(id)}/skus?${new URLSearchParams({ selection: JSON.stringify(selection) })}`,
    ),
  brands: () => request<{ id: string; name: string }[]>('/mall/brands'),
  attributes: () => request<Record<string, string[]>>('/mall/attributes'),
  suggest: (keyword: string) =>
    request<string[]>(`/mall/search/suggest?${new URLSearchParams({ keyword })}`),
  hotwords: () => request<string[]>('/mall/search/hotwords'),
  floors: () => request<Floor[]>('/mall/home'),
  isFavorite: (id: string) => request<boolean>(`/mall/me/favorites/${encodeURIComponent(id)}`),
  favorite: (id: string, value: boolean) =>
    request<void>(`/mall/me/favorites/${encodeURIComponent(id)}`, {
      method: value ? 'PUT' : 'DELETE',
    }),
  visit: (id: string) =>
    request<void>(`/mall/me/history/${encodeURIComponent(id)}`, { method: 'POST' }),
  personal: (type: 'favorites' | 'history', page: number) =>
    request<PageResult<PersonalEntry>>(`/mall/me/${type}?page=${page}&size=12`),
  removePersonal: (type: 'favorites' | 'history', id: string) =>
    request<void>(`/mall/me/${type}/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  clearHistory: () => request<void>('/mall/me/history', { method: 'DELETE' }),
}
