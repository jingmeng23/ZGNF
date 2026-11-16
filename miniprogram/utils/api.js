const config = require('../config')
// These existing catalog images ship with the mini-program, so they also work
// when the optional local asset server is stopped.
const bundledImages = {
  '/images/products/pepsi.png': '/assets/products/pepsi.png',
  '/images/products/唯怡.jpg': '/assets/products/weiyi.jpg',
}
const KEY = 'zgnf.mini.session'
function session() {
  const value = wx.getStorageSync(KEY)
  if (value && value.token && value.expiresAt > Date.now()) return value
  wx.removeStorageSync(KEY)
  return null
}
function save(result) {
  if (!result.accessToken || !(result.expiresIn > 0)) throw new Error('登录返回数据无效')
  wx.setStorageSync(KEY, { token: result.accessToken, expiresAt: Date.now() + result.expiresIn * 1000 })
}
function logout() { wx.removeStorageSync(KEY); wx.removeStorageSync('zgnf.mini.checkout.pending') }
function request(path, method = 'GET', data, headers = {}) {
  const current = session()
  return new Promise((resolve, reject) => wx.request({
    url: config.apiBase.replace(/\/$/, '') + path, method, data, timeout: 15000,
    header: Object.assign({ 'Content-Type': 'application/json', Accept: 'application/json' }, current ? { Authorization: 'Bearer ' + current.token } : {}, headers),
    success(res) {
      if (res.statusCode === 401 && session() && current && session().token === current.token) logout()
      if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === 200) resolve(res.data.data)
      else { const error = new Error((res.data && res.data.message) || '服务暂时不可用，请稍后重试'); error.status = res.statusCode; error.code = res.data && res.data.code; reject(error) }
    },
    fail() { const error = new Error('无法连接商城，请检查网络后重试'); error.uncertain = method !== 'GET'; reject(error) },
  }))
}
function requireLogin() {
  if (session()) return true
  wx.navigateTo({ url: '/pages/login/index' })
  return false
}
function code() { return new Promise((resolve, reject) => wx.login({ success: r => r.code ? resolve(r.code) : reject(new Error('微信登录凭证获取失败')), fail: () => reject(new Error('微信登录失败')) })) }
function id() { return Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 14) }
function image(value) {
  if (!value) return ''
  if (Object.prototype.hasOwnProperty.call(bundledImages, value)) return bundledImages[value]
  if (value.startsWith('/api/v1/media/products/')) return config.apiBase.replace(/\/$/, '') + value.slice('/api/v1'.length)
  if (/^https?:\/\/[^\s]+$/.test(value)) return value
  if (/^\/(?!\/)/.test(value)) return config.assetBase.replace(/\/$/, '') + value
  return ''
}
const labels = { pending_payment: '待付款', paid: '已付款', processing: '配送或处理中', completed: '已完成', cancelled: '已取消', refunded: '已退款' }
module.exports = { request, session, save, logout, requireLogin, code, id, image, labels }
