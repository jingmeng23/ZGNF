import { afterEach, expect, it, vi } from 'vitest'
import { productsApi, type ProductInput } from './products'

afterEach(() => vi.unstubAllGlobals())
function mockFetch() {
  const fetchMock = vi
    .fn()
    .mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify({ code: 200, data: {} }))),
    )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
const input: ProductInput = {
  categoryId: '9007199254740993',
  brandId: null,
  name: '燃气灶',
  subtitle: null,
  coverUrl: '/images/stove.jpg',
  price: 99.99,
  originalPrice: null,
  description: '说明',
  detailImages: ['https://images.example/detail.jpg'],
}

it('connects product pagination, category, status and sorting filters', async () => {
  const fetchMock = mockFetch()
  await productsApi.list({
    page: 2,
    size: 20,
    keyword: '灶 & 热水',
    categoryId: input.categoryId,
    status: 'off_sale',
    sort: 'price',
    order: 'asc',
  })
  const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost')
  expect(url.pathname).toBe('/api/v1/admin/products')
  expect(Object.fromEntries(url.searchParams)).toEqual({
    page: '2',
    size: '20',
    keyword: '灶 & 热水',
    categoryId: input.categoryId,
    status: 'off_sale',
    sort: 'price',
    order: 'asc',
  })
})
it('omits empty optional filters instead of sending invalid status values', async () => {
  const fetchMock = mockFetch()
  await productsApi.list({
    page: 1,
    size: 10,
    keyword: '',
    categoryId: '',
    status: '',
    sort: 'createTime',
    order: 'desc',
  })
  expect(fetchMock.mock.calls[0][0]).toBe(
    '/api/v1/admin/products?page=1&size=10&sort=createTime&order=desc',
  )
})
it('creates and edits products, preserving Long IDs and nullable fields', async () => {
  const fetchMock = mockFetch()
  await productsApi.save(null, input)
  await productsApi.save('9007199254740995', input)
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/products')
  expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/products/9007199254740995')
  expect(fetchMock.mock.calls[1][1].method).toBe('PUT')
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(input)
})
it('uses the status endpoint for publishing and unpublishing and DELETE for removal', async () => {
  const fetchMock = mockFetch()
  await productsApi.status('42', 'on_sale')
  await productsApi.status('42', 'off_sale')
  await productsApi.remove('42')
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/products/42/status')
  expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ status: 'on_sale' })
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ status: 'off_sale' })
  expect(fetchMock.mock.calls[2][1].method).toBe('DELETE')
})
it('loads editor options and authoritative product detail', async () => {
  const fetchMock = mockFetch()
  await productsApi.options()
  await productsApi.detail('42')
  expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
    '/api/v1/admin/products/options',
    '/api/v1/admin/products/42',
  ])
})

it('creates categories through the category owner endpoint', async () => {
  const fetchMock = mockFetch()
  const input = { name: '灶具', parentId: '0', sort: 0, status: 'active' as const }
  await productsApi.createCategory(input)
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/categories')
  expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(input)
})
