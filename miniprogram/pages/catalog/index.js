const api = require('../../utils/api')
const { categories, query, pageCount } = require('../../utils/business')
const emptyFilters = () => ({ brandId: '', brandLabel: '全部品牌', minPrice: '', maxPrice: '', inStock: false })
function priceError(values) {
  for (const value of [values.minPrice, values.maxPrice]) {
    if (value && !/^\d{1,8}(\.\d{1,2})?$/.test(value)) return '价格须为有效金额，最多两位小数'
  }
  return values.minPrice && values.maxPrice && Number(values.minPrice) > Number(values.maxPrice) ? '最低价格不能高于最高价格' : ''
}

Page({
  data: {
    products: [], skeletons: [1, 2, 3, 4], keyword: '', searchKeyword: '', page: 1, pages: 1, total: 0,
    error: '', moreError: '', ready: false, loading: false, loadingMore: false, hasMore: false,
    optionsLoading: false, optionsError: '', showFilters: false, filterError: '', filterCount: 0,
    categories: [{ id: '', name: '全部分类' }], categoryShortcuts: [{ id: '', name: '全部' }], subcategories: [],
    rootId: '', categoryId: '', categoryLabel: '全部分类', categoryScrollTarget: 'root-all',
    brands: [{ id: '', name: '全部品牌' }], brandIndex: 0,
    brandId: '', brandLabel: '全部品牌', minPrice: '', maxPrice: '', inStock: false, filterDraft: emptyFilters(),
    sortIndex: 0,
    sorts: [
      { name: '综合', sort: 'comprehensive', order: 'desc' },
      { name: '销量', sort: 'sales', order: 'desc' },
      { name: '价格从低到高', sort: 'price', order: 'asc' },
      { name: '价格从高到低', sort: 'price', order: 'desc' },
      { name: '新品', sort: 'newest', order: 'desc' },
    ],
  },
  onLoad() { this.options(); if (!this.consumeNavigation()) return this.load() },
  onShow() { this._navigating = false; this.consumeNavigation() },
  onUnload() {
    this.generation = (this.generation || 0) + 1
    this.optionGeneration = (this.optionGeneration || 0) + 1
  },
  async onPullDownRefresh() {
    try { await Promise.allSettled([this.load(), this.data.optionsError ? this.options() : Promise.resolve()]) }
    finally { wx.stopPullDownRefresh() }
  },
  onReachBottom() { return this.loadMore() },
  consumeNavigation() {
    const app = getApp()
    const value = app.globalData && app.globalData.catalogQuery
    if (value == null) return false
    delete app.globalData.catalogQuery
    const params = Object.create(null)
    String(value).split('&').filter(Boolean).forEach(pair => {
      const index = pair.indexOf('=')
      if (index < 0) return
      try { params[decodeURIComponent(pair.slice(0, index))] = decodeURIComponent(pair.slice(index + 1)) } catch (_) { /* Ignore malformed navigation. */ }
    })
    const categoryId = /^[1-9]\d*$/.test(params.categoryId || '') ? params.categoryId : ''
    const keyword = (params.keyword || '').trim().slice(0, 100)
    this.setData(Object.assign(emptyFilters(), { keyword, searchKeyword: keyword, categoryId, sortIndex: 0, showFilters: false, filterCount: 0, filterError: '' }))
    this.syncCategory()
    this.load()
    return true
  },
  async options() {
    const generation = this.optionGeneration = (this.optionGeneration || 0) + 1
    this.setData({ optionsLoading: true, optionsError: '' })
    const results = await Promise.allSettled([api.request('/categories/tree'), api.request('/mall/brands')])
    if (generation !== this.optionGeneration) return
    const errors = []
    if (results[0].status === 'fulfilled') {
      const flat = categories(results[0].value).map(item => Object.assign({}, item, { shortName: item.name.split(' / ').pop() }))
      this.setData({ categories: [{ id: '', name: '全部分类' }].concat(flat), categoryShortcuts: [{ id: '', name: '全部' }].concat(flat.filter(item => item.depth === 0)) })
      this.syncCategory()
    } else errors.push('分类')
    if (results[1].status === 'fulfilled' && Array.isArray(results[1].value)) {
      const brands = [{ id: '', name: '全部品牌' }].concat(results[1].value.map(item => ({ id: String(item.id), name: item.name })))
      this.setData({ brands, brandIndex: Math.max(0, brands.findIndex(item => item.id === this.data.filterDraft.brandId)) })
    } else errors.push('品牌')
    this.setData({ optionsLoading: false, optionsError: errors.length ? errors.join('、') + '暂时加载失败，商品仍可浏览' : '' })
  },
  syncCategory() {
    const byId = new Map(this.data.categories.map(item => [item.id, item]))
    const selected = byId.get(this.data.categoryId)
    let root = selected
    while (root && root.depth > 0) root = byId.get(root.parentId)
    const rootId = root && root.id || ''
    const descendants = rootId ? this.data.categories.filter(item => {
      if (!item.depth) return false
      let ancestor = item
      while (ancestor && ancestor.depth > 0) ancestor = byId.get(ancestor.parentId)
      return ancestor && ancestor.id === rootId
    }) : []
    this.setData({
      rootId, categoryLabel: selected ? selected.name : this.data.categoryId ? '已选分类' : '全部分类',
      categoryScrollTarget: 'root-' + (rootId || 'all'),
      subcategories: descendants.length ? [{ id: rootId, name: '全部' }].concat(descendants.map(item => ({ id: item.id, name: item.name.slice(root.name.length + 3) }))) : [],
    })
  },
  input(e) { this.setData({ keyword: e.detail.value }) },
  search() {
    const keyword = this.data.keyword.trim().slice(0, 100)
    this.setData({ keyword, searchKeyword: keyword })
    return this.load()
  },
  clearSearch() { this.setData({ keyword: '', searchKeyword: '' }); return this.load() },
  quickCategory(e) {
    const id = String(e.currentTarget.dataset.id || '')
    if (id === this.data.categoryId || !this.data.categoryShortcuts.concat(this.data.subcategories).some(item => item.id === id)) return
    this.setData({ categoryId: id })
    this.syncCategory()
    return this.load()
  },
  chooseSort(e) {
    const key = e.currentTarget.dataset.key
    const index = key === 'price' ? (this.data.sortIndex === 2 ? 3 : 2) : Number(key)
    if (!this.data.sorts[index] || index === this.data.sortIndex) return
    this.setData({ sortIndex: index })
    return this.load()
  },
  toggleFilters() {
    if (this.data.showFilters) { this.closeFilters(); return }
    const d = this.data
    this.setData({ showFilters: true, filterError: '', brandIndex: Math.max(0, d.brands.findIndex(item => item.id === d.brandId)), filterDraft: { brandId: d.brandId, brandLabel: d.brandLabel, minPrice: d.minPrice, maxPrice: d.maxPrice, inStock: d.inStock } })
  },
  closeFilters() { this.setData({ showFilters: false, filterError: '' }) },
  preventScroll() {},
  draftInput(e) {
    const key = e.currentTarget.dataset.key
    if (['minPrice', 'maxPrice'].includes(key)) this.setData({ ['filterDraft.' + key]: e.detail.value, filterError: '' })
  },
  draftBrand(e) {
    const index = Number(e.detail.value), brand = this.data.brands[index]
    if (brand) this.setData({ brandIndex: index, 'filterDraft.brandId': brand.id, 'filterDraft.brandLabel': brand.name })
  },
  draftStock(e) { this.setData({ 'filterDraft.inStock': !!e.detail.value }) },
  resetDraft() { this.setData({ filterDraft: emptyFilters(), brandIndex: 0, filterError: '' }) },
  applyFilters() {
    const draft = Object.assign({}, this.data.filterDraft, { minPrice: this.data.filterDraft.minPrice.trim(), maxPrice: this.data.filterDraft.maxPrice.trim() })
    const error = priceError(draft)
    if (error) { this.setData({ filterError: error }); return }
    this.setData(Object.assign(draft, { showFilters: false, filterError: '', filterCount: Number(!!draft.brandId) + Number(!!(draft.minPrice || draft.maxPrice)) + Number(draft.inStock) }))
    return this.load()
  },
  reset() {
    this.setData(Object.assign(emptyFilters(), { keyword: '', searchKeyword: '', categoryId: '', sortIndex: 0, filterCount: 0, showFilters: false }))
    this.syncCategory()
    return this.load()
  },
  load(options = {}) {
    const d = this.data, append = options.append === true
    const error = priceError(d)
    if (error) { this.setData({ error }); return }
    const sort = d.sorts[d.sortIndex] || d.sorts[0]
    const filters = { size: 12, keyword: d.searchKeyword, categoryId: d.categoryId, brandId: d.brandId, minPrice: d.minPrice, maxPrice: d.maxPrice, inStock: d.inStock, sort: sort.sort, order: sort.order }
    const signature = query(filters), targetPage = append ? d.page + 1 : 1
    const loadingKey = signature + '&page=' + targetPage
    if (this._loadingKey === loadingKey) return this._loadPromise
    const changed = signature !== this._signature
    const generation = this.generation = (this.generation || 0) + 1
    this._signature = signature
    this._loadingKey = loadingKey
    this.setData({ loading: true, loadingMore: append, error: '', moreError: '' })
    if (changed) {
      this.setData({ products: [], total: 0, ready: false, hasMore: false, page: 1, pages: 1 })
      if (typeof wx.pageScrollTo === 'function') wx.pageScrollTo({ scrollTop: 0, duration: 0 })
    }
    this._loadPromise = this.fetchProducts({ filters, append, generation, targetPage, previous: this.data.products.slice() })
    return this._loadPromise
  },
  async fetchProducts({ filters, append, generation, targetPage, previous }) {
    try {
      const response = await api.request('/mall/search?' + query(Object.assign({ page: targetPage }, filters)))
      if (generation !== this.generation) return
      const result = response.results
      const rows = (result.list || []).map(item => Object.assign({}, item, {
        image: api.image(item.coverUrl), imageFailed: false, needsSpec: Number(item.skuCount || 0) > 1,
        priceText: item.price != null && item.price !== '' && Number.isFinite(Number(item.price)) && Number(item.price) >= 0 ? Number(item.price).toFixed(2) : '',
      }))
      const seen = new Set()
      const products = (append ? previous.concat(rows) : rows).filter(item => {
        const id = String(item.id)
        if (seen.has(id)) return false
        seen.add(id)
        return true
      })
      const pages = Math.min(1000, pageCount(result, 12))
      this.setData({ products, page: targetPage, pages, total: Number(result.total || 0), ready: true, hasMore: targetPage < pages && rows.length > 0 })
    } catch (e) {
      if (generation === this.generation) this.setData(append ? { moreError: e.message || '加载失败，请重试' } : { error: e.message || '商品暂时加载失败，请重试' })
    } finally {
      if (generation === this.generation) { this._loadingKey = ''; this._loadPromise = null; this.setData({ loading: false, loadingMore: false }) }
    }
  },
  retry() { if (!this.data.loading) return this.load() },
  loadMore() { if (!this.data.loading && this.data.hasMore && !this.data.error) return this.load({ append: true }) },
  product(e) {
    const id = String(e.currentTarget.dataset.id || '')
    if (!this.data.products.some(item => String(item.id) === id) || this._navigating) return
    this._navigating = true
    wx.navigateTo({ url: '/pages/product/index?id=' + encodeURIComponent(id), complete: () => { this._navigating = false } })
  },
  imageError(e) {
    const index = this.data.products.findIndex(item => String(item.id) === String(e.currentTarget.dataset.id))
    if (index >= 0) { const products = this.data.products.slice(); products[index] = Object.assign({}, products[index], { imageFailed: true }); this.setData({ products }) }
  },
})
