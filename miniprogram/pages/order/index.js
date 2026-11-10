const api = require('../../utils/api')
const payment = require('../../utils/payment')
Page({
  data: { order: null, label: '', error: '', notice: '', busy: false },
  onLoad(q) { this.id = q.id },
  onShow() { if (api.requireLogin()) this.load() },
  async load() { try { const order = await api.request('/orders/' + encodeURIComponent(this.id)); this.setData({ order, label: api.labels[order.status] || order.status, error: '' }) } catch (e) { this.setData({ error: e.message }) } },
  async pay() {
    if (this.data.busy) return
    this.setData({ busy: true, error: '', notice: '' })
    try { const r = await payment.pay(this.id); this.setData({ notice: r.paid ? '支付成功，商城正在安排履约' : r.message }); await this.load() }
    catch (e) { this.setData({ error: e.message + '。如已扣款，请刷新订单确认，勿重复付款。' }) }
    finally { this.setData({ busy: false }) }
  },
  action(e) {
    if (this.data.busy) return
    const action = e.currentTarget.dataset.action
    wx.showModal({ title: action === 'cancel' ? '取消订单？' : '确认已收到商品或服务已完成？', content: '请核实后确认操作。', success: async r => {
      if (!r.confirm) return
      this.setData({ busy: true, error: '' })
      try { await api.request('/orders/' + this.id + '/' + action, 'PUT'); await this.load() }
      catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
    } })
  },
  refund() { wx.navigateTo({ url: '/pages/aftersales/index?orderId=' + this.id }) },
  bind() { wx.navigateTo({ url: '/pages/login/index' }) },
})
