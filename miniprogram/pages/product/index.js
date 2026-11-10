const api = require('../../utils/api')
Page({
  data: { detail: null, image: '', error: '', busy: false },
  onLoad(q) { this.id = q.id; this.load() },
  async load() { try { const detail = await api.request('/mall/products/' + encodeURIComponent(this.id)); this.setData({ detail, image: api.image(detail.product.coverUrl), error: '' }) } catch (e) { this.setData({ error: e.message }) } },
  async add() {
    if (!api.requireLogin() || this.data.busy) return
    this.setData({ busy: true, error: '' })
    try { await api.request('/cart/items', 'POST', { productId: this.id, quantity: 1 }, { 'Idempotency-Key': api.id() }); wx.showToast({ title: '已加入购物车' }) }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  cart() { wx.switchTab({ url: '/pages/cart/index' }) },
})
