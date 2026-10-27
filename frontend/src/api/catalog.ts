import { request } from './http'
import type { Category, PageResult, Product } from './types'
export interface CatalogQuery { page?: number; size?: number; keyword?: string; categoryId?: string; sort?: string; order?: string }

export const demoMode = import.meta.env.VITE_DEMO_MODE === 'true'

const demoProducts: Product[] = [
  { id: 'demo-1', name: '家用智能燃气灶', subtitle: '5.2kW 大火力 · 熄火保护', coverUrl: null, price: 1299, originalPrice: 1599, sales: 268 },
  { id: 'demo-2', name: '恒温燃气热水器', subtitle: '16L 零冷水 · 四季恒温', coverUrl: null, price: 2399, originalPrice: 2899, sales: 186 },
  { id: 'demo-3', name: '智能燃气报警器', subtitle: '实时监测 · 声光报警', coverUrl: null, price: 199, originalPrice: 259, sales: 520 },
  { id: 'demo-4', name: '燃气专用安全软管', subtitle: '不锈钢波纹 · 安全耐用', coverUrl: null, price: 89, originalPrice: 129, sales: 832 },
]

export const catalogApi = {
  async list(query: CatalogQuery = {}): Promise<PageResult<Product>> {
    const page = query.page || 1, size = query.size || 12
    if (demoMode) {
      const rows = demoProducts.filter(p => (!query.keyword || p.name.includes(query.keyword)) && (!query.categoryId || (query.categoryId === 'accessories' ? ['demo-3', 'demo-4'].includes(p.id) : p.id === query.categoryId)))
      if (query.sort === 'price' || query.sort === 'sales') rows.sort((a, b) => (Number(a[query.sort as 'price' | 'sales']) - Number(b[query.sort as 'price' | 'sales'])) * (query.order === 'asc' ? 1 : -1))
      return { list: rows.slice((page - 1) * size, page * size), total: rows.length, page, size, pages: Math.ceil(rows.length / size) }
    }
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    for (const key of ['keyword', 'categoryId', 'sort', 'order'] as const) if (query[key]) params.set(key, query[key]!)
    return request<PageResult<Product>>(`/mall/products?${params}`)
  },
  async categories(): Promise<Category[]> {
    if (demoMode) return [{ id: 'demo-1', parentId: '0', name: '燃气灶', sort: 0, status: 'active' }, { id: 'demo-2', parentId: '0', name: '热水器', sort: 1, status: 'active' }, { id: 'accessories', parentId: '0', name: '安全配件', sort: 2, status: 'active' }]
    return request<Category[]>('/categories')
  },
  async detail(id: string): Promise<Product> {
    if (!demoMode) return request<Product>(`/products/${encodeURIComponent(id)}`)
    const product = demoProducts.find(p => p.id === id)
    if (!product) throw new Error('商品不存在或已下架')
    return { ...product, availableStock: 99, description: product.subtitle, detailImages: [] }
  },
}
