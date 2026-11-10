const api = require('../../utils/api')
Page({
  data: { orderId: '', reason: '', rows: [], page: 1, pages: 1, error: '', busy: false },
  onLoad(q) { this.setData({ orderId: q.orderId || '' }); this.requestId = api.id() },
  onShow() { if (api.requireLogin()) this.load() },
  input(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  async load() { try { const r = await api.request('/aftersales?page=' + this.data.page + '&size=10'); this.setData({ rows: r.list, pages: r.pages, error: '' }) } catch (e) { this.setData({ error: e.message }) } },
  async submit() {
    if (this.data.busy) return
    this.setData({ busy: true, error: '' })
    try { await api.request('/aftersales', 'POST', { orderId: this.data.orderId, reason: this.data.reason, requestId: this.requestId }); this.requestId = api.id(); this.setData({ reason: '' }); wx.showToast({ title: '申请已提交' }); await this.load() }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  async cancel(e) { try { await api.request('/aftersales/' + e.currentTarget.dataset.id + '/cancel', 'PUT'); await this.load() } catch (e) { this.setData({ error: e.message }) } },
  previous() { if (this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.load() } },
})
