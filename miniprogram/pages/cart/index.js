const api = require('../../utils/api')
const { confirm, positiveId } = require('../../utils/business')
Page({
  data: { cart: null, error: '', notice: '', busy: false, loading: false, authenticated: false, page: 1, pages: 1, allSelected: false, selectedInvalid: 0 },
  onShow() { if (!this.data.busy) this.load() },
  onUnload() { this.generation = (this.generation || 0) + 1 },
  async load() {
    const current = api.session(), generation = (this.generation || 0) + 1; this.generation = generation
    this.setData({ authenticated: !!current, error: '' })
    if (!current) { this.setData({ cart: null, loading: false, allSelected: false }); return }
    this.setData({ loading: true })
    try {
      const cart = await api.request('/cart?page=' + this.data.page + '&size=20')
      if (generation !== this.generation || !api.session() || api.session().token !== current.token) return
      const pages = Math.max(1, Math.ceil(cart.total / 20))
      if (this.data.page > pages) { this.setData({ page: pages }); await this.load(); return }
      cart.items = (cart.items || []).map(p => Object.assign({}, p, { image: api.image(p.coverUrl) }))
      cart.invalidItems = (cart.invalidItems || []).map(p => Object.assign({}, p, { image: api.image(p.coverUrl) }))
      const validQuantity = cart.totalQuantity - cart.invalidItems.reduce((sum, p) => sum + p.quantity, 0)
      this.setData({ cart, pages, selectedInvalid: cart.invalidItems.filter(p => p.selected).length, allSelected: validQuantity > 0 && validQuantity === cart.selectedQuantity })
    } catch (e) {
      if (generation === this.generation) this.setData({ error: e.message, authenticated: !!api.session(), cart: null })
    } finally { if (generation === this.generation) this.setData({ loading: false }) }
  },
  login() { api.requireLogin() },
  product(e) { wx.navigateTo({ url: '/pages/product/index?id=' + encodeURIComponent(e.currentTarget.dataset.id) }) },
  item(id) { return this.data.cart && this.data.cart.items.concat(this.data.cart.invalidItems).find(p => String(p.id) === String(id)) },
  async mutate(work) {
    if (this.data.busy || this.data.loading || !api.requireLogin()) return
    this.setData({ busy: true, error: '', notice: '' })
    let error
    try { await work() } catch (e) { error = e }
    await this.load()
    this.setData({ busy: false })
    if (error) this.setData({ error: error.message })
  },
  async update(e) {
    if (this.data.busy || this.data.loading) return
    const { id, action } = e.currentTarget.dataset, item = this.item(id)
    if (!item || !positiveId(id)) return
    if (action === 'remove') {
      this.setData({ busy: true })
      const yes = await confirm('删除商品', '确认从购物车移除“' + item.productName + '”？')
      this.setData({ busy: false }); if (!yes) return
      await this.mutate(() => api.request('/cart/items/' + encodeURIComponent(id), 'DELETE')); return
    }
    if (action === 'select') {
      if (!item.available && !item.selected) return
      await this.mutate(() => api.request('/cart/items/' + encodeURIComponent(id) + '/selection', 'PUT', { selected: !item.selected })); return
    }
    if (!['plus', 'minus'].includes(action)) return
    await this.setQuantity(item, item.quantity + (action === 'plus' ? 1 : -1))
  },
  async quantity(e) {
    if (this.data.busy || this.data.loading) return
    const item = this.item(e.currentTarget.dataset.id)
    if (item) await this.setQuantity(item, Number(e.detail.value))
  },
  async setQuantity(item, quantity) {
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > Math.min(999, item.availableStock)) { this.setData({ error: '数量须为 1–' + Math.min(999, item.availableStock) + ' 件' }); await this.load(); this.setData({ error: '数量超出范围，请按现有库存调整' }); return }
    if (quantity === item.quantity) return
    await this.mutate(() => api.request('/cart/items/' + encodeURIComponent(item.id), 'PUT', { quantity }, { 'Idempotency-Key': api.id() }))
  },
  async all() {
    if (!this.data.cart) return
    const selected = !this.data.allSelected, invalid = this.data.cart.invalidItems
    await this.mutate(async () => {
      await api.request('/cart/selection', 'PUT', { selected })
      if (selected) {
        const results = await Promise.allSettled(invalid.map(item => api.request('/cart/items/' + encodeURIComponent(item.id) + '/selection', 'PUT', { selected: false })))
        const failed = results.find(r => r.status === 'rejected')
        if (failed) throw failed.reason
      }
    })
  },
  async batch(e) {
    if (this.data.busy || this.data.loading || !this.data.cart) return
    const action = e.currentTarget.dataset.action, cart = this.data.cart
    if (!['selected', 'invalid', 'clear'].includes(action)) return
    if (action === 'invalid' && !cart.invalidItems.length) return
    this.setData({ busy: true })
    const yes = await confirm(action === 'clear' ? '清空购物车' : action === 'selected' ? '删除已选商品' : '清理不可购买商品', action === 'selected' ? '将删除所有分页中已勾选的商品，确认继续？' : action === 'invalid' ? '将移除下架、库存不足或暂时无法查询的商品，确认继续？' : '将移除购物车中全部商品，确认继续？')
    this.setData({ busy: false }); if (!yes) return
    await this.mutate(async () => {
      if (action !== 'invalid') { await api.request(action === 'clear' ? '/cart' : '/cart/selected', 'DELETE'); return }
      const ids = cart.invalidItems.map(p => String(p.id))
      for (let i = 0; i < ids.length; i += 100) await api.request('/cart/items', 'DELETE', { ids: ids.slice(i, i + 100) })
    })
  },
  checkout() { if (!this.data.busy && !this.data.loading && this.data.cart && this.data.cart.selectedQuantity && !this.data.selectedInvalid && api.requireLogin()) wx.navigateTo({ url: '/pages/checkout/index' }) },
  previous() { if (!this.data.busy && !this.data.loading && this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (!this.data.busy && !this.data.loading && this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.load() } },
})
