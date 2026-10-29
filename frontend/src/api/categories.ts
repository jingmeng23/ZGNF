import { request } from './http'
import type { Category } from './types'

export type CategoryStatus = 'active' | 'inactive'
export interface CategoryInput {
  name: string
  parentId: string
  sort: number
  status: CategoryStatus
}
export interface AdminCategory extends Category {
  childCount: number
  productCount: number
}
export const categoriesApi = {
  list: () => request<AdminCategory[]>('/admin/categories/management'),
  detail: (id: string) => request<Category>(`/admin/categories/${encodeURIComponent(id)}`),
  save: (id: string | null, input: CategoryInput) =>
    request<Category>(id ? `/admin/categories/${encodeURIComponent(id)}` : '/admin/categories', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(input),
    }),
  status: (id: string, status: CategoryStatus) =>
    request<Category>(`/admin/categories/${encodeURIComponent(id)}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    }),
  remove: (id: string) =>
    request<void>(`/admin/categories/${encodeURIComponent(id)}`, { method: 'DELETE' }),
}

export function descendants(rows: Category[], id: string): Set<string> {
  const result = new Set([id])
  let changed = true
  while (changed) {
    changed = false
    for (const row of rows)
      if (result.has(row.parentId) && !result.has(row.id)) {
        result.add(row.id)
        changed = true
      }
  }
  return result
}

export function categoryRows(
  rows: AdminCategory[],
  keyword = '',
  status = '',
  collapsed = new Set<string>(),
) {
  const byId = new Map(rows.map((row) => [row.id, row]))
  const included = new Set<string>()
  const term = keyword.trim().toLocaleLowerCase()
  const filtering = !!(term || status)
  for (const row of rows)
    if (
      (!term || row.name.toLocaleLowerCase().includes(term) || row.id === term) &&
      (!status || row.status === status)
    ) {
      let current: AdminCategory | undefined = row
      const path = new Set<string>()
      while (current && !path.has(current.id)) {
        path.add(current.id)
        included.add(current.id)
        current = byId.get(current.parentId)
      }
    }
  const children = new Map<string, AdminCategory[]>()
  for (const row of rows) {
    const key = byId.has(row.parentId) ? row.parentId : '0'
    const siblings = children.get(key) || []
    siblings.push(row)
    children.set(key, siblings)
  }
  children.forEach((siblings) =>
    siblings.sort(
      (a, b) =>
        a.sort - b.sort || (BigInt(a.id) < BigInt(b.id) ? -1 : BigInt(a.id) > BigInt(b.id) ? 1 : 0),
    ),
  )
  const result: (AdminCategory & { depth: number; path: string; context: boolean })[] = []
  const visited = new Set<string>()
  function visit(row: AdminCategory, depth: number, parentPath: string) {
    if (visited.has(row.id) || (filtering && !included.has(row.id))) return
    visited.add(row.id)
    const path = parentPath ? `${parentPath} / ${row.name}` : row.name
    result.push({
      ...row,
      depth,
      path,
      context:
        filtering &&
        !(
          (!term || row.name.toLocaleLowerCase().includes(term) || row.id === term) &&
          (!status || row.status === status)
        ),
    })
    if (!filtering && collapsed.has(row.id)) return
    for (const child of children.get(row.id) || []) visit(child, depth + 1, path)
  }
  for (const row of children.get('0') || []) visit(row, 0, '')
  return result
}
