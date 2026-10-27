import { afterEach, describe, expect, it, vi } from 'vitest'
import { catalogApi } from './catalog'

afterEach(() => vi.unstubAllGlobals())

describe('catalog API', () => {
  it('sends catalog search, category, pagination and sort to the server', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 200,
      data: { list: [], total: 0, page: 2, size: 12, pages: 0 },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await catalogApi.list({ page: 2, size: 12, keyword: '燃气灶', categoryId: '42', sort: 'price', order: 'asc' })
    const url = new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
    expect(url.pathname).toBe('/api/v1/products')
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: '2', size: '12', keyword: '燃气灶', categoryId: '42', sort: 'price', order: 'asc' })
  })

  it('uses the public detail and real category endpoints', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 200, data: { id: '42', description: 'Details', detailImages: [] } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 200, data: [{ id: '7', parentId: '0', name: '厨房电器', sort: 1, status: 'active' }] }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    expect((await catalogApi.detail('42')).description).toBe('Details')
    expect((await catalogApi.categories())[0].id).toBe('7')
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/products/42')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/categories')
  })
})
