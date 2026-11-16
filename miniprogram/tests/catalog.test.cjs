const { test, beforeEach, afterEach } = require('node:test')
const assert = require('node:assert/strict')
const api = require('../utils/api')
const { page, event, storage } = require('./helpers.cjs')
const originalRequest = api.request
beforeEach(() => { storage() })
afterEach(() => { api.request = originalRequest })
const result = (list = [], total = list.length, pages = Math.max(1, Math.ceil(total / 12))) => ({ results: { list, total, pages } })
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const tree = [
  { id: '9007199254740993', name: '生活用品', children: [{ id: '9007199254740994', name: '家居', children: [{ id: '9007199254740995', name: '收纳' }] }] },
  { id: '20', name: '生活用品', children: [{ id: '21', name: '其他' }] },
]

test('nested categories preserve large IDs and isolate descendants even when roots share a name', async () => {
  api.request = async url => url === '/categories/tree' ? tree : []
  const catalog = page('catalog')
  await catalog.options()
  catalog.setData({ categoryId: '9007199254740995' })
  catalog.syncCategory()
  assert.equal(catalog.data.rootId, '9007199254740993')
  assert.equal(catalog.data.categoryLabel, '生活用品 / 家居 / 收纳')
  assert.equal(catalog.data.categoryScrollTarget, 'root-9007199254740993')
  assert.deepEqual(Array.from(catalog.data.subcategories, item => item.id), ['9007199254740993', '9007199254740994', '9007199254740995'])
  assert.equal(catalog.data.subcategories[2].name, '家居 / 收纳')
  catalog.load = () => {}
  catalog.quickCategory(event({ id: '20' }))
  assert.equal(catalog.data.rootId, '20')
  assert.deepEqual(Array.from(catalog.data.subcategories, item => item.id), ['20', '21'])
})

test('failed classification options do not hide successfully loaded products', async () => {
  api.request = async url => {
    if (url === '/categories/tree') throw new Error('分类暂不可用')
    if (url === '/mall/brands') return [{ id: '2', name: '真实品牌' }]
    return result([{ id: '3', name: '商品', price: 12 }])
  }
  const catalog = page('catalog')
  await Promise.all([catalog.options(), catalog.load()])
  assert.match(catalog.data.optionsError, /分类/)
  assert.equal(catalog.data.error, '')
  assert.equal(catalog.data.products[0].id, '3')
  assert.equal(catalog.data.brands[1].id, '2')
  assert.equal(catalog.data.optionsLoading, false)
})

test('filter drafts make no requests and cancelling discards edits; confirming applies one query', async () => {
  const requests = []
  api.request = async url => { requests.push(new URL(url, 'https://local.invalid')); return result() }
  const catalog = page('catalog')
  catalog.setData({ categoryId: '8', searchKeyword: '热水器', brands: [{ id: '', name: '全部品牌' }, { id: '7', name: '品牌甲' }] })
  catalog.toggleFilters()
  catalog.draftInput(event({ key: 'minPrice' }, '10'))
  catalog.draftBrand(event({}, '1'))
  catalog.draftStock(event({}, true))
  assert.equal(requests.length, 0)
  catalog.closeFilters()
  assert.equal(catalog.data.minPrice, '')
  assert.equal(catalog.data.brandId, '')
  catalog.toggleFilters()
  assert.equal(catalog.data.filterDraft.minPrice, '')
  assert.equal(catalog.data.brandIndex, 0)
  catalog.draftInput(event({ key: 'minPrice' }, '10'))
  catalog.draftInput(event({ key: 'maxPrice' }, '20.50'))
  catalog.draftBrand(event({}, '1'))
  catalog.draftStock(event({}, true))
  await catalog.applyFilters()
  assert.equal(requests.length, 1)
  const params = requests[0].searchParams
  for (const [key, value] of Object.entries({ categoryId: '8', keyword: '热水器', brandId: '7', minPrice: '10', maxPrice: '20.50', inStock: 'true', page: '1', size: '12' })) assert.equal(params.get(key), value)
  assert.equal(catalog.data.filterCount, 3)
  assert.equal(catalog.data.showFilters, false)
})

test('invalid or reversed prices keep the panel open without changing committed filters', async () => {
  let requests = 0
  api.request = async () => { requests++; return result() }
  const catalog = page('catalog')
  catalog.toggleFilters()
  catalog.draftInput(event({ key: 'minPrice' }, '12.345'))
  await catalog.applyFilters()
  assert.match(catalog.data.filterError, /两位小数/)
  catalog.draftInput(event({ key: 'minPrice' }, '20'))
  catalog.draftInput(event({ key: 'maxPrice' }, '10'))
  await catalog.applyFilters()
  assert.match(catalog.data.filterError, /不能高于/)
  assert.equal(catalog.data.showFilters, true)
  assert.equal(catalog.data.minPrice, '')
  assert.equal(requests, 0)
  catalog.resetDraft()
  assert.equal(catalog.data.filterDraft.minPrice, '')
  assert.equal(catalog.data.filterError, '')
})

test('sort buttons and price direction change the actual server order', async () => {
  const queries = []
  api.request = async url => { queries.push(new URL(url, 'https://local.invalid').searchParams); return result() }
  const catalog = page('catalog')
  for (const key of ['1', 'price', 'price', '4', '0']) await catalog.chooseSort(event({ key }))
  assert.deepEqual(queries.map(q => [q.get('sort'), q.get('order')]), [
    ['sales', 'desc'], ['price', 'asc'], ['price', 'desc'], ['newest', 'desc'], ['comprehensive', 'desc'],
  ])
})

test('load more appends unique items, commits page only on success and retries the failed page', async () => {
  const requests = []
  let failNext = true
  api.request = async url => {
    const current = Number(new URL(url, 'https://local.invalid').searchParams.get('page'))
    requests.push(current)
    if (current === 1) return result([{ id: '1' }, { id: '2' }], 13, 2)
    if (failNext) { failNext = false; throw new Error('分页失败') }
    return result([{ id: '2' }, { id: '3' }], 13, 2)
  }
  const catalog = page('catalog')
  await catalog.load()
  await catalog.onReachBottom()
  assert.equal(catalog.data.page, 1)
  assert.equal(catalog.data.products.length, 2)
  assert.match(catalog.data.moreError, /分页/)
  assert.equal(catalog.data.error, '')
  await catalog.loadMore()
  assert.deepEqual(Array.from(catalog.data.products, item => item.id), ['1', '2', '3'])
  assert.equal(catalog.data.page, 2)
  assert.equal(catalog.data.hasMore, false)
  assert.equal(catalog.data.moreError, '')
  await catalog.loadMore()
  assert.deepEqual(requests, [1, 2, 2])
})

test('typing an unsubmitted keyword never changes the query while loading more', async () => {
  const queries = []
  api.request = async url => { queries.push(new URL(url, 'https://local.invalid').searchParams); return result([{ id: String(queries.length) }], 24, 2) }
  const catalog = page('catalog')
  catalog.input(event({}, ' 灶具 '))
  await catalog.search()
  catalog.input(event({}, '尚未提交的新词'))
  await catalog.loadMore()
  assert.equal(queries[1].get('keyword'), '灶具')
  assert.equal(queries[1].get('page'), '2')
  await catalog.clearSearch()
  assert.equal(queries[2].has('keyword'), false)
  assert.equal(queries[2].get('page'), '1')
})

test('rapid category switches ignore old responses and restart at the top of the new list', async () => {
  const requests = [], scrolls = []
  wx.pageScrollTo = options => scrolls.push(options.scrollTop)
  api.request = () => { const pending = deferred(); requests.push(pending); return pending.promise }
  const catalog = page('catalog')
  catalog.setData({ categoryShortcuts: [{ id: '', name: '全部' }, { id: '1', name: '甲' }, { id: '2', name: '乙' }] })
  const first = catalog.quickCategory(event({ id: '1' }))
  const second = catalog.quickCategory(event({ id: '2' }))
  requests[1].resolve(result([{ id: '20' }]))
  await second
  requests[0].resolve(result([{ id: '10' }]))
  await first
  assert.equal(catalog.data.categoryId, '2')
  assert.equal(catalog.data.products[0].id, '20')
  assert.equal(catalog.data.loading, false)
  assert.deepEqual(scrolls, [0, 0])
})

test('an in-flight page cannot be submitted twice or add products after unload', async () => {
  const pending = deferred()
  let requests = 0
  api.request = () => { requests++; return pending.promise }
  const catalog = page('catalog')
  const first = catalog.load()
  const duplicate = catalog.load()
  assert.equal(duplicate, first)
  assert.equal(requests, 1)
  catalog.onUnload()
  pending.resolve(result([{ id: '1' }]))
  await Promise.all([first, duplicate])
  assert.equal(catalog.data.products.length, 0)
})

test('a failed refresh retains the same-filter list; a failed new filter cannot display old items', async () => {
  const catalog = page('catalog')
  api.request = async () => result([{ id: '1' }])
  await catalog.load()
  api.request = async () => { throw new Error('网络失败') }
  await catalog.load()
  assert.equal(catalog.data.products[0].id, '1')
  catalog.setData({ brandId: '9' })
  await catalog.load()
  assert.equal(catalog.data.products.length, 0)
  assert.equal(catalog.data.ready, false)
  assert.match(catalog.data.error, /网络/)
})

test('empty results, unknown prices and image failure keep honest display states', async () => {
  const catalog = page('catalog')
  api.request = async () => result()
  await catalog.load()
  assert.equal(catalog.data.ready, true)
  assert.equal(catalog.data.total, 0)
  assert.equal(catalog.data.hasMore, false)
  api.request = async () => result([{ id: '1', price: null }, { id: '2', price: 0, skuCount: 2 }, { id: '3', price: '12.5', coverUrl: '/images/products/pepsi.png' }])
  await catalog.load()
  assert.equal(catalog.data.products[0].priceText, '')
  assert.equal(catalog.data.products[1].priceText, '0.00')
  assert.equal(catalog.data.products[1].needsSpec, true)
  assert.equal(catalog.data.products[2].priceText, '12.50')
  catalog.imageError(event({ id: '3' }))
  assert.equal(catalog.data.products[2].imageFailed, true)
})

test('product selection does not require login and repeated taps cannot stack detail pages', () => {
  const visits = []
  let navigation
  wx.navigateTo = options => { visits.push(options.url); navigation = options }
  const catalog = page('catalog')
  catalog.data.products = [{ id: '9007199254740993' }]
  catalog.product(event({ id: '9007199254740993' }))
  catalog.product(event({ id: '9007199254740993' }))
  assert.deepEqual(visits, ['/pages/product/index?id=9007199254740993'])
  navigation.complete()
  catalog.product(event({ id: 'unknown' }))
  assert.equal(visits.length, 1)
})

test('pull-to-refresh stops the system spinner after failure', async () => {
  let stopped = 0
  wx.stopPullDownRefresh = () => { stopped++ }
  api.request = async () => { throw new Error('网络失败') }
  const catalog = page('catalog')
  await catalog.onPullDownRefresh()
  assert.equal(stopped, 1)
  assert.equal(catalog.data.loading, false)
})

test('pull-to-refresh waits for a shared in-flight request before stopping the spinner', async () => {
  const pending = deferred()
  let stopped = 0, requests = 0
  wx.stopPullDownRefresh = () => { stopped++ }
  api.request = () => { requests++; return pending.promise }
  const catalog = page('catalog')
  const first = catalog.load()
  const refresh = catalog.onPullDownRefresh()
  assert.equal(stopped, 0)
  assert.equal(requests, 1)
  pending.resolve(result([{ id: '1' }]))
  await Promise.all([first, refresh])
  assert.equal(stopped, 1)
  assert.equal(catalog.data.products[0].id, '1')
})
