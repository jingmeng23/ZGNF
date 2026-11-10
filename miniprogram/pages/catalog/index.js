const api = require('../../utils/api')
Page({
  data: { products: [], keyword: '', page: 1, pages: 1, error: '', loading: false },
  onLoad() { this.load() },
  input(e) { this.setData({ keyword: e.detail.value }) },
  search() { this.setData({ page: 1 }); this.load() },
  async load() {
    this.setData({ loading: true, error: '' })
    try { const r = await api.request('/mall/search?page=' + this.data.page + '&size=12&keyword=' + encodeURIComponent(this.data.keyword)); const result = r.results; this.setData({ products: result.list.map(p => Object.assign({}, p, { image: api.image(p.coverUrl) })), pages: result.pages }) }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ loading: false }) }
  },
  previous() { if (this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.load() } },
  product(e) { wx.navigateTo({ url: '/pages/product/index?id=' + e.currentTarget.dataset.id }) },
})
