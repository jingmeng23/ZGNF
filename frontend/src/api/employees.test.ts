import { afterEach, expect, it, vi } from 'vitest'
import { employeesApi, type EmployeeInput } from './employees'

afterEach(() => vi.unstubAllGlobals())
function mockFetch() {
  const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ code: 200, data: {} }))))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
const input: EmployeeInput = { username: 'technician', name: '服务员工', phone: '13800138000', departmentId: null, positionId: null, status: 'active' }
it('sends paginated filters without blank parameters and encodes the keyword', async () => {
  const f = mockFetch()
  await employeesApi.list({ page: 2, size: 20, keyword: '员工&甲', status: 'inactive', roleId: '' })
  const url = new URL(f.mock.calls[0][0], 'http://localhost')
  expect(url.pathname).toBe('/api/v1/admin/employees')
  expect(Object.fromEntries(url.searchParams)).toEqual({ page: '2', size: '20', keyword: '员工&甲', status: 'inactive' })
})
it('loads options and employee detail through the real employee resource', async () => {
  const f = mockFetch()
  await employeesApi.options(); await employeesApi.detail('9007199254740993')
  expect(f.mock.calls.map(call => call[0])).toEqual(['/api/v1/admin/employees/options', '/api/v1/admin/employees/9007199254740993'])
})
it('creates an employee with a separate initial password and preserves Long identifiers', async () => {
  const f = mockFetch()
  await employeesApi.createEmployee(input, 'a-strong-password')
  expect(f.mock.calls[0][1].method).toBe('POST')
  expect(JSON.parse(f.mock.calls[0][1].body)).toEqual({ employee: input, password: 'a-strong-password' })
})
it('updates only employee profile without silently replacing roles or password', async () => {
  const f = mockFetch()
  await employeesApi.update('7', input)
  expect(f.mock.calls[0][0]).toBe('/api/v1/admin/employees/7')
  expect(f.mock.calls[0][1].method).toBe('PUT')
  expect(JSON.parse(f.mock.calls[0][1].body)).toEqual(input)
})
it('changes status through the sparse endpoint', async () => {
  const f = mockFetch()
  await employeesApi.status('7', 'inactive')
  expect(f.mock.calls[0][0]).toBe('/api/v1/admin/employees/7/status')
  expect(JSON.parse(f.mock.calls[0][1].body)).toEqual({ status: 'inactive' })
})
it('separately assigns roles, clears roles, and resets passwords', async () => {
  const f = mockFetch()
  await employeesApi.roles('7', ['9007199254740993']); await employeesApi.roles('7', []); await employeesApi.resetPassword('7', 'new-strong-password')
  expect(f.mock.calls.map(call => call[1].method)).toEqual(['PUT', 'PUT', 'PUT'])
  expect(f.mock.calls[0][0]).toBe('/api/v1/admin/employees/7/roles')
  expect(JSON.parse(f.mock.calls[0][1].body)).toEqual({ ids: ['9007199254740993'] })
  expect(JSON.parse(f.mock.calls[1][1].body)).toEqual({ ids: [] })
  expect(f.mock.calls[2][0]).toBe('/api/v1/admin/employees/7/password')
  expect(JSON.parse(f.mock.calls[2][1].body)).toEqual({ password: 'new-strong-password' })
})
it('deletes through the protected employee endpoint', async () => {
  const f = mockFetch(); await employeesApi.deleteEmployee('7')
  expect(f.mock.calls[0][0]).toBe('/api/v1/admin/employees/7')
  expect(f.mock.calls[0][1].method).toBe('DELETE')
})
it('propagates server business protection errors instead of pretending to succeed', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 400001, message: '必须保留至少一个有效的内置管理员账号' }), { status: 400 })))
  await expect(employeesApi.status('7', 'inactive')).rejects.toThrow('必须保留至少一个有效的内置管理员账号')
})
