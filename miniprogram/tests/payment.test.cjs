const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const api = require('../utils/api')
const { pay } = require('../utils/payment')

beforeEach(() => {
  const storage = new Map()
  global.wx = {
    getStorageSync: key => storage.get(key), setStorageSync: (key, value) => storage.set(key, value), removeStorageSync: key => storage.delete(key),
  }
})
test('request uses the mall access token and clears it on rejected authentication', async () => {
  api.save({ accessToken: 'token', expiresIn: 3600 })
  wx.request = options => { assert.equal(options.header.Authorization, 'Bearer token'); options.success({ statusCode: 401, data: { code: 401001, message: '登录过期' } }) }
  await assert.rejects(api.request('/orders'), /登录过期/)
  assert.equal(api.session(), null)
})
test('an expired session is never sent to the backend', () => {
  api.save({ accessToken: 'token', expiresIn: 1 })
  const realNow = Date.now
  Date.now = () => realNow() + 5000
  try { assert.equal(api.session(), null) } finally { Date.now = realNow }
})
test('successful cashier does not make an unconfirmed server payment successful', async () => {
  const realRequest = api.request, realTimeout = global.setTimeout
  const requests = []
  api.request = async (path, method, data) => {
    requests.push({ path, method, data })
    return path.endsWith('/prepay') ? { paymentId: '123', payment: { package: 'prepay_id=test', signType: 'RSA' } } : { status: 'pending' }
  }
  wx.requestPayment = options => options.success({})
  global.setTimeout = callback => { callback(); return 0 }
  try {
    const result = await pay('9007199254740993')
    assert.equal(result.paid, false)
    assert.match(result.message, /确认中/)
    assert.equal(requests[0].data.orderId, '9007199254740993')
    assert.equal(requests.filter(r => r.path.endsWith('/sync')).length, 5)
  } finally { api.request = realRequest; global.setTimeout = realTimeout }
})
test('cancelled cashier still checks a settlement that raced cancellation', async () => {
  const realRequest = api.request
  api.request = async path => path.endsWith('/prepay') ? { paymentId: '123', payment: {} } : { status: 'success' }
  wx.requestPayment = options => options.fail({ errMsg: 'requestPayment:fail cancel' })
  try { assert.equal((await pay('1')).paid, true) } finally { api.request = realRequest }
})
test('cancel without a server settlement leaves the order unpaid', async () => {
  const realRequest = api.request
  api.request = async path => path.endsWith('/prepay') ? { paymentId: '123', payment: {} } : { status: 'pending' }
  wx.requestPayment = options => options.fail({ errMsg: 'requestPayment:fail cancel' })
  try { const result = await pay('1'); assert.equal(result.paid, false); assert.match(result.message, /取消/) } finally { api.request = realRequest }
})
test('cashier never opens when merchant prepay is unavailable', async () => {
  const realRequest = api.request
  api.request = async () => { throw new Error('微信支付尚未启用') }
  wx.requestPayment = () => assert.fail('cashier must not open')
  try { await assert.rejects(pay('1'), /未启用/) } finally { api.request = realRequest }
})
test('relative media uses the configured public image origin and rejects protocol-relative URLs', () => {
  assert.equal(api.image('/images/a.jpg'), require('../config').assetBase.replace(/\/$/, '') + '/images/a.jpg')
  assert.equal(api.image('//attacker/a.jpg'), '')
  assert.equal(api.image('javascript:alert(1)'), '')
})
