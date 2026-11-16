const { test, beforeEach, afterEach } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const api = require('../utils/api')
const { page, event, storage } = require('./helpers.cjs')
const request = api.request
let values, visits
beforeEach(() => {
  values = storage()
  visits = []
  wx.navigateTo = options => { visits.push(options.url); if (options.complete) options.complete() }
})
afterEach(() => { api.request = request })
function login(token = 'customer') { api.save({ accessToken: token, expiresIn: 3600 }) }
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

test('guest can view account without a request or forced login; protected entries request login', async () => {
  let requests = 0
  api.request = async () => { requests++ }
  const account = page('account')
  await account.onShow()
  assert.equal(account.data.authenticated, false)
  assert.equal(requests, 0)
  assert.equal(visits.length, 0)
  account.open(event({ key: 'pending' }))
  assert.equal(visits.pop(), '/pages/login/index')
  account.profile()
  assert.equal(visits.pop(), '/pages/login/index')
  account.manage()
  assert.equal(visits.pop(), '/pages/manage/index')
})

test('profile normalizes server fields and retains same-account content during refresh and failure', async () => {
  login()
  api.request = async () => ({ nickname: '购物用户', member_level: 'standard', points: 12, avatar: 'https://example.com/avatar.png' })
  const account = page('account')
  await account.onShow()
  assert.equal(account.data.user.memberLevel, 'standard')
  assert.equal(account.data.user.points, 12)
  assert.equal(account.data.avatar, 'https://example.com/avatar.png')
  const pending = deferred()
  api.request = () => pending.promise
  const refresh = account.onShow()
  assert.equal(account.data.user.nickname, '购物用户')
  assert.equal(account.data.authenticated, true)
  assert.equal(account.data.loading, true)
  pending.reject(new Error('网络暂不可用'))
  await refresh
  assert.equal(account.data.user.nickname, '购物用户')
  assert.equal(account.data.authenticated, true)
  assert.equal(account.data.loading, false)
  assert.match(account.data.error, /网络/)
  account.avatarError()
  assert.equal(account.data.avatar, '')
  api.request = async () => ({ nickname: '新昵称' })
  await account.loadProfile()
  assert.equal(account.data.user.nickname, '新昵称')
  assert.equal(account.data.error, '')
})

test('repeated shows share an in-flight profile request', async () => {
  login()
  const pending = deferred()
  let requests = 0
  api.request = () => { requests++; return pending.promise }
  const account = page('account')
  const first = account.onShow()
  await account.onShow()
  assert.equal(requests, 1)
  pending.resolve({ nickname: '用户' })
  await first
  assert.equal(account.data.loading, false)
})

test('switching accounts clears prior identity and ignores a late response', async () => {
  login('first')
  const pending = deferred()
  api.request = () => pending.promise
  const account = page('account')
  const first = account.onShow()
  login('second')
  const next = deferred()
  api.request = () => next.promise
  const second = account.onShow()
  assert.equal(account.data.user, null)
  next.resolve({ nickname: '第二个用户' })
  await second
  pending.resolve({ nickname: '第一个用户' })
  await first
  assert.equal(account.data.user.nickname, '第二个用户')
})

test('expired session clears private content instead of displaying a stale profile', async () => {
  login()
  const account = page('account')
  api.request = async () => ({ nickname: '用户' })
  await account.onShow()
  api.request = async () => { api.logout(); throw new Error('登录已失效') }
  await account.onShow()
  assert.equal(account.data.user, null)
  assert.equal(account.data.authenticated, false)
  assert.equal(account.data.loading, false)
})

test('cancelled logout preserves session and profile', async () => {
  login()
  api.request = async () => ({ nickname: '用户' })
  const account = page('account')
  await account.onShow()
  wx.showModal = options => options.success({ confirm: false })
  await account.logout()
  assert.ok(api.session())
  assert.equal(account.data.user.nickname, '用户')
  assert.equal(account.data.loggingOut, false)
})

test('confirmed logout clears consumer checkout and cannot be undone by a late profile response', async () => {
  login()
  values.set('zgnf.mini.checkout.pending', { orderId: '12' })
  values.set('zgnf.mini.admin.session', { token: 'staff', expiresAt: Date.now() + 3600000 })
  const pending = deferred()
  api.request = () => pending.promise
  const account = page('account')
  const loading = account.onShow()
  await account.logout()
  pending.resolve({ nickname: '旧用户' })
  await loading
  assert.equal(api.session(), null)
  assert.equal(account.data.user, null)
  assert.equal(account.data.authenticated, false)
  assert.equal(values.has('zgnf.mini.checkout.pending'), false)
  assert.equal(values.get('zgnf.mini.admin.session').token, 'staff')
})

test('logout dialog suppresses repeat taps and never logs out a replacement session', async () => {
  login('first')
  let modal, dialogs = 0
  wx.showModal = options => { modal = options; dialogs++ }
  const account = page('account')
  const logout = account.logout()
  await account.logout()
  assert.equal(dialogs, 1)
  login('second')
  modal.success({ confirm: true })
  await logout
  assert.equal(api.session().token, 'second')
  assert.equal(account.data.loggingOut, false)
})

test('account links preserve existing order filters, personal tabs and native routes', () => {
  login()
  const account = page('account')
  const expected = {
    orders: 'orders/index', pending: 'orders/index?status=pending_payment', paid: 'orders/index?status=paid',
    processing: 'orders/index?status=processing', aftersales: 'aftersales/index',
    favorites: 'personal/index?tab=favorites', history: 'personal/index?tab=history', coupons: 'coupons/index',
    points: 'personal/index?tab=points', addresses: 'addresses/index', services: 'services/index',
    feedback: 'personal/index?tab=feedback', profile: 'personal/index?tab=profile',
    security: 'personal/index?tab=security', privacy: 'personal/index?tab=privacy',
    logins: 'personal/index?tab=logins', wechat: 'login/index',
  }
  for (const [key, destination] of Object.entries(expected)) {
    account.open(event({ key }))
    assert.equal(visits.pop(), '/pages/' + destination)
  }
  account.open(event({ key: '../unknown' }))
  assert.equal(visits.length, 0)
})

test('rapid guest taps open one login page until navigation finishes', () => {
  let navigation
  wx.navigateTo = options => { visits.push(options.url); navigation = options }
  const account = page('account')
  account.profile()
  account.open(event({ key: 'orders' }))
  assert.equal(visits.length, 1)
  navigation.complete()
  account.profile()
  assert.equal(visits.length, 2)
})

test('all account icons are bundled PNGs so menu icons work without an asset server', () => {
  const account = page('account')
  const icons = ['user', 'settings'].concat(...['orders', 'tools', 'services'].map(key => account.data[key].map(item => item.icon)))
  for (const name of icons) {
    const bytes = fs.readFileSync(path.join(__dirname, '../assets/account', name + '.png'))
    assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a')
    assert.equal(bytes.readUInt32BE(16), 96)
    assert.equal(bytes.readUInt32BE(20), 96)
  }
})
