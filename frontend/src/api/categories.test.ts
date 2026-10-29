import { afterEach, expect, it, vi } from 'vitest'
import { categoriesApi, categoryRows, descendants, type AdminCategory } from './categories'

afterEach(() => vi.unstubAllGlobals())
const input = { name: '灶具', parentId: '9007199254740993', sort: 10, status: 'active' as const }
function mockFetch() {
  const fetchMock = vi
    .fn()
    .mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify({ code: 200, data: {} }))),
    )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
it('connects category overview, detail, create and edit with precise Long IDs', async () => {
  const fetchMock = mockFetch()
  await categoriesApi.list()
  await categoriesApi.detail(input.parentId)
  await categoriesApi.save(null, input)
  await categoriesApi.save('9007199254740995', input)
  expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
    '/api/v1/admin/categories/management',
    '/api/v1/admin/categories/9007199254740993',
    '/api/v1/admin/categories',
    '/api/v1/admin/categories/9007199254740995',
  ])
  expect(fetchMock.mock.calls[2][1].method).toBe('POST')
  expect(fetchMock.mock.calls[3][1].method).toBe('PUT')
  expect(JSON.parse(fetchMock.mock.calls[3][1].body)).toEqual(input)
})
it('changes status independently and deletes through the owner API', async () => {
  const fetchMock = mockFetch()
  await categoriesApi.status('42', 'inactive')
  await categoriesApi.status('42', 'active')
  await categoriesApi.remove('42')
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/categories/42/status')
  expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ status: 'inactive' })
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ status: 'active' })
  expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/admin/categories/42')
  expect(fetchMock.mock.calls[2][1].method).toBe('DELETE')
})
const row = (
  id: string,
  parentId: string,
  name: string,
  sort = 0,
  status = 'active',
): AdminCategory => ({
  id,
  parentId,
  name,
  sort,
  status,
  childCount: 0,
  productCount: 0,
})
const rows = [
  row('9007199254740995', '0', '厨电', 0),
  row('9007199254740993', '0', '安全', 0),
  row('3', '9007199254740995', '灶具', 2),
  row('4', '3', '燃气灶', 1, 'inactive'),
]
it('orders a complete hierarchy without losing precision for large IDs', () => {
  expect(categoryRows(rows).map((r) => [r.id, r.depth])).toEqual([
    ['9007199254740993', 0],
    ['9007199254740995', 0],
    ['3', 1],
    ['4', 2],
  ])
  expect(categoryRows(rows).at(-1)?.path).toBe('厨电 / 灶具 / 燃气灶')
})
it('keeps ancestors of filtered matches, overriding collapsed branches during search', () => {
  const collapsed = new Set(['9007199254740995'])
  expect(categoryRows(rows, '', '', collapsed).map((r) => r.id)).toEqual([
    '9007199254740993',
    '9007199254740995',
  ])
  const filtered = categoryRows(rows, '燃气灶', 'inactive', collapsed)
  expect(filtered.map((r) => r.id)).toEqual(['9007199254740995', '3', '4'])
  expect(filtered.map((r) => r.context)).toEqual([true, true, false])
  expect(categoryRows(rows, '不存在')).toEqual([])
})
it('excludes a category and all its descendants from parent choices', () => {
  expect([...descendants(rows, '9007199254740995')]).toEqual(['9007199254740995', '3', '4'])
  expect([...descendants([row('1', '2', 'a'), row('2', '1', 'b')], '1')]).toEqual(['1', '2'])
})
