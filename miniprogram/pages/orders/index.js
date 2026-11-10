const api = require('../../utils/api')
Page({
  data: { rows: [], page: 1, pages: 1, status: '', error: '', loading: false },
  onLoad(q) { this.setData({ status: q.status || '' }) },
  onShow() { if (api.requireLogin()) this.load() },
  async load() {
    this.setData({ loading: true, error: '' })
    try { const r = await api.request('/orders?page=' + this.data.page + '&size=10' + (this.data.status ? '&status=' + this.data.status : '')); this.setData({ rows: r.list.map(o => Object.assign({}, o, { label: api.labels[o.status] || o.status })), pages: r.pages }) }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ loading: false }) }
  },
  filter(e) { this.setData({ status: e.currentTarget.dataset.status || '', page: 1 }); this.load() },
  open(e) { wx.navigateTo({ url: '/pages/order/index?id=' + e.currentTarget.dataset.id }) },
  previous() { if (this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.load() } },
})
