import { afterEach, expect, it, vi } from 'vitest'
import { createRenderer, nextTick, ssrContextKey } from 'vue'
import AdminProducts from './AdminProducts.vue'
import { productsApi, type ProductInput } from '../api/products'

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('../api/products', () => ({
  productsApi: {
    list: vi.fn(async () => ({ list: [], total: 0, pages: 0 })),
    options: vi.fn(async () => ({ categories: [], brands: [] })),
    save: vi.fn(async (_id: string | null, input: ProductInput) => ({ ...input })),
  },
}))

// Exercise the component's actual reactive state and lifecycle without a browser.
const renderer = createRenderer<any, any>({
  createElement: () => ({}),
  createText: () => ({}),
  createComment: () => ({}),
  insert() {},
  remove() {},
  setText() {},
  setElementText() {},
  patchProp() {},
  parentNode: () => null,
  nextSibling: () => null,
})
const apps: ReturnType<typeof renderer.createApp>[] = []
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.clearAllMocks()
})

async function editor() {
  const app = renderer.createApp({ ...AdminProducts, render: () => null })
  app.provide(ssrContextKey, {})
  apps.push(app)
  app.mount({})
  await nextTick()
  const state = (app as any)._instance.setupState
  state.mode = 'create'
  Object.assign(state.form, { name: '燃气灶', categoryId: '9007199254740993', price: 99.99 })
  return state
}

it('shows both existing products and the new draft across categories after creating', async () => {
  const state = await editor()
  Object.assign(state.query, {
    keyword: '旧商品',
    categoryId: '2',
    status: 'on_sale',
    sort: 'price',
    order: 'asc',
  })
  const products = [
    { id: 'new', categoryId: state.form.categoryId, status: 'draft' },
    { id: 'existing', categoryId: '2', status: 'on_sale' },
  ]
  vi.mocked(productsApi.list).mockImplementationOnce(async (query) => {
    const list = products.filter(
      (product) =>
        (!query.categoryId || product.categoryId === query.categoryId) &&
        (!query.status || product.status === query.status),
    )
    return { list, total: list.length, pages: 1 } as any
  })
  await state.save()
  expect(productsApi.save).toHaveBeenCalledWith(
    null,
    expect.objectContaining({
      categoryId: '9007199254740993',
      name: '燃气灶',
      price: 99.99,
      originalPrice: null,
    }),
  )
  expect(productsApi.list).toHaveBeenLastCalledWith(
    expect.objectContaining({
      page: 1,
      keyword: '',
      categoryId: '',
      status: '',
      sort: 'createTime',
      order: 'desc',
    }),
  )
  expect(state.mode).toBe(null)
  expect(state.rows.map((product: { id: string }) => product.id)).toEqual(['new', 'existing'])
  expect(state.total).toBe(2)
  expect(state.notice).toContain('已创建为草稿')
})

it('returns to the first page after creating a product from a later page', async () => {
  const state = await editor()
  state.query.page = 3
  await nextTick()
  await state.save()
  await nextTick()
  expect(productsApi.list).toHaveBeenLastCalledWith(
    expect.objectContaining({ page: 1, status: '', categoryId: '', keyword: '' }),
  )
})

it('keeps entered data and displays the error when saving fails', async () => {
  const state = await editor()
  vi.mocked(productsApi.save).mockRejectedValueOnce(new Error('分类不存在'))
  await state.save()
  expect(state.error).toBe('分类不存在')
  expect(state.mode).toBe('create')
  expect(state.form.name).toBe('燃气灶')
  expect(state.busy).toBe(false)
})

it('sends a freely entered brand name and allows clearing it', async () => {
  const state = await editor()
  state.form.brandName = '  百事可乐  '
  await state.save()
  expect(productsApi.save).toHaveBeenLastCalledWith(
    null,
    expect.objectContaining({ brandId: null, brandName: '百事可乐' }),
  )
  state.mode = 'edit'
  state.selected = { id: '42' }
  state.form.brandId = '123'
  state.form.brandName = '  '
  await state.save()
  expect(productsApi.save).toHaveBeenLastCalledWith(
    '42',
    expect.objectContaining({ brandId: null, brandName: null }),
  )
})

it('does not claim success when an older backend silently drops the brand name', async () => {
  const state = await editor()
  state.form.brandName = '百事可乐'
  vi.mocked(productsApi.save).mockResolvedValueOnce({ brandName: undefined } as any)
  await state.save()
  expect(state.mode).toBe('create')
  expect(state.form.brandName).toBe('百事可乐')
  expect(state.error).toContain('后端已更新')
  expect(state.notice).toBe('')
})

it('preserves list filters when editing an existing product', async () => {
  const state = await editor()
  state.mode = 'edit'
  state.selected = { id: '42' }
  state.query.status = 'on_sale'
  await state.save()
  expect(productsApi.save).toHaveBeenCalledWith('42', expect.any(Object))
  expect(state.query.status).toBe('on_sale')
})
