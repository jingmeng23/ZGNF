const config = require('../config')
const KEY = 'zgnf.mini.admin.session'
function session() {
  const s = wx.getStorageSync(KEY)
  if (s && s.token && s.expiresAt > Date.now()) return s
  wx.removeStorageSync(KEY)
  return null
}
function logout() { wx.removeStorageSync(KEY) }
function request(path, method = 'GET', data) {
  const s = session()
  return new Promise((resolve, reject) => wx.request({
    url: config.apiBase.replace(/\/$/, '') + '/admin' + path,
    method, data, timeout: 15000,
    header: Object.assign({ 'Content-Type': 'application/json' }, s ? { Authorization: 'Bearer ' + s.token } : {}),
    success(r) {
      if (r.statusCode === 401 && session() && s && session().token === s.token) logout()
      if (r.statusCode >= 200 && r.statusCode < 300 && r.data && r.data.code === 200) resolve(r.data.data)
      else { const error = new Error(r.data && r.data.message || '管理操作失败，请重试'); error.status = r.statusCode; error.code = r.data && r.data.code; reject(error) }
    },
    fail() { const error = new Error('网络连接失败，请重试'); error.uncertain = method !== 'GET'; reject(error) },
  }))
}
async function login(username, password) {
  logout()
  const r = await request('/auth/login', 'POST', { username, password })
  if (!r || !r.accessToken || !(r.expiresIn > 0)) throw new Error('登录信息无效')
  wx.setStorageSync(KEY, { token: r.accessToken, expiresAt: Date.now() + r.expiresIn * 1000 })
  return r.admin
}
function uploadImage(filePath) {
  const s = session()
  return new Promise((resolve, reject) => wx.uploadFile({
    url: config.apiBase.replace(/\/$/, '') + '/admin/products/images',
    filePath, name: 'file', timeout: 60000,
    header: s ? { Authorization: 'Bearer ' + s.token } : {},
    success(r) {
      if (r.statusCode === 401 && session() && s && session().token === s.token) logout()
      let body
      try { body = JSON.parse(r.data) } catch (_) { /* Gateway errors may not be JSON. */ }
      if (r.statusCode >= 200 && r.statusCode < 300 && body && body.code === 200 && body.data && body.data.url) resolve(body.data)
      else { const error = new Error(body && body.message || '图片上传失败，请重试'); error.status = r.statusCode; reject(error) }
    },
    fail() { reject(new Error('图片上传失败，请检查网络后重试')) },
  }))
}
function can(profile, permission) { return !!(profile && Array.isArray(profile.permissions) && profile.permissions.includes(permission)) }
module.exports = { session, logout, request, uploadImage, login, can }
