const api = require('../../utils/api')
Page({
  data: { cart: null, error: '', busy: false, authenticated: false, page: 1 },
  onShow() { this.load() },
  async load() {
    const authenticated = !!api.session(); this.setData({ authenticated, error: '' })
    if (!authenticated) { this.setData({ cart: null }); return }
    try { const cart = await api.request('/cart?page=' + this.data.page + '&size=20'); cart.items = cart.items.map(p => Object.assign({}, p, { image: api.image(p.coverUrl) })); this.setData({ cart }) }
    catch (e) { this.setData({ error: e.message }) }
  },
  login() { api.requireLogin() },
  async update(e) {
    if (this.data.busy) return
    this.setData({ busy: true, error: '' })
    const { id, action, quantity, selected } = e.currentTarget.dataset
    try {
      if (action === 'remove') await api.request('/cart/items/' + id, 'DELETE')
      else if (action === 'select') await api.request('/cart/items/' + id + '/selection', 'PUT', { selected: !selected })
      else await api.request('/cart/items/' + id, 'PUT', { quantity: Number(quantity) + (action === 'plus' ? 1 : -1) }, { 'Idempotency-Key': api.id() })
      await this.load()
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  async all() { if (this.data.busy) return; this.setData({ busy: true }); try { await api.request('/cart/selection', 'PUT', { selected: true }); await this.load() } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) } },
  checkout() { if (api.requireLogin()) wx.navigateTo({ url: '/pages/checkout/index' }) },
  previous() { if (this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (this.data.page * 20 < this.data.cart.total) { this.setData({ page: this.data.page + 1 }); this.load() } },
})
