const api = require('./api')
async function pay(orderId, onStatus) {
  const prepared = await api.request('/payments/wechat/prepay', 'POST', { orderId })
  let cancelled = false
  try {
    await new Promise((resolve, reject) => wx.requestPayment(Object.assign({}, prepared.payment, { success: resolve, fail: reject })))
  } catch (error) {
    cancelled = /cancel/i.test(error.errMsg || '')
    // Even a cancelled/interrupted client operation may race a real settlement. Server results are authoritative.
  }
  for (let attempt = 0; attempt < 5; attempt++) {
    const payment = await api.request('/payments/wechat/' + prepared.paymentId + '/sync', 'POST')
    if (onStatus) onStatus(payment)
    if (payment.status === 'success') return { paid: true }
    if (payment.status === 'refunded') return { paid: false, message: '支付已退款，请查看订单' }
    if (cancelled) return { paid: false, message: '已取消收银台，可在订单中继续付款' }
    await new Promise(resolve => setTimeout(resolve, 1500))
  }
  return { paid: false, message: '支付结果确认中，请稍后刷新订单；请勿重复付款' }
}
module.exports = { pay }
