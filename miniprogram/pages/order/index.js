const api = require('../../utils/api')
const payment = require('../../utils/payment')
const { label, confirm, positiveId } = require('../../utils/business')
Page({
  data: { order: null, label: '', payments: [], timeline: [], error: '', notice: '', busy: false, loading: false },
  onLoad(q) { this.id = q.id },
  onShow() { if (!this.data.busy && api.requireLogin()) this.load() },
  async load() {
    if (!positiveId(this.id)) { this.setData({ error: '订单编号无效' }); return }
    this.setData({ loading: true, error: '' })
    try {
      const order = await api.request('/orders/' + encodeURIComponent(this.id))
      this.setData({ order, label: label(order.status), timeline: (order.timeline || []).map(v => Object.assign({}, v, { label: label(v.toStatus) })), payments: (order.payments || []).map(v => Object.assign({}, v, { label: label(v.status), channelLabel: label(v.channel) })) })
    } catch (e) { this.setData({ error: e.message }); if (e.status === 401) this.setData({ order: null }) }
    finally { this.setData({ loading: false }) }
  },
  async pay() {
    if (this.data.busy || this.data.loading || !this.data.order || this.data.order.status !== 'pending_payment') return
    this.setData({ busy: true, error: '', notice: '' })
    try { const r = await payment.pay(this.id); this.setData({ notice: r.paid ? '支付成功，商城正在安排履约' : r.message }); await this.load() }
    catch (e) { this.setData({ error: e.message + '。如已扣款，请刷新订单确认。' }) }
    finally { this.setData({ busy: false }) }
  },
  async action(e) {
    if (this.data.busy || this.data.loading || !this.data.order) return
    const action = e.currentTarget.dataset.action, status = this.data.order.status
    if (!((action === 'cancel' && status === 'pending_payment') || (action === 'confirm-receipt' && status === 'processing'))) return
    this.setData({ busy: true, error: '' })
    const yes = await confirm(action === 'cancel' ? '取消订单？' : '确认商品已收到 / 服务已完成？', action === 'cancel' ? '取消后将释放库存及本单占用的优惠券。' : '请核实实际收货或服务情况后确认。')
    if (!yes) { this.setData({ busy: false }); return }
    try { await api.request('/orders/' + encodeURIComponent(this.id) + '/' + action, 'PUT'); await this.load() }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  refund() { if (!this.data.busy) wx.navigateTo({ url: '/pages/aftersales/index?orderId=' + encodeURIComponent(this.id) }) },
  aftersales() { wx.navigateTo({ url: '/pages/aftersales/index?orderNo=' + encodeURIComponent(this.data.order.orderNo) }) },
  copy(e) { const value = e.currentTarget.dataset.value; if (value) wx.setClipboardData({ data: String(value) }) },
  product(e) { wx.navigateTo({ url: '/pages/product/index?id=' + encodeURIComponent(e.currentTarget.dataset.id) }) },
  bind() { wx.navigateTo({ url: '/pages/login/index' }) },
})
