const api = require('../../utils/api')
Page({
  data: { products: [], error: '', loading: false },
  onLoad() { this.load() },
  async load() {
    this.setData({ loading: true, error: '' })
    try { const result = await api.request('/mall/products?page=1&size=6'); this.setData({ products: result.list.map(p => Object.assign({}, p, { image: api.image(p.coverUrl) })) }) }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ loading: false }) }
  },
  product(e) { wx.navigateTo({ url: '/pages/product/index?id=' + e.currentTarget.dataset.id }) },
  catalog() { wx.switchTab({ url: '/pages/catalog/index' }) },
  services() { wx.navigateTo({ url: '/pages/services/index' }) },
})
