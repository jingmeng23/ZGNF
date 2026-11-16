const api = require('../../utils/api')
const { query } = require('../../utils/business')

Page({
  data: {
    products: [], skeletons: [1, 2, 3, 4],
    bannerImage: '', bannerTarget: null,
    keyword: '', error: '', loading: false, actionError: '',
    total: 0, failedImages: {}, addBusy: '',
  },
  onLoad() { return this.loadHome() },
  onUnload() {
    this.homeGeneration = (this.homeGeneration || 0) + 1
    this.generation = (this.generation || 0) + 1
  },
  async onPullDownRefresh() {
    try { await this.loadHome() } finally { wx.stopPullDownRefresh() }
  },
  async loadHome() {
    const generation = this.homeGeneration = (this.homeGeneration || 0) + 1
    await Promise.allSettled([
      this.loadProducts(),
      api.request('/mall/home').then(floors => {
        if (generation !== this.homeGeneration) return
        const floor = (Array.isArray(floors) ? floors : []).find(item => item.type === 'banner')
        const item = floor && (floor.items || []).find(entry => typeof entry.imageUrl === 'string' && api.image(entry.imageUrl) && (entry.path === '/pages/catalog/index' || /^[1-9]\d*$/.test(String(entry.productId || ''))))
        this.setData({ bannerImage: item ? api.image(item.imageUrl) : '', bannerTarget: item || null })
      }),
    ])
    // Optional banner configuration must not hide successfully loaded products.
  },
  async loadProducts() {
    const generation = this.generation = (this.generation || 0) + 1
    this.setData({ loading: true, error: '', actionError: '' })
    try {
      const result = await api.request('/mall/search?' + query({ page: 1, size: 6, sort: 'comprehensive', order: 'desc' }))
      if (generation !== this.generation) return
      const page = result.results || {}
      this.setData({ products: (page.list || []).map(item => Object.assign({}, item, {
        image: api.image(item.coverUrl),
        priceText: item.price != null && item.price !== '' && Number.isFinite(Number(item.price)) && Number(item.price) >= 0 ? Number(item.price).toFixed(2) : '',
        needsSpec: Number(item.skuCount || 0) > 1,
      })), total: Number(page.total || 0), failedImages: {} })
    } catch (error) {
      if (generation === this.generation) this.setData({ error: error.message || '商品暂时加载失败，请重试' })
    } finally {
      if (generation === this.generation) this.setData({ loading: false })
    }
  },
  input(e) { this.setData({ keyword: e.detail.value }) },
  retryProducts() { if (!this.data.loading) return this.loadProducts() },
  search() { this.openCatalog({ keyword: this.data.keyword.trim() }) },
  catalog() { this.openCatalog({}) },
  banner() {
    const item = this.data.bannerTarget
    if (item && /^[1-9]\d*$/.test(String(item.productId || ''))) { wx.navigateTo({ url: '/pages/product/index?id=' + encodeURIComponent(item.productId) }); return }
    this.catalog()
  },
  bannerError() { this.setData({ bannerImage: '' }) },
  openCatalog(values) {
    const parts = Object.keys(values).filter(key => values[key]).map(key => encodeURIComponent(key) + '=' + encodeURIComponent(values[key]))
    const app = getApp()
    app.globalData = app.globalData || {}
    app.globalData.catalogQuery = parts.join('&')
    wx.switchTab({ url: '/pages/catalog/index' })
  },
  product(e) { wx.navigateTo({ url: '/pages/product/index?id=' + encodeURIComponent(e.currentTarget.dataset.id) }) },
  imageError(e) { this.setData({ ['failedImages.' + e.currentTarget.dataset.id]: true }) },
  buy(e) {
    const item = this.data.products.find(product => String(product.id) === String(e.currentTarget.dataset.id))
    if (!item || !item.purchasable) return
    if (Number(item.skuCount || 0) > 1) { this.product(e); return }
    return this.addProduct(e)
  },
  async addProduct(e) {
    const productId = String(e.currentTarget.dataset.id || '')
    const item = this.data.products.find(product => String(product.id) === productId)
    if (!item || !item.purchasable || Number(item.skuCount || 0) > 1 || this.data.addBusy || !api.requireLogin()) return
    const owner = api.session().token
    this.pendingAdds = this.pendingAdds || Object.create(null)
    const pending = this.pendingAdds[productId] && this.pendingAdds[productId].owner === owner ? this.pendingAdds[productId] : { key: api.id(), owner }
    this.pendingAdds[productId] = pending
    this.setData({ addBusy: productId, actionError: '' })
    try {
      await api.request('/cart/items', 'POST', { productId, quantity: 1 }, { 'Idempotency-Key': pending.key })
      delete this.pendingAdds[productId]
      wx.showToast({ title: '已加入购物车' })
    } catch (error) {
      if (!error.uncertain && error.status && error.status < 500) delete this.pendingAdds[productId]
      this.setData({ actionError: error.message || '加购失败，请重试' })
      wx.showToast({ title: '加购失败，请查看提示后重试', icon: 'none' })
    } finally { this.setData({ addBusy: '' }) }
  },
  services() { wx.navigateTo({ url: '/pages/services/index' }) },
})
