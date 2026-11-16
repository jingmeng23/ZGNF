const { test, beforeEach, afterEach } = require('node:test')
const assert = require('node:assert/strict')
const api = require('../utils/api')
const admin = require('../utils/admin')
const checkout = require('../utils/checkout')
const { modules, payload, allowed, prepareFields } = require('../utils/management')
const { categories, query } = require('../utils/business')
const { page, event, storage } = require('./helpers.cjs')
let apiRequest, adminRequest
beforeEach(() => { storage(); api.save({ accessToken: 'customer', expiresIn: 3600 }); apiRequest = api.request; adminRequest = admin.request })
afterEach(() => { api.request = apiRequest; admin.request = adminRequest })

test('category editing excludes itself and its complete subtree without converting Long IDs', () => {
  const rows = [{ id: '9007199254740993', parentId: '0', name: 'Root' }, { id: '2', parentId: '9007199254740993', name: 'Child' }, { id: '3', parentId: '2', name: 'Grandchild' }, { id: '4', parentId: '0', name: 'Other' }]
  assert.deepEqual(categories(rows, '9007199254740993').map(c => c.id), ['4'])
  assert.equal(categories(rows).find(c => c.id === '3').name, 'Root / Child / Grandchild')
  const fields = prepareFields(modules.find(m => m.key === 'categories').fields, { parentId: '0' }, { categories: rows }, 'categories', { id: '9007199254740993' })
  assert.deepEqual(fields.find(f => f.key === 'parentId').choices.filter(c => c.id).map(c => c.id), ['0', '4'])
})

test('product validation catches original-price mismatch and invalid media before submitting', () => {
  const fields = modules.find(m => m.key === 'products').fields
  assert.throws(() => payload(fields, { name: 'Stove', categoryId: '12', price: '20', originalPrice: '19' }), /原价/)
  for (const coverUrl of ['//example.com/a.png', 'javascript:alert(1)', 'https://example.com/a.png\n/b.png']) assert.throws(() => payload(fields, { name: 'Stove', categoryId: '12', price: '20', coverUrl }))
})

test('status controls follow cancellation, fulfillment, aftersale review and built-in role rules', () => {
  const orders = modules.find(m => m.key === 'orders'), cancel = orders.actions.find(a => a.key === 'cancel')
  assert.equal(allowed(orders, { status: 'paid' }, cancel), false)
  assert.equal(allowed(orders, { status: 'pending_payment' }, cancel), true)
  const aftersales = modules.find(m => m.key === 'aftersales'), complete = aftersales.actions.find(a => a.key === 'complete')
  assert.equal(allowed(aftersales, { status: 'pending' }, complete), false)
  const roles = modules.find(m => m.key === 'roles')
  assert.equal(allowed(roles, { roleKey: 'admin' }, roles.actions[0]), false)
})

test('grouped permission changes keep selections belonging to other groups', () => {
  const p = page('manage'), role = modules.find(m => m.key === 'roles')
  p.data.current = role; p.baseFields = role.actions[0].fields; p.editingDetail = null
  p.source = { permissions: [{ id: '1', name: 'Read products', permissionKey: 'product:read' }, { id: '2', name: 'Write products', permissionKey: 'product:write' }, { id: '9007199254740993', name: 'Read orders', permissionKey: 'order:read' }] }
  p.data.form = { ids: '1,9007199254740993' }
  p.data.fields = prepareFields(p.baseFields, p.data.form, p.source, 'roles')
  p.checks(event({ key: 'ids', group: 'product' }, ['2']))
  assert.deepEqual(p.data.form.ids.split(',').sort(), ['2', '9007199254740993'].sort())
  p.groupSelect(event({ key: 'ids', group: 'product' }))
  assert.deepEqual(p.data.form.ids.split(',').sort(), ['1', '2', '9007199254740993'].sort())
})

test('removing write authority closes an existing form after profile refresh', async () => {
  const p = page('manage')
  p.data.current = modules[0]; p.data.profile = { permissions: ['product:read', 'product:write'] }
  p.data.fields = modules[0].fields; p.data.operation = { edit: true }; p.data.busy = true
  admin.request = async () => ({ username: 'staff', permissions: ['product:read'] })
  await p.refreshProfile()
  assert.equal(p.data.fields, null); assert.equal(p.data.canWrite, false)
})

test('outbound and count adjustments protect both available and locked stock before HTTP', async () => {
  const p = page('manage'), inventory = modules.find(m => m.key === 'inventory')
  p.data.current = inventory; p.data.profile = { permissions: ['inventory:write'] }; p.data.detail = { id: '12', availableQuantity: 2, lockedQuantity: 5 }
  let requests = 0
  admin.request = async () => { requests++; throw new Error('unexpected HTTP') }
  for (const [key, quantity] of [['outbound', '3'], ['adjust', '4']]) {
    p.data.operation = inventory.actions.find(a => a.key === key); p.data.fields = p.data.operation.fields; p.data.form = { quantity, remark: 'count' }
    await p.submit(); assert.match(p.data.error, /不能/)
  }
  assert.equal(requests, 0)
})

test('ambiguous inventory responses freeze the original payload as well as its request ID', async () => {
  const p = page('manage'), inventory = modules.find(m => m.key === 'inventory'), requests = []
  p.data.current = inventory; p.data.profile = { permissions: ['inventory:write'] }; p.data.detail = { id: '12' }
  p.data.operation = inventory.actions.find(a => a.key === 'inbound'); p.data.fields = p.data.operation.fields; p.data.form = { quantity: '3', remark: 'delivery' }; p.requestId = 'stable'
  admin.request = async (path, method, body) => { requests.push(JSON.parse(JSON.stringify(body))); throw Object.assign(new Error('timeout'), { uncertain: true }) }
  await p.submit()
  assert.equal(p.data.formLocked, true)
  p.data.form.quantity = '9'
  await p.submit()
  assert.equal(requests.length, 2); assert.deepEqual(requests[0], requests[1]); assert.equal(requests[1].quantity, 3)
})

test('inventory log filters paginate using the actual log endpoint and retain zero-stock filters', async () => {
  const p = page('manage'), requests = []
  p.data.current = modules.find(m => m.key === 'inventory'); p.data.detail = { id: '9007199254740993' }
  p.data.records = { key: 'logs', page: 2, changeType: 'outbound', referenceNo: 'ORDER/12' }
  admin.request = async path => { requests.push(path); return { list: [], total: 12, pages: 2 } }
  await p.loadRecords()
  assert.match(requests[0], /^\/inventory\/logs\?inventoryId=9007199254740993&page=2&size=10&changeType=outbound&referenceNo=ORDER%2F12$/)
  assert.equal(query({ availableMax: 0, status: '' }), 'availableMax=0')
})

test('leaving an uncertain inventory form reloads committed quantities for reconciliation', async () => {
  const p = page('manage')
  p.data.current = modules.find(m => m.key === 'inventory'); p.data.profile = { permissions: ['inventory:read', 'inventory:write'] }
  p.data.detail = { id: '12', quantity: 3, lockedQuantity: 0, availableQuantity: 3 }; p.data.formLocked = true
  admin.request = async path => { assert.equal(path, '/inventory/12'); return { id: '12', quantity: 6, lockedQuantity: 0, availableQuantity: 6 } }
  await p.closeForm()
  assert.equal(p.data.detail.quantity, 6); assert.equal(p.data.fields, null)
})

test('all-selected state covers the entire cart even when the current page is completely selected', async () => {
  const p = page('cart')
  api.request = async () => ({ items: [{ id: '1', quantity: 20, selected: true }], invalidItems: [{ id: '9', quantity: 5, selected: false }], total: 40, totalQuantity: 45, selectedQuantity: 20 })
  await p.load()
  assert.equal(p.data.allSelected, false)
  api.request = async () => ({ items: [{ id: '1', quantity: 20, selected: true }], invalidItems: [{ id: '9', quantity: 5, selected: false }], total: 40, totalQuantity: 45, selectedQuantity: 40 })
  await p.load()
  assert.equal(p.data.allSelected, true)
})

test('selecting all deselects invalid entries so they cannot enter checkout', async () => {
  const p = page('cart'), calls = []
  p.data.cart = { items: [], invalidItems: [{ id: '9007199254740993', quantity: 1, selected: true }] }
  api.request = async (path, method, body) => {
    calls.push({ path, method, body })
    if (!method) return { items: [], invalidItems: [], total: 0, totalQuantity: 0, selectedQuantity: 0 }
  }
  await p.all()
  assert.equal(calls[0].path, '/cart/selection'); assert.equal(calls[0].body.selected, true)
  assert.equal(calls[1].path, '/cart/items/9007199254740993/selection'); assert.equal(calls[1].body.selected, false)
})

test('a lost checkout response survives reopening the page and retries the original order snapshot', async () => {
  const p = page('checkout'), calls = []
  p.onLoad(); p.data.address = { id: '9007199254740993', receiver: 'Customer' }; p.data.quote = { orderNo: 'ORDER-1', checkoutToken: 'signed-token', payAmount: '10.00' }; p.data.remark = 'Original remark'; p.couponId = '5'
  api.request = async (path, method, body) => { calls.push(JSON.parse(JSON.stringify(body))); throw Object.assign(new Error('timeout'), { uncertain: true }) }
  await p.submit()
  assert.equal(p.data.pending, true); assert.equal(checkout.pending().body.orderNo, 'ORDER-1')
  const reopened = page('checkout'); reopened.onLoad(); await reopened.onShow()
  reopened.data.remark = 'Changed accidentally'; reopened.couponId = '9'
  api.request = async (path, method, body) => { calls.push(JSON.parse(JSON.stringify(body))); return { id: '123' } }
  await reopened.submit()
  assert.deepEqual(calls[0], calls[1]); assert.equal(checkout.pending(), null)
})

test('missing delivery rules explain recovery and never leave a stale quote usable for submission', async () => {
  const p = page('checkout'), calls = []
  p.onLoad(); p.data.address = { id: '9007199254740993' }; p.data.quote = { orderNo: 'OLD', checkoutToken: 'old-token' }
  api.request = async (path, method, body) => {
    calls.push({ path, method, body })
    throw Object.assign(new Error('配送服务未配置该地区规则，暂不能结算'), { status: 400, code: 400001 })
  }
  await p.preview()
  assert.equal(p.data.quote, null); assert.equal(p.data.busy, false)
  assert.match(p.data.error, /配送服务未配置/); assert.match(p.data.notice, /联系商家.*配送范围和运费/)
  assert.equal(calls[0].body.addressId, '9007199254740993')
  await p.submit()
  assert.equal(calls.length, 1); assert.equal(checkout.pending(), null)
  api.request = async () => ({ orderNo: 'NEW', checkoutToken: 'new-token' })
  await p.preview()
  assert.equal(p.data.quote.orderNo, 'NEW'); assert.equal(p.data.error, ''); assert.equal(p.data.notice, '')
})

test('unreachable addresses and invalid regions keep distinct checkout recovery instructions', async () => {
  const p = page('checkout')
  p.onLoad(); p.data.address = { id: '4' }
  for (const [message, guidance] of [['该地址不在配送范围内', /更换可配送/], ['请补全当前有效的省市区编码', /重新选择并保存省市区/], ['商品库存不足', /返回购物车/]]) {
    api.request = async () => { throw Object.assign(new Error(message), { status: 400, code: 400001 }) }
    await p.preview()
    assert.equal(p.data.error, message); assert.match(p.data.notice, guidance); assert.equal(p.data.quote, null)
  }
})

test('pending checkout snapshots are discarded when another consumer logs in', () => {
  checkout.save({ orderNo: 'ORDER-1' }, { id: '1' }, {})
  api.save({ accessToken: 'another-customer', expiresIn: 3600 })
  assert.equal(checkout.pending(), null)
})

test('in-progress checkout conflicts retain the old ticket until the server permits a fresh preview', async () => {
  const p = page('checkout')
  p.onLoad(); p.data.address = { id: '1' }; p.data.quote = { orderNo: 'ORDER-2', checkoutToken: 'token' }
  api.request = async () => { throw Object.assign(new Error('订单提交或资源释放处理中，请稍后使用同一Token查询或重试'), { status: 409, code: 409001 }) }
  await p.submit()
  assert.equal(p.data.pending, true); assert.equal(checkout.pending().body.orderNo, 'ORDER-2')
  api.request = async () => { throw Object.assign(new Error('本次提交已失败并释放资源，请重新预览取得新Token'), { status: 409, code: 409001 }) }
  await p.submit()
  assert.equal(p.data.pending, false); assert.equal(checkout.pending(), null); assert.equal(p.data.quote, null)
})

test('WeChat refunds use the dedicated server endpoint and only become complete after server confirmation', async () => {
  const p = page('manage'), m = modules.find(m => m.key === 'aftersales'), calls = []
  p.data.current = m; p.data.profile = { permissions: ['aftersale:read', 'aftersale:write'] }; p.data.detail = { id: '12', status: 'approved', amount: '15.00' }
  p.showDetail(p.data.detail)
  await p.action(event({ key: 'wechat-refund' }))
  admin.request = async (path, method) => {
    calls.push({ path, method })
    if (method === 'POST') throw Object.assign(new Error('微信退款处理中，请稍后点击查询退款结果'), { status: 400 })
    return { id: '12', status: 'completed' }
  }
  await p.submit()
  assert.equal(p.data.detail.status, 'approved'); assert.equal(calls[0].path, '/aftersales/12/wechat-refund'); assert.equal(calls[0].method, 'POST')
  admin.request = async () => ({ id: '12', status: 'completed' })
  await p.submit()
  assert.equal(p.data.detail.status, 'completed')
})

test('required selectors include an explicit empty option and preserve their actual selected index', () => {
  const fields = prepareFields(modules[0].fields, { categoryId: '9007199254740993' }, { categories: [{ id: '9007199254740993', parentId: '0', name: 'Stoves' }] }, 'products')
  const category = fields.find(f => f.key === 'categoryId')
  assert.equal(category.choices[0].id, ''); assert.equal(category.choices[category.selectedIndex].id, '9007199254740993')
})

test('paid orders reject a forged cancellation action on the client', async () => {
  const p = page('order')
  p.id = '1'; p.data.order = { status: 'paid' }
  api.request = async () => assert.fail('paid order cancellation must not be sent')
  await p.action(event({ action: 'cancel' }))
})

test('consumer aftersale detail includes translated operations even when opened from the list', async () => {
  const p = page('aftersales')
  api.request = async path => path.endsWith('/operations') ? [{ id: '1', action: 'approve', toStatus: 'approved', operatorType: 'admin' }] : { id: '10', status: 'approved', amount: '12.00' }
  await p.detail(event({ id: '10' }))
  assert.equal(p.data.detail.label, '审核通过'); assert.equal(p.data.operations[0].label, '审核通过')
})

test('favorites map nested product cards and keep unavailable entries removable', async () => {
  const p = page('personal')
  p.onLoad({ tab: 'favorites' })
  api.request = async () => ({ list: [{ productId: '9007199254740993', time: '2026-11-10', unavailable: false, product: { name: 'Stove', price: '12', coverUrl: '/stove.jpg' } }, { productId: '2', unavailable: true, product: null }], total: 2, pages: 1 })
  await p.load()
  assert.equal(p.data.rows[0].id, '9007199254740993'); assert.equal(p.data.rows[0].name, 'Stove'); assert.equal(p.data.rows[1].unavailable, true)
})

test('consumer profile update sends only whitelisted profile fields', async () => {
  const p = page('personal'), calls = []
  p.data.ready = true; p.data.user = { id: '1' }; p.data.form = { nickname: 'Me', email: '', avatar: '', gender: 'unknown', birthday: '', status: 'active', points: 9999 }
  api.request = async (path, method, body) => { calls.push(body) }
  await p.save()
  assert.equal(calls.length, 1); assert.equal(calls[0].points, undefined); assert.equal(calls[0].status, undefined)
})

test('employee creation wraps employee DTO separately from its initial password', async () => {
  const p = page('manage'), m = modules.find(m => m.key === 'employees'), calls = []
  p.data.current = m; p.data.profile = { permissions: ['employee:read', 'employee:write'] }; p.data.canWrite = true
  admin.request = async (path, method, body) => {
    if (path === '/employees/options') return { departments: [], positions: [], roles: [] }
    if (path === '/auth/me') return { permissions: ['employee:read', 'employee:write'] }
    if (method === 'POST') { calls.push(body); return { id: '1' } }
    return { list: [], total: 0, pages: 1 }
  }
  await p.edit(event({ create: 'yes' }))
  p.data.form = { username: 'staff01', name: 'Employee', phone: '13800138000', status: 'active', password: 'InitialPwd123', departmentId: '', positionId: '' }
  await p.submit()
  assert.equal(calls.length, 1); assert.equal(calls[0].employee.username, 'staff01'); assert.equal(calls[0].employee.password, undefined); assert.equal(calls[0].password, 'InitialPwd123')
})

test('an unreadable privacy profile cannot be overwritten with default settings', async () => {
  const p = page('personal')
  p.onLoad({ tab: 'privacy' })
  let calls = 0
  api.request = async () => { calls++; throw new Error('unavailable') }
  await p.load(); await p.save()
  assert.equal(calls, 1); assert.equal(p.data.ready, false)
})
