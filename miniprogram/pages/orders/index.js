const api = require('../../utils/api')
const { query, label, pageCount } = require('../../utils/business')
Page({
  data: { rows: [], page: 1, pages: 1, total: 0, status: '', orderNo: '', error: '', loading: false, statuses: [{ id: '', name: '全部订单' }].concat(['pending_payment', 'paid', 'processing', 'completed', 'cancelled', 'refunded'].map(id => ({ id, name: label(id) }))) },
  onLoad(q) { this.setData({ status: this.data.statuses.some(s => s.id === q.status) ? q.status : '' }) },
  onShow() { if (api.requireLogin()) this.load() },
  onUnload() { this.generation = (this.generation || 0) + 1 },
  input(e) { this.setData({ orderNo: e.detail.value }) },
  search() { if (!this.data.loading) { this.setData({ page: 1 }); this.load() } },
  async load() {
    const generation = (this.generation || 0) + 1; this.generation = generation
    this.setData({ loading: true, error: '' })
    try {
      const r = await api.request('/orders?' + query({ page: this.data.page, size: 10, status: this.data.status, orderNo: this.data.orderNo.trim() }))
      if (generation !== this.generation) return
      const pages = pageCount(r)
      if (this.data.page > pages) { this.setData({ page: pages }); await this.load(); return }
      this.setData({ rows: r.list.map(o => Object.assign({}, o, { label: label(o.status) })), pages, total: r.total })
    } catch (e) { if (generation === this.generation) this.setData({ error: e.message, rows: [] }) }
    finally { if (generation === this.generation) this.setData({ loading: false }) }
  },
  filter(e) { if (!this.data.loading) { this.setData({ status: e.currentTarget.dataset.status || '', page: 1 }); this.load() } },
  open(e) { wx.navigateTo({ url: '/pages/order/index?id=' + encodeURIComponent(e.currentTarget.dataset.id) }) },
  previous() { if (!this.data.loading && this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (!this.data.loading && this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.load() } },
})
