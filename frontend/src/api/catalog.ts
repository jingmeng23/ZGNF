import { request } from './http'
import type { PageResult, Product } from './types'

export const demoMode = import.meta.env.VITE_DEMO_MODE === 'true'

const demoProducts: Product[] = [
  { id: 'demo-1', name: '家用智能燃气灶', subtitle: '5.2kW 大火力 · 熄火保护', coverUrl: null, price: 1299, originalPrice: 1599, sales: 268 },
  { id: 'demo-2', name: '恒温燃气热水器', subtitle: '16L 零冷水 · 四季恒温', coverUrl: null, price: 2399, originalPrice: 2899, sales: 186 },
  { id: 'demo-3', name: '智能燃气报警器', subtitle: '实时监测 · 声光报警', coverUrl: null, price: 199, originalPrice: 259, sales: 520 },
  { id: 'demo-4', name: '燃气专用安全软管', subtitle: '不锈钢波纹 · 安全耐用', coverUrl: null, price: 89, originalPrice: 129, sales: 832 },
]

export const catalogApi = {
  async list(): Promise<Product[]> {
    if (demoMode) return demoProducts.map(product => ({ ...product }))
    return (await request<PageResult<Product>>('/products?page=1&size=100')).list
  },
}
