const api = require('./api')
const KEY = 'zgnf.mini.checkout.pending'
function pending() {
  const current = api.session(), value = wx.getStorageSync(KEY)
  if (current && value && value.owner === current.token && value.body && value.body.orderNo) return value
  wx.removeStorageSync(KEY)
  return null
}
function save(body, address, quote) {
  const current = api.session()
  if (!current) throw new Error('请重新登录后提交订单')
  const value = { owner: current.token, body: Object.assign({}, body), address, quote }
  wx.setStorageSync(KEY, value)
  return value
}
function clear() { wx.removeStorageSync(KEY) }
function retryable(error) {
  if (error.uncertain || !error.status || error.status >= 500) return true
  // The existing server uses one conflict code for in-progress and terminal submissions.
  // Only an explicit instruction to obtain a fresh quote permits abandoning the old ticket.
  return error.code === 409001 && !/重新预览|重新结算|不能保存订单/.test(error.message)
}
module.exports = { pending, save, clear, retryable }
