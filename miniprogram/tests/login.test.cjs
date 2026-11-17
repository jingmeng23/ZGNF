const { test, beforeEach, afterEach } = require('node:test')
const assert = require('node:assert/strict')
const api = require('../utils/api')
const { page, event, storage } = require('./helpers.cjs')
const original = { request: api.request, code: api.code }
const result = { accessToken: 'customer', expiresIn: 3600 }
let visits
beforeEach(() => {
  storage()
  visits = []
  wx.switchTab = options => visits.push(options.url)
  wx.navigateBack = options => visits.push(options.delta)
})
afterEach(() => Object.assign(api, original))
function loginPage(app) {
  const p = page('login', app)
  p.onShow()
  p.input(event({ field: 'phone' }, '13800138000'))
  p.input(event({ field: 'password' }, 'Secret123'))
  return p
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

test('guest entry does not request identity and can browse without login', () => {
  api.request = () => assert.fail('guest entry must not request identity')
  const p = page('login')
  p.onShow()
  assert.equal(p.data.authenticated, false)
  p.browse(); p.browse()
  assert.deepEqual(visits, ['/pages/home/index'])
  assert.equal(api.session(), null)
})

test('invalid phone and short password stay local; corrections clear the field error', async () => {
  api.request = () => assert.fail('invalid input must not submit')
  const p = loginPage()
  p.input(event({ field: 'phone' }, '123'))
  await p.submit()
  assert.equal(p.data.errorField, 'phone')
  p.input(event({ field: 'phone' }, ' 13800138000 '))
  assert.equal(p.data.error, '')
  p.input(event({ field: 'password' }, 'short'))
  await p.submit()
  assert.equal(p.data.errorField, 'password')
  p.clearPhone()
  assert.equal(p.data.phone, '')
})

test('password login keeps password bytes, persists a valid token and returns to the source page', async () => {
  const p = loginPage({ pages: [{ route: 'pages/cart/index' }, { route: 'pages/login/index' }] })
  p.input(event({ field: 'password' }, ' Secret123 '))
  api.request = async (path, method, data) => {
    assert.equal(path, '/users/login/password'); assert.equal(method, 'POST')
    assert.equal(data.account, '13800138000'); assert.equal(data.password, ' Secret123 ')
    return result
  }
  await p.submit()
  assert.equal(api.session().token, 'customer')
  assert.equal(p.data.password, '')
  assert.equal(p.data.passwordVisible, false)
  assert.deepEqual(visits, [1])
})

test('a failed back navigation falls back to my account and a failed fallback can be retried', async () => {
  const p = loginPage({ pages: [{}, {}] })
  wx.navigateBack = options => options.fail()
  wx.switchTab = options => { visits.push(options.url); options.fail() }
  api.request = async () => result
  await p.submit()
  assert.equal(p.data.authenticated, true)
  assert.match(p.data.error, /无法返回/)
  assert.equal(p._navigating, false)
  wx.switchTab = options => visits.push(options.url)
  p.done()
  assert.deepEqual(visits, ['/pages/account/index', '/pages/account/index'])
})

test('in-flight login locks both methods, fields and mode against repeated taps', async () => {
  const p = loginPage(), pending = deferred()
  let requests = 0
  api.request = () => { requests++; return pending.promise }
  api.code = () => assert.fail('another method must not start')
  const first = p.submit()
  await p.submit(); await p.wechat(); p.toggle()
  p.input(event({ field: 'phone' }, '13900139000')); p.clearPhone()
  assert.equal(p.data.phone, '13800138000')
  assert.equal(p.data.registering, false)
  assert.equal(requests, 1)
  assert.equal(p.data.action, 'password')
  pending.resolve(result); await first
  assert.equal(p.data.busy, false)
  assert.deepEqual(visits, ['/pages/account/index'])
})

test('server and malformed login responses never fabricate a successful session and allow retry', async () => {
  const p = loginPage()
  api.request = async () => { throw new Error('手机号或密码错误') }
  await p.submit()
  assert.match(p.data.error, /手机号或密码错误/)
  assert.equal(p.data.phone, '13800138000')
  assert.equal(p.data.busy, false)
  assert.equal(api.session(), null)
  api.request = async () => ({ expiresIn: 3600 })
  await p.submit()
  assert.match(p.data.error, /登录返回数据无效/)
  assert.equal(visits.length, 0)
  api.request = async () => result
  await p.submit()
  assert.equal(api.session().token, 'customer')
})

test('register enforces server password rules and matching confirmation before making a request', async () => {
  const p = loginPage()
  p.toggle()
  assert.equal(p.data.password, '')
  api.request = () => assert.fail('invalid registration must not submit')
  p.input(event({ field: 'password' }, 'weakpass1'))
  await p.submit()
  assert.equal(p.data.errorField, 'password')
  p.input(event({ field: 'password' }, 'Secret123'))
  p.input(event({ field: 'confirmPassword' }, 'Other123'))
  await p.submit()
  assert.equal(p.data.errorField, 'confirmPassword')
  p.togglePassword()
  assert.equal(p.data.passwordVisible, true)
  p.toggle()
  assert.equal(p.data.confirmPassword, '')
  assert.equal(p.data.passwordVisible, false)
  assert.equal(p.data.phone, '13800138000')
})

test('successful register returns to login with phone retained, passwords cleared and no fake login', async () => {
  const p = loginPage()
  p.toggle()
  p.input(event({ field: 'password' }, 'Secret123'))
  p.input(event({ field: 'confirmPassword' }, 'Secret123'))
  p.input(event({ field: 'nickname' }, ' 小王 '))
  api.request = async (path, method, data) => {
    assert.equal(path, '/users/register'); assert.equal(method, 'POST')
    assert.equal(data.nickname, '小王'); assert.equal(data.password, 'Secret123')
    assert.equal(data.confirmPassword, undefined)
    return { id: '1' }
  }
  await p.submit()
  assert.equal(p.data.registering, false)
  assert.equal(p.data.phone, '13800138000')
  assert.equal(p.data.password, '')
  assert.equal(p.data.confirmPassword, '')
  assert.match(p.data.message, /注册成功/)
  assert.equal(api.session(), null)
  assert.equal(visits.length, 0)
})

test('wechat login sends the one-time code to the existing backend and returns to my account', async () => {
  const p = page('login')
  p.onShow()
  api.code = async () => 'one-time-code'
  api.request = async (path, method, data) => {
    assert.equal(path, '/auth/wechat/login'); assert.equal(method, 'POST')
    assert.equal(data.code, 'one-time-code'); return result
  }
  await p.wechat()
  assert.equal(api.session().token, 'customer')
  assert.deepEqual(visits, ['/pages/account/index'])
})

test('unbound wechat or wx.login failure leaves the password form usable without a session', async () => {
  const p = loginPage()
  api.code = async () => { throw new Error('微信登录失败') }
  api.request = () => assert.fail('missing code must not submit')
  await p.wechat()
  assert.match(p.data.error, /微信登录失败/)
  assert.equal(p.data.busy, false)
  api.code = async () => 'code'
  api.request = async () => { throw new Error('请先使用商城账号登录并绑定微信') }
  await p.wechat()
  assert.match(p.data.error, /先使用商城账号/)
  assert.equal(p.data.authenticated, false)
  assert.equal(p.data.phone, '13800138000')
  assert.equal(api.session(), null)
})

test('binding requires an existing session and only reports bound after server confirmation', async () => {
  api.save(result)
  const p = page('login'), pending = deferred()
  p.onShow()
  assert.equal(p.data.authenticated, true)
  assert.equal(p.data.bound, false)
  api.code = async () => 'bind-code'
  let requests = 0
  api.request = (path, method, data) => {
    requests++; assert.equal(path, '/users/me/wechat-binding')
    assert.equal(method, 'POST'); assert.equal(data.code, 'bind-code')
    return pending.promise
  }
  const binding = p.wechat()
  await Promise.resolve()
  assert.equal(p.data.bound, false)
  pending.resolve(); await binding
  assert.equal(p.data.bound, true)
  await p.wechat()
  assert.equal(requests, 1)
  assert.equal(api.session().token, 'customer')
  assert.equal(visits.length, 0)
})

test('expired binding and identity changes during wx.login do not bind another account', async () => {
  api.save(result)
  const p = page('login')
  p.onShow()
  api.logout()
  api.code = () => assert.fail('expired session must not obtain a code')
  await p.wechat()
  assert.equal(p.data.authenticated, false)
  assert.match(p.data.error, /登录已过期/)
  api.save(result); p.onShow()
  const pending = deferred()
  api.code = () => pending.promise
  api.request = () => assert.fail('changed identity must not bind')
  const binding = p.wechat()
  api.save({ ...result, accessToken: 'another-customer' })
  pending.resolve('code'); await binding
  assert.equal(p.data.bound, false)
  assert.match(p.data.error, /登录状态已变化/)
})

test('leaving during login ignores late success, does not save credentials or navigate', async () => {
  for (const method of ['submit', 'wechat']) {
    const p = loginPage(), pending = deferred()
    api.code = async () => 'code'
    api.request = () => pending.promise
    const loading = p[method]()
    await Promise.resolve()
    p.onUnload()
    p.setData = () => assert.fail('unloaded page must not receive a response')
    pending.resolve(result); await loading
    assert.equal(api.session(), null)
    assert.equal(p.data.password, '')
    assert.equal(visits.length, 0)
  }
})

test('hiding the page conceals passwords and switching accounts resets the previous binding confirmation', () => {
  const p = loginPage()
  p.togglePassword()
  assert.equal(p.data.passwordVisible, true)
  p.onHide()
  assert.equal(p.data.passwordVisible, false)
  api.save(result); p.onShow()
  p.setData({ bound: true, message: '微信绑定成功' })
  api.save({ ...result, accessToken: 'replacement' }); p.onShow()
  assert.equal(p.data.authenticated, true)
  assert.equal(p.data.bound, false)
  assert.equal(p.data.message, '')
})
