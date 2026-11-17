const { test, beforeEach, afterEach } = require('node:test')
const assert = require('node:assert/strict')
const api = require('../utils/api')
const { page, event, storage } = require('./helpers.cjs')
const request = api.request
let visits
const row = (values = {}) => Object.assign({ id: '9007199254740993', productId: '9007199254740994', productName: '燃气设备', coverUrl: '/images/products/pepsi.png', quantity: 2, unitPrice: '12.30', subtotal: '24.60', availableStock: 5, available: true, selected: true }, values)
const cart = (values = {}) => Object.assign({ items: [row()], invalidItems: [], total: 1, totalQuantity: 2, selectedQuantity: 2, selectedAmount: '24.60' }, values)
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
beforeEach(() => {
  storage(); api.save({ accessToken: 'customer', expiresIn: 3600 })
  visits = []
  wx.navigateTo = options => { visits.push(options.url); if (options.complete) options.complete() }
  wx.switchTab = options => { visits.push(options.url); if (options.complete) options.complete() }
})
afterEach(() => { api.request = request })
async function loaded(value = cart(), app) {
  api.request = async () => value
  const p = page('cart', app)
  await p.onShow()
  return p
}

test('guest cart does not request private data and login and shopping links preserve native routes', async () => {
  api.logout()
  api.request = () => assert.fail('guest must not request private cart')
  const p = page('cart')
  await p.onShow()
  assert.equal(p.data.cart, null)
  assert.equal(p.data.authenticated, false)
  p.login(); p.shop()
  assert.deepEqual(visits, ['/pages/login/index', '/pages/catalog/index'])
})

test('empty cart stays empty and money uses server totals, including zero and unknown prices', async () => {
  const p = await loaded(cart({ items: [], total: 0, totalQuantity: 0, selectedQuantity: 0, selectedAmount: 0 }))
  assert.equal(p.data.cart.items.length, 0)
  assert.equal(p.data.amountText, '0.00')
  p.checkout()
  assert.equal(visits.length, 0)
  api.request = async () => cart({ selectedAmount: '123.45' })
  await p.load()
  assert.equal(p.data.amountText, '123.45')
  assert.equal(p.data.cart.items[0].priceText, '12.30')
  api.request = async () => cart({ selectedAmount: null, items: [row({ unitPrice: null })] })
  await p.load()
  assert.equal(p.data.amountText, '')
  assert.equal(p.data.cart.items[0].priceText, '')
  p.checkout()
  assert.equal(visits.length, 0)
})

test('repeated shows share a request and refresh keeps the same page visible while blocking checkout', async () => {
  const p = await loaded(), pending = deferred()
  let calls = 0
  api.request = () => { calls++; return pending.promise }
  const first = p.onShow(), second = p.onShow()
  assert.equal(p.data.cart.items[0].id, '9007199254740993')
  assert.equal(p.data.loading, true)
  p.checkout()
  assert.equal(visits.length, 0)
  pending.resolve(cart()); await Promise.all([first, second])
  assert.equal(calls, 1)
  assert.equal(p.data.loading, false)
  assert.equal(p.data.stale, false)
})

test('failed refresh retains content but blocks stale actions until a successful retry', async () => {
  const p = await loaded()
  api.request = async () => { throw new Error('无法连接商城') }
  await p.load()
  assert.equal(p.data.cart.items[0].productName, '燃气设备')
  assert.equal(p.data.stale, true)
  assert.match(p.data.error, /无法连接/)
  api.request = () => assert.fail('stale data must not mutate')
  await p.all(); await p.update(event({ id: row().id, action: 'plus' })); p.checkout()
  assert.equal(visits.length, 0)
  api.request = async () => cart({ selectedAmount: '30.00' })
  await p.load()
  assert.equal(p.data.stale, false)
  assert.equal(p.data.amountText, '30.00')
  assert.equal(p.data.error, '')
})

test('account replacement and expiry clear old private cart and ignore late responses', async () => {
  const p = await loaded(), pending = deferred()
  api.request = () => pending.promise
  const old = p.load()
  api.save({ accessToken: 'replacement', expiresIn: 3600 })
  const next = deferred()
  api.request = () => next.promise
  const replacement = p.onShow()
  assert.equal(p.data.cart, null)
  next.resolve(cart({ items: [row({ productName: '新账号商品' })] })); await replacement
  pending.resolve(cart({ items: [row({ productName: '旧账号商品' })] })); await old
  assert.equal(p.data.cart.items[0].productName, '新账号商品')
  api.request = async () => { api.logout(); throw new Error('登录过期') }
  await p.load()
  assert.equal(p.data.cart, null)
  assert.equal(p.data.authenticated, false)
  assert.equal(p.data.selectedTotal, 0)
})

test('unloaded cart ignores late responses and does not mutate page state', async () => {
  const p = page('cart'), pending = deferred()
  api.request = () => pending.promise
  const loading = p.load()
  p.onUnload()
  p.setData = () => assert.fail('unloaded page must not update')
  pending.resolve(cart()); await loading
})

test('images use bundled resources and both groups fall back on failed image loading', async () => {
  const p = await loaded(cart({ invalidItems: [row({ id: '9', available: false })] }))
  assert.equal(p.data.cart.items[0].image, '/assets/products/pepsi.png')
  p.imageError(event({ id: row().id, group: 'items' }))
  p.imageError(event({ id: '9', group: 'invalidItems' }))
  assert.equal(p.data.cart.items[0].image, '')
  assert.equal(p.data.cart.invalidItems[0].image, '')
})

test('quantity validates integer stock bounds locally, restores input and uses cart item IDs with idempotency', async () => {
  const p = await loaded(), calls = []
  api.request = async (path, method, data, headers) => { calls.push({ path, method, data, headers }); return cart({ items: [row({ quantity: 3 })], totalQuantity: 3 }) }
  for (const value of ['', '0', '-1', '1.5', '6', '1000']) {
    p.quantityInput(event({ id: row().id }, value))
    await p.quantity(event({ id: row().id }, value))
    assert.equal(p.data.quantityValues[row().id], '2')
  }
  assert.equal(calls.length, 0)
  p.quantityInput(event({ id: row().id }, '3'))
  p.checkout()
  assert.equal(visits.length, 0)
  await p.quantity(event({ id: row().id }, '3'))
  assert.equal(calls[0].path, '/cart/items/9007199254740993')
  assert.equal(calls[0].data.quantity, 3)
  assert.ok(calls[0].headers['Idempotency-Key'])
  assert.equal(calls.length, 1, 'PUT response must update the cart without another GET')
  assert.equal(p.data.cart.items[0].quantity, 3)
})

test('quantity update suppresses repeated taps, locks checkout and uses the PUT server amount without a reload', async () => {
  const p = await loaded(), pending = deferred()
  let writes = 0
  api.request = async (path, method) => {
    if (method) { writes++; return pending.promise }
    assert.fail('quantity update must not fetch the entire cart again')
  }
  const first = p.update(event({ id: row().id, action: 'plus' }))
  await p.update(event({ id: row().id, action: 'plus' })); p.checkout()
  assert.equal(writes, 1)
  assert.equal(visits.length, 0)
  assert.equal(p.data.loading, false)
  assert.equal(p.data.stale, false)
  assert.equal(p.data.quantityBusy, true)
  pending.resolve(cart({ items: [row({ quantity: 3, subtotal: '36.90' })], totalQuantity: 3, selectedQuantity: 3, selectedAmount: '36.90' })); await first
  assert.equal(p.data.amountText, '36.90')
  assert.equal(p.data.busy, false)
  assert.equal(p.data.quantityBusy, false)
})

test('quantity updates preserve card order and failed image placeholders and only patch changed fields', async () => {
  const second = row({ id: '12', productName: '第二件商品' })
  const p = await loaded(cart({ items: [row(), second], total: 2, totalQuantity: 4, selectedQuantity: 4 }))
  p.imageError(event({ id: row().id, group: 'items' }))
  const patches = [], original = p.setData
  p.setData = function (value) { patches.push(value); original.call(this, value) }
  api.request = async (path, method, { quantity }) => cart({ items: [second, row({ quantity, subtotal: quantity === 3 ? '36.90' : '24.60' })], total: 2, totalQuantity: quantity + 2, selectedQuantity: quantity + 2, selectedAmount: quantity === 3 ? '61.50' : '49.20' })
  await p.update(event({ id: row().id, action: 'plus' }))
  assert.deepEqual(Array.from(p.data.cart.items, item => item.id), [row().id, '12'])
  assert.equal(p.data.cart.items[0].image, '')
  assert.equal(p.data.cart.items[0].quantity, 3)
  assert.equal(p.data.cart.items[0].subtotalText, '36.90')
  assert.equal(p.data.amountText, '61.50')
  assert.equal(p.data.cart.totalQuantity, 5)
  assert.ok(patches.every(patch => !('cart' in patch) && !('cart.items' in patch) && !patch.loading && !patch.stale))
  assert.ok(patches.every(patch => !Object.keys(patch).some(key => key.endsWith('.image'))))
  await p.update(event({ id: row().id, action: 'minus' }))
  assert.equal(p.data.cart.items[0].quantity, 2)
  assert.equal(p.data.amountText, '49.20')
  assert.equal(p.data.loading, false)
})

test('successive quantity and image updates preserve both arrays using native bracket paths', async () => {
  const second = row({ id: '12', selected: false })
  const invalid = row({ id: '9', available: false, selected: false })
  const p = await loaded(cart({ items: [row(), second], invalidItems: [invalid], total: 2, totalQuantity: 6 }))
  const patches = [], original = p.setData
  p.setData = function (value) { patches.push(value); original.call(this, value) }
  let rows = [row(), second], writes = 0
  api.request = async (path, method, { quantity }) => {
    assert.equal(method, 'PUT', 'normal quantity edits must not reload the list')
    writes++
    const id = path.slice(path.lastIndexOf('/') + 1)
    rows = rows.map(item => item.id === id ? row({ ...item, quantity, subtotal: (quantity * 12.3).toFixed(2) }) : item)
    return cart({ items: rows, invalidItems: [row({ ...invalid, availableStock: 0 })], total: 2,
      totalQuantity: rows.reduce((sum, item) => sum + item.quantity, 2),
      selectedQuantity: rows[0].quantity, selectedAmount: rows[0].subtotal })
  }
  p.imageError(event({ id: row().id, group: 'items' }))
  p.imageError(event({ id: invalid.id, group: 'invalidItems' }))
  for (const [id, action] of [[row().id, 'plus'], [second.id, 'plus'], [row().id, 'minus'], [second.id, 'minus']]) {
    await p.update(event({ id, action }))
    assert.equal(Array.isArray(p.data.cart.items), true)
    assert.equal(Array.isArray(p.data.cart.invalidItems), true)
    assert.equal(p.data.cart.items.length, 2)
    assert.equal(p.data.cart.invalidItems.length, 1)
    assert.ok(p.item(id))
    assert.equal(p.data.error, '')
  }
  assert.equal(writes, 4)
  assert.equal(p.data.cart.items[0].quantity, 2)
  assert.equal(p.data.cart.items[1].quantity, 2)
  assert.equal(p.data.amountText, '24.60')
  assert.equal(p.data.cart.items[0].image, '')
  assert.equal(p.data.cart.invalidItems[0].image, '')
  const paths = patches.flatMap(patch => Object.keys(patch))
  assert.ok(paths.includes('cart.items[0].quantity'))
  assert.ok(paths.includes('cart.items[1].quantity'))
  assert.ok(paths.includes('cart.items[0].image'))
  assert.ok(paths.includes('cart.invalidItems[0].image'))
  assert.ok(paths.includes('cart.invalidItems[0].availableStock'))
  assert.ok(paths.every(key => !/^cart\.(items|invalidItems)\.\d+(\.|$)/.test(key)))
})

test('corrupted cart lists do not crash handlers or allow checkout and a refresh restores them', async () => {
  for (const group of ['items', 'invalidItems']) {
    const p = await loaded()
    p.data.cart[group] = { 0: row() }
    api.request = () => assert.fail('corrupted cart must not submit a write')
    p.checkout()
    assert.equal(visits.length, 0)
    p.imageError(event({ id: row().id, group }))
    assert.equal(p.patchQuantityCart(cart()), false)
    await p.update(event({ id: row().id, action: 'plus' }))
    assert.equal(p.data.stale, true)
    assert.match(p.data.error, /请刷新/)
    api.request = async () => cart()
    await p.load()
    assert.equal(Array.isArray(p.data.cart.items), true)
    assert.equal(Array.isArray(p.data.cart.invalidItems), true)
    assert.equal(p.data.stale, false)
    assert.equal(p.item(row().id).quantity, 2)
  }
})

test('a full quantity response updates global totals without inserting other pages or changing page metadata', async () => {
  const p = await loaded(cart({ total: 21, page: 2, size: 20 }))
  p.setData({ page: 2 }); p._loadedPage = 2
  const items = Array.from({ length: 20 }, (_, i) => row({ id: String(i + 1) })).concat(row({ quantity: 3 }))
  api.request = async () => cart({ items, total: 21, page: 1, size: 2147483647, totalQuantity: 43, selectedQuantity: 43, selectedAmount: '528.90' })
  await p.update(event({ id: row().id, action: 'plus' }))
  assert.equal(p.data.cart.items.length, 1)
  assert.equal(p.data.cart.items[0].id, row().id)
  assert.equal(p.data.cart.items[0].quantity, 3)
  assert.equal(p.data.page, 2)
  assert.equal(p.data.cart.page, 2)
  assert.equal(p.data.cart.size, 20)
  assert.equal(p.data.pages, 2)
  assert.equal(p.data.amountText, '528.90')
})

test('quantity response that moves a product to invalid items is reconciled and blocks checkout', async () => {
  const p = await loaded(), calls = []
  const value = cart({ items: [], invalidItems: [row({ available: false, availableStock: 0 })], total: 0, selectedQuantity: 0, selectedAmount: 0 })
  api.request = async (path, method) => { calls.push(method || 'GET'); return value }
  await p.update(event({ id: row().id, action: 'plus' }))
  assert.deepEqual(calls, ['PUT', 'GET'])
  assert.equal(p.data.cart.items.length, 0)
  assert.equal(p.data.cart.invalidItems.length, 1)
  p.checkout()
  assert.equal(visits.length, 0)
})

test('late quantity writes cannot overwrite another account or update an unloaded page', async () => {
  const p = await loaded(), pending = deferred()
  api.request = () => pending.promise
  const writing = p.update(event({ id: row().id, action: 'plus' }))
  api.save({ accessToken: 'replacement', expiresIn: 3600 })
  api.request = async () => cart({ items: [row({ productName: '新账号商品' })] })
  await p.onShow()
  pending.resolve(cart({ items: [row({ quantity: 3 })] })); await writing
  assert.equal(p.data.cart.items[0].productName, '新账号商品')
  assert.equal(p.data.quantityBusy, false)
  const next = deferred()
  api.request = () => next.promise
  const unloaded = p.update(event({ id: row().id, action: 'plus' }))
  p.onUnload()
  p.setData = () => assert.fail('unloaded page must not update')
  next.resolve(cart()); await unloaded
})

test('uncertain mutation and failed reconciliation keep checkout disabled until server state is fetched', async () => {
  const p = await loaded()
  api.request = async (path, method) => { throw Object.assign(new Error('网络中断'), method ? { uncertain: true } : {}) }
  await p.update(event({ id: row().id, action: 'plus' }))
  assert.equal(p.data.busy, false)
  assert.equal(p.data.stale, true)
  assert.match(p.data.error, /尚未确认/)
  assert.equal(p.data.quantityValues[row().id], '2')
  p.checkout()
  assert.equal(visits.length, 0)
  api.request = async () => cart()
  await p.load()
  assert.equal(p.data.stale, false)
})

test('invalid selected items block checkout and cannot be selected anew', async () => {
  const invalid = row({ id: '9', available: false, quantity: 4, selected: true })
  const p = await loaded(cart({ invalidItems: [invalid], totalQuantity: 6 }))
  assert.equal(p.data.selectedInvalid, 1)
  assert.equal(p.data.selectedTotal, 6)
  assert.equal(p.data.amountText, '24.60')
  p.checkout()
  assert.equal(visits.length, 0)
  let writes = 0
  api.request = async (path, method, data) => {
    if (method) { writes++; assert.equal(path, '/cart/items/9/selection'); assert.equal(data.selected, false) }
    return cart({ invalidItems: [Object.assign({}, invalid, { selected: false })], totalQuantity: 6 })
  }
  await p.update(event({ id: '9', action: 'select' }))
  await p.update(event({ id: '9', action: 'select' }))
  assert.equal(writes, 1)
  assert.equal(p.data.selectedInvalid, 0)
})

test('cancelled deletion makes no requests and deletion confirmation cannot cross an account switch', async () => {
  const p = await loaded()
  api.request = () => assert.fail('cancelled deletion must not submit or refresh')
  wx.showModal = options => options.success({ confirm: false })
  await p.update(event({ id: row().id, action: 'remove' }))
  assert.equal(p.data.busy, false)
  let modal
  wx.showModal = options => { modal = options }
  const deleting = p.update(event({ id: row().id, action: 'remove' }))
  api.save({ accessToken: 'replacement', expiresIn: 3600 })
  modal.success({ confirm: true }); await deleting
  assert.equal(p.data.cart, null)
  assert.equal(p.data.busy, false)
})

test('management mode keeps shopping selections, explains deletion across pages and requires confirmation', async () => {
  const p = await loaded(cart({ total: 30 })), calls = []
  p.toggleManage()
  assert.equal(p.data.managing, true)
  assert.equal(p.data.cart.items[0].selected, true)
  p.checkout()
  assert.equal(visits.length, 0)
  wx.showModal = options => { assert.match(options.content, /所有分页/); options.success({ confirm: true }) }
  api.request = async (path, method) => { calls.push({ path, method }); return cart() }
  await p.batch(event({ action: 'selected' }))
  assert.equal(calls[0].path, '/cart/selected')
  assert.equal(calls[0].method, 'DELETE')
})

test('invalid cleanup uses batches of at most 100 and stops before sending another batch as a different user', async () => {
  const invalidItems = Array.from({ length: 101 }, (_, i) => row({ id: String(i + 100), available: false, quantity: 1 }))
  const p = await loaded(cart({ invalidItems, totalQuantity: 103 }))
  let writes = 0
  api.request = async (path, method, data) => {
    writes++; assert.equal(path, '/cart/items'); assert.equal(method, 'DELETE')
    assert.equal(data.ids.length, 100)
    api.save({ accessToken: 'replacement', expiresIn: 3600 })
  }
  await p.batch(event({ action: 'invalid' }))
  assert.equal(writes, 1)
  assert.equal(p.data.cart, null)
})

test('paging hides prior page content on failure and deletion of the last row returns to a valid page', async () => {
  const p = await loaded(cart({ total: 21 }))
  api.request = async () => { throw new Error('第二页失败') }
  await p.next()
  assert.equal(p.data.page, 2)
  assert.equal(p.data.cart, null)
  api.request = async () => cart({ total: 21 })
  await p.load()
  const calls = []
  api.request = async path => { calls.push(path); return cart() }
  await p.update(event({ id: row().id, action: 'remove' }))
  assert.equal(p.data.page, 1)
  assert.equal(p.data.loading, false)
  assert.equal(p.data.busy, false)
  assert.ok(calls.includes('/cart?page=1&size=20'))
})

test('checkout and product links preserve large IDs and suppress repeated navigation while pending', async () => {
  const p = await loaded()
  let navigation
  wx.navigateTo = options => { navigation = options; visits.push(options.url) }
  p.checkout(); p.checkout()
  assert.deepEqual(visits, ['/pages/checkout/index'])
  navigation.complete()
  p.product(event({ id: row().productId }))
  assert.equal(visits[1], '/pages/product/index?id=9007199254740994')
})

test('pull-to-refresh shares an active read and stops its system spinner after a failure', async () => {
  const p = await loaded(), pending = deferred()
  let stopped = 0, calls = 0
  wx.stopPullDownRefresh = () => { stopped++ }
  api.request = () => { calls++; return pending.promise }
  const first = p.load(), refresh = p.onPullDownRefresh()
  assert.equal(stopped, 0)
  pending.reject(new Error('网络失败')); await Promise.all([first, refresh])
  assert.equal(calls, 1)
  assert.equal(stopped, 1)
})

test('checkout stays locked after navigation completes and recovers after a route failure or return', async () => {
  const p = await loaded()
  let navigation
  wx.navigateTo = options => { navigation = options; visits.push(options.url) }
  p.checkout()
  assert.equal(p.data.checkingOut, true)
  navigation.complete()
  p.checkout()
  await p.all()
  assert.equal(visits.length, 1)
  navigation.fail()
  assert.equal(p.data.checkingOut, false)
  assert.match(p.data.error, /页面打开失败/)
  p.checkout()
  assert.equal(visits.length, 2)
  await p.onShow()
  assert.equal(p.data.checkingOut, false)
})

test('footer can deselect invalid items without adding valid selections or deleting products', async () => {
  const invalidItems = [row({ id: '9', available: false, selected: true }), row({ id: '10', available: false, selected: false })]
  const p = await loaded(cart({ invalidItems, totalQuantity: 6, selectedQuantity: 0, selectedAmount: 0, items: [row({ selected: false })] }))
  const calls = []
  api.request = async (path, method, data) => {
    if (method) calls.push({ path, method, data })
    return cart({ invalidItems: invalidItems.map(item => Object.assign({}, item, { selected: false })), totalQuantity: 6, selectedQuantity: 0, selectedAmount: 0, items: [row({ selected: false })] })
  }
  await p.deselectInvalid()
  assert.equal(calls.length, 1)
  assert.equal(calls[0].path, '/cart/items/9/selection')
  assert.equal(calls[0].method, 'PUT')
  assert.equal(calls[0].data.selected, false)
  assert.equal(p.data.selectedInvalid, 0)
  assert.equal(p.data.cart.items[0].selected, false)
  assert.equal(p.data.cart.invalidItems.length, 2)
})

test('footer invalid deselection stops before continuing under a replacement account', async () => {
  const invalidItems = [row({ id: '9', available: false, selected: true }), row({ id: '10', available: false, selected: true })]
  const p = await loaded(cart({ invalidItems, totalQuantity: 6 }))
  let writes = 0
  api.request = async () => { writes++; api.save({ accessToken: 'replacement', expiresIn: 3600 }) }
  await p.deselectInvalid()
  assert.equal(writes, 1)
  assert.equal(p.data.cart, null)
})

test('focusing a quantity pauses checkout and switching modes until its value is confirmed', async () => {
  const p = await loaded()
  p.quantityFocus(event({ id: row().id }))
  p.checkout(); p.toggleManage()
  assert.equal(visits.length, 0)
  assert.equal(p.data.managing, false)
  await p.quantity(event({ id: row().id }, '2'))
  assert.equal(p.data.editingQuantity, '')
  p.checkout()
  assert.deepEqual(visits, ['/pages/checkout/index'])
})
