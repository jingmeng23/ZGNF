const { test, beforeEach, afterEach } = require('node:test')
const assert = require('node:assert/strict')
const api = require('../utils/api')
const { page, event, storage } = require('./helpers.cjs')
const originalRequest = api.request
beforeEach(() => { storage() })
afterEach(() => { api.request = originalRequest })
function deferred() {
  let resolve
  const promise = new Promise(yes => { resolve = yes })
  return { promise, resolve }
}
function result(list = []) { return { results: { list, total: list.length } } }

test('home loads a bounded unfiltered product list without category requests or gas-only filtering', async () => {
  const requests = []
  api.request = async url => {
    requests.push(url)
    if (url === '/mall/home') throw new Error('optional banner unavailable')
    return result([{ id: '2', name: '实际商品', price: '8.5', coverUrl: '/images/products/pepsi.png' }])
  }
  const home = page('home')
  await home.onLoad()
  assert.equal(requests.length, 2)
  const search = new URL(requests.find(url => url.startsWith('/mall/search?')), 'https://local.invalid')
  assert.equal(search.searchParams.get('size'), '6')
  assert.equal(search.searchParams.get('page'), '1')
  assert.equal(search.searchParams.has('categoryId'), false)
  assert.equal(search.searchParams.has('keyword'), false)
  assert.equal(home.data.products[0].name, '实际商品')
  assert.equal(home.data.products[0].priceText, '8.50')
  assert.equal(home.data.products[0].image, '/assets/products/pepsi.png')
  assert.equal(home.data.error, '')
  assert.equal(home.data.loading, false)
})

test('an empty product response remains empty; unknown prices are never displayed as zero', async () => {
  const home = page('home')
  api.request = async () => result()
  await home.loadProducts()
  assert.equal(home.data.products.length, 0)
  assert.equal(home.data.total, 0)
  assert.equal(home.data.error, '')
  api.request = async () => result([{ id: '1', price: null }, { id: '2', price: 'invalid' }, { id: '3', price: 0 }])
  await home.loadProducts()
  assert.equal(home.data.products[0].priceText, '')
  assert.equal(home.data.products[1].priceText, '')
  assert.equal(home.data.products[2].priceText, '0.00')
})

test('refresh failure preserves visible products and retry replaces them with the latest response', async () => {
  const home = page('home')
  api.request = async () => result([{ id: '1', name: '已读商品' }])
  await home.loadProducts()
  api.request = async () => { throw new Error('网络暂不可用') }
  await home.loadProducts()
  assert.equal(home.data.products[0].name, '已读商品')
  assert.match(home.data.error, /网络/)
  assert.equal(home.data.loading, false)
  api.request = async () => result([{ id: '2', name: '更新商品' }])
  await home.retryProducts()
  assert.equal(home.data.products[0].name, '更新商品')
  assert.equal(home.data.error, '')
})

test('late product responses cannot overwrite a newer refresh or an unloaded page', async () => {
  const home = page('home')
  const older = deferred(), newer = deferred()
  api.request = () => older.promise
  const first = home.loadProducts()
  api.request = () => newer.promise
  const second = home.loadProducts()
  newer.resolve(result([{ id: '2' }]))
  await second
  older.resolve(result([{ id: '1' }]))
  await first
  assert.equal(home.data.products[0].id, '2')
  const last = deferred()
  api.request = () => last.promise
  const loading = home.loadProducts()
  home.onUnload()
  last.resolve(result([{ id: '3' }]))
  await loading
  assert.equal(home.data.products[0].id, '2')
})

test('pull-to-refresh stops its spinner when both product and optional banner requests fail', async () => {
  let stopped = 0
  wx.stopPullDownRefresh = () => { stopped++ }
  api.request = async () => { throw new Error('网络失败') }
  const home = page('home')
  await home.onPullDownRefresh()
  assert.equal(stopped, 1)
  assert.equal(home.data.loading, false)
  assert.match(home.data.error, /网络/)
})

test('home search hands the keyword to catalog and all-products clears previous shopping filters', () => {
  const app = { globalData: {} }
  const home = page('home', app), catalog = page('catalog', app)
  const visits = []
  wx.switchTab = options => visits.push(options.url)
  catalog.load = () => {}
  home.input(event({}, '  热水器 & 配件  '))
  home.search()
  assert.equal(visits.pop(), '/pages/catalog/index')
  assert.equal(catalog.consumeNavigation(), true)
  assert.equal(catalog.data.keyword, '热水器 & 配件')
  catalog.setData({ brandId: '4', minPrice: '999', maxPrice: '1000', categoryId: '6', inStock: true, sortIndex: 3 })
  home.catalog()
  catalog.consumeNavigation()
  assert.equal(catalog.data.keyword, '')
  assert.equal(catalog.data.categoryId, '')
  assert.equal(catalog.data.brandId, '')
  assert.equal(catalog.data.minPrice, '')
  assert.equal(catalog.data.maxPrice, '')
  assert.equal(catalog.data.inStock, false)
  assert.equal(catalog.data.sortIndex, 0)
})

test('banner accepts a real product target and falls back to the catalog without a configured target', async () => {
  const visits = []
  wx.navigateTo = options => visits.push(options.url)
  wx.switchTab = options => visits.push(options.url)
  api.request = async url => url === '/mall/home'
    ? [{ type: 'banner', items: [
      { imageUrl: '/images/unusable.png', path: '/pages/home/index' },
      { imageUrl: '/images/products/pepsi.png', productId: '2' },
    ] }]
    : result()
  const home = page('home')
  await home.loadHome()
  home.banner()
  assert.equal(visits.pop(), '/pages/product/index?id=2')
  home.bannerError()
  assert.equal(home.data.bannerImage, '')
  home.setData({ bannerTarget: null })
  home.banner()
  assert.equal(visits.pop(), '/pages/catalog/index')
})

test('multi-spec purchase opens detail without adding; unavailable goods cannot be added', async () => {
  const visits = []
  wx.navigateTo = options => visits.push(options.url)
  let requests = 0
  api.request = async () => { requests++ }
  const home = page('home')
  home.data.products = [{ id: 2, purchasable: true, skuCount: 2 }, { id: 3, purchasable: false, skuCount: 0 }]
  await home.buy(event({ id: 2 }))
  assert.equal(visits.pop(), '/pages/product/index?id=2')
  await home.buy(event({ id: 3 }))
  assert.equal(visits.length, 0)
  assert.equal(requests, 0)
})

test('cart errors stay separate from product loading and retain the idempotency key for uncertain retries', async () => {
  api.save({ accessToken: 'customer', expiresIn: 3600 })
  const keys = []
  api.request = async (url, method, body, headers) => {
    keys.push(headers['Idempotency-Key'])
    const error = new Error('加购结果待确认')
    error.uncertain = true
    throw error
  }
  const home = page('home')
  home.data.products = [{ id: '2', purchasable: true, skuCount: 0 }]
  await home.buy(event({ id: '2' }))
  await home.buy(event({ id: '2' }))
  assert.equal(home.data.error, '')
  assert.equal(home.data.products.length, 1)
  assert.match(home.data.actionError, /加购结果/)
  assert.equal(keys.length, 2)
  assert.equal(keys[0], keys[1])
})
