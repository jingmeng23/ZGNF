import { request } from './http'
import type { PageResult, Category } from './types'

export type ProductStatus = 'draft' | 'on_sale' | 'off_sale' | 'sold_out' | 'presale' | 'scheduled'
export interface ProductInput {
  categoryId: string
  brandId: string | null
  brandName?: string | null
  name: string
  subtitle: string | null
  coverUrl: string | null
  price: number
  originalPrice: number | null
  description: string | null
  detailImages: string[]
}
export interface AdminProduct extends ProductInput {
  id: string
  category: string
  status: ProductStatus
  sales: number
  stock: number
  createTime: string
  updateTime: string
}
export interface ProductOptions {
  categories: Category[]
  brands: { id: string; name: string; status: string }[]
}
export interface ProductQuery {
  page: number
  size: number
  keyword: string
  categoryId: string
  status: string
  sort: string
  order: string
}
export const productsApi = {
  list: (query: ProductQuery) => {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(query))
      if (value !== '') params.set(key, String(value))
    return request<PageResult<AdminProduct>>(`/admin/products?${params}`)
  },
  options: () => request<ProductOptions>('/admin/products/options'),
  createCategory: (input: {
    name: string
    parentId: string
    sort: number
    status: 'active' | 'inactive'
  }) => request<Category>('/admin/categories', { method: 'POST', body: JSON.stringify(input) }),
  detail: (id: string) => request<AdminProduct>(`/admin/products/${encodeURIComponent(id)}`),
  save: (id: string | null, input: ProductInput) =>
    request<AdminProduct>(id ? `/admin/products/${encodeURIComponent(id)}` : '/admin/products', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(input),
    }),
  status: (id: string, status: ProductStatus) =>
    request<AdminProduct>(`/admin/products/${encodeURIComponent(id)}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    }),
  remove: (id: string) =>
    request<void>(`/admin/products/${encodeURIComponent(id)}`, { method: 'DELETE' }),
}
