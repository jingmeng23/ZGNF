const api = require('../../utils/api')
Page({
  data: { rows: [], mine: false, error: '', page: 1, pages: 1, busy: false },
  onShow() { if (api.requireLogin()) this.load() },
  async load() { try { const r = await api.request((this.data.mine ? '/user-coupons' : '/coupons') + '?page=' + this.data.page + '&size=20'); this.setData({ rows: r.list, pages: r.pages, error: '' }) } catch (e) { this.setData({ error: e.message }) } },
  toggle() { this.setData({ mine: !this.data.mine, page: 1 }); this.load() },
  async claim(e) { if (this.data.busy) return; this.setData({ busy: true }); try { await api.request('/coupons/' + e.currentTarget.dataset.id + '/claim', 'POST'); wx.showToast({ title: '领取成功' }); await this.load() } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) } },
  previous() { if (this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.load() } },
})
