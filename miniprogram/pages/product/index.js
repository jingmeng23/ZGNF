const api = require('../../utils/api')
const { positiveId } = require('../../utils/business')
Page({
  data: { detail: null, images: [], detailImages: [], attributes: [], skus: [], skuDimensions: [], selectedSpecs: {}, selectedSku: null, quantity: 1, maxQuantity: 1, favorite: false, error: '', notice: '', busy: false, loading: false, addLocked: false },
  onLoad(q) { this.id = q.id; this.load() },
  onShow() { if (this.id && api.session()) this.loadFavorite() },
  async loadFavorite() {
    try { const favorite = await api.request('/mall/me/favorites/' + encodeURIComponent(this.id)); this.setData({ favorite: !!favorite }) } catch (e) { if (e.status === 401) this.setData({ favorite: false }) }
  },
  async load() {
    if (!positiveId(this.id)) { this.setData({ error: '商品编号无效' }); return }
    this.setData({ loading: true, error: '' })
    try {
      const detail = await api.request('/mall/products/' + encodeURIComponent(this.id))
      const images = (detail.mainImages && detail.mainImages.length ? detail.mainImages : [detail.product.coverUrl]).map(api.image).filter(Boolean)
      const attributes = Object.keys(detail.attributes || {}).map(name => ({ name, value: detail.attributes[name].join('、') }))
      const skus = ((detail.matrix || {}).skus || []).map(s => Object.assign({}, s, { specification: Object.keys(s.specs || {}).map(k => k + '：' + s.specs[k]).join(' / '), image: api.image(s.imageUrl) }))
      const skuDimensions = this.dimensionOptions(detail.matrix || {}, {}, skus)
      const maxQuantity = Math.min(999, Math.max(0, Number(detail.product.availableStock) || 0))
      const skuFlowUnsupported = skus.length > 1
      this.setData({ detail, images, attributes, skus, skuDimensions, selectedSpecs: {}, selectedSku: null, detailImages: (detail.detailImages || []).map(api.image).filter(Boolean), maxQuantity, quantity: Math.max(1, Math.min(this.data.quantity, maxQuantity || 1)), notice: skuFlowUnsupported ? '该商品有多种规格。当前购物车与结算接口尚不支持保存规格选择，因此暂不能下单，避免订错规格。' : '' })
      if (api.session()) {
        await this.loadFavorite()
        try { await api.request('/mall/me/history/' + encodeURIComponent(this.id), 'POST') } catch (e) { /* Browsing remains available when history recording fails. */ }
      }
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ loading: false }) }
  },
  quantity(e) {
    if (this.data.busy || this.data.addLocked) return
    const delta = e.currentTarget.dataset.delta
    const quantity = delta == null ? Number(e.detail.value) : this.data.quantity + Number(delta)
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > this.data.maxQuantity) { this.setData({ error: '购买数量须为 1–' + this.data.maxQuantity + ' 件', quantity: this.data.quantity }); return }
    this.setData({ quantity, error: '' })
  },
  dimensionOptions(matrix, selection, skus) {
    return (matrix.dimensions || []).map(dimension => {
      const options = (dimension.options || []).map(option => {
        const available = skus.some(sku => sku.selectable && sku.specs[dimension.name] === option.value && Object.keys(selection).filter(key => key !== dimension.name).every(key => sku.specs[key] === selection[key]))
        return Object.assign({}, option, { disabled: !available, selected: selection[dimension.name] === option.value })
      })
      return { name: dimension.name, options, selectedIndex: Math.max(0, options.findIndex(option => option.selected)) }
    })
  },
  chooseSpec(e) {
    const index = Number(e.currentTarget.dataset.index), dimension = this.data.skuDimensions[index]
    if (!dimension) return
    const option = dimension.options[Number(e.detail.value)]
    if (!option || option.disabled) return
    const selected = Object.assign({}, this.data.selectedSpecs || {}, { [dimension.name]: option.value })
    const matrix = this.data.detail.matrix, sku = this.data.skus.find(item => item.selectable && Object.keys(selected).length === Object.keys(item.specs || {}).length && Object.keys(selected).every(key => item.specs[key] === selected[key])) || null
    this.setData({ selectedSpecs: selected, selectedSku: sku, skuDimensions: this.dimensionOptions(matrix, selected, this.data.skus) })
  },
  async add() {
    if (!api.requireLogin() || this.data.busy || !this.data.detail || !this.data.detail.product.purchasable) return
    if (this.data.skus.length > 1) { this.setData({ error: '当前购物车接口仅支持商品编号，尚不能保存规格编号，暂不可购买此多规格商品' }); return }
    this.setData({ busy: true, error: '' })
    const owner = api.session().token
    const pending = this.pendingAdd && this.pendingAdd.owner === owner ? this.pendingAdd : { key: api.id(), quantity: this.data.quantity, owner }
    this.pendingAdd = pending
    try {
      await api.request('/cart/items', 'POST', { productId: this.id, quantity: pending.quantity }, { 'Idempotency-Key': pending.key })
      this.pendingAdd = null; this.setData({ addLocked: false, notice: '已加入购物车，可前往购物车核对并结算' }); wx.showToast({ title: '已加入购物车' })
    } catch (e) {
      const uncertain = e.uncertain || !e.status || e.status >= 500
      if (!uncertain) this.pendingAdd = null
      this.setData({ error: e.message, addLocked: uncertain, notice: uncertain ? '加购结果暂未确认，重试会沿用原请求，避免重复加购。' : '' })
    } finally { this.setData({ busy: false }) }
  },
  async favorite() {
    if (!api.requireLogin() || this.data.busy) return
    this.setData({ busy: true, error: '' })
    try { await api.request('/mall/me/favorites/' + encodeURIComponent(this.id), this.data.favorite ? 'DELETE' : 'PUT'); this.setData({ favorite: !this.data.favorite }) }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  preview(e) { const current = e.currentTarget.dataset.url; wx.previewImage({ current, urls: this.data.images.concat(this.data.detailImages) }) },
  cart() { wx.switchTab({ url: '/pages/cart/index' }) },
})
