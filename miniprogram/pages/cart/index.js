const api = require('../../utils/api')
const { confirm, positiveId } = require('../../utils/business')
const money = value => value != null && value !== '' && Number.isFinite(Number(value)) && Number(value) >= 0 ? Number(value).toFixed(2) : ''
const viewItem = item => Object.assign({}, item, { image: api.image(item.coverUrl), priceText: money(item.unitPrice), subtotalText: money(item.subtotal), maxQuantity: Math.min(999, Number(item.availableStock) || 0) })
const hasCartLists = cart => !!cart && Array.isArray(cart.items) && Array.isArray(cart.invalidItems)

Page({
  data: {
    cart: null, error: '', notice: '', busy: false, loading: false, stale: false,
    authenticated: false, managing: false, page: 1, pages: 1, allSelected: false,
    selectedInvalid: 0, selectedTotal: 0, amountText: '', quantityValues: {}, editingQuantity: '', checkingOut: false, quantityBusy: false,
  },
  onShow() {
    this._navigating = false
    this.setData({ checkingOut: false })
    const current = api.session()
    if (!current || current.token !== this._owner || !this.data.busy) return this.load()
  },
  onUnload() { this._disposed = true; this.generation = (this.generation || 0) + 1 },
  async onPullDownRefresh() {
    try { if (!this.data.busy) await this.load() } finally { wx.stopPullDownRefresh() }
  },
  owns(token) { const current = api.session(); return !this._disposed && !!current && current.token === token },
  reset(authenticated = false) {
    this.generation = (this.generation || 0) + 1
    this._owner = ''; this._loadedPage = 0; this._loadingKey = ''; this._loadPromise = null
    this.setData({ cart: null, authenticated, loading: false, busy: false, quantityBusy: false, stale: false, managing: false, page: 1, pages: 1, allSelected: false, selectedInvalid: 0, selectedTotal: 0, amountText: '', quantityValues: {}, editingQuantity: '', checkingOut: false, error: '', notice: '' })
  },
  checkOwner(token) {
    if (this.owns(token)) return true
    if (!this._disposed && this._owner === token) {
      this.reset(!!api.session())
      if (api.session()) this.setData({ error: '登录状态已变化，请刷新购物车' })
    }
    return false
  },
  load() {
    if (this._disposed) return Promise.resolve()
    const current = api.session()
    if (!current) { this.reset(); return Promise.resolve() }
    if (this._owner && this._owner !== current.token) this.reset(true)
    const page = this.data.page, key = current.token + ':' + page
    if (this._loadingKey === key && this._loadPromise) return this._loadPromise
    const keep = this._owner === current.token && this._loadedPage === page
    this._owner = current.token
    const generation = this.generation = (this.generation || 0) + 1
    this._loadingKey = key
    this.setData({ authenticated: true, loading: true, error: '', stale: true, editingQuantity: '' })
    if (!keep) this.setData({ cart: null, allSelected: false, selectedInvalid: 0, selectedTotal: 0, amountText: '', quantityValues: {} })
    this._loadPromise = this.fetchCart(current.token, page, generation)
    return this._loadPromise
  },
  async fetchCart(token, page, generation) {
    try {
      const value = await api.request('/cart?page=' + page + '&size=20')
      if (generation !== this.generation || !this.checkOwner(token)) return
      if (!value || !Array.isArray(value.items) || !Array.isArray(value.invalidItems)) throw new Error('购物车数据暂不可用，请刷新重试')
      const pages = Math.max(1, Math.ceil(value.total / 20))
      if (page > pages) { this.setData({ page: pages }); return await this.load() }
      const cart = Object.assign({}, value, { items: value.items.map(viewItem), invalidItems: value.invalidItems.map(viewItem) })
      const validQuantity = cart.totalQuantity - cart.invalidItems.reduce((sum, item) => sum + item.quantity, 0)
      const selectedInvalid = cart.invalidItems.filter(item => item.selected)
      const quantityValues = {}
      cart.items.forEach(item => { quantityValues[item.id] = String(item.quantity) })
      this._loadedPage = page
      this.setData({ cart, pages, quantityValues, stale: false, amountText: money(cart.selectedAmount),
        selectedInvalid: selectedInvalid.length,
        selectedTotal: cart.selectedQuantity + selectedInvalid.reduce((sum, item) => sum + item.quantity, 0),
        allSelected: validQuantity > 0 && validQuantity === cart.selectedQuantity,
        managing: cart.totalQuantity ? this.data.managing : false,
      })
    } catch (error) {
      if (generation === this.generation && this.checkOwner(token)) this.setData({ error: error.message || '购物车加载失败，请重试', stale: true })
    } finally {
      if (generation === this.generation && !this._disposed) {
        this._loadingKey = ''; this._loadPromise = null; this.setData({ loading: false })
      }
    }
  },
  login() {
    if (this._navigating) return
    if (api.session()) return this.load()
    this.navigate('/pages/login/index')
  },
  navigate(url) {
    if (this._navigating) return
    this._navigating = true
    wx.navigateTo({ url, complete: () => { this._navigating = false } })
  },
  shop() {
    if (this._navigating || this.data.busy) return
    this._navigating = true
    wx.switchTab({ url: '/pages/catalog/index', complete: () => { this._navigating = false } })
  },
  product(e) { const id = e.currentTarget.dataset.id; if (positiveId(id)) this.navigate('/pages/product/index?id=' + encodeURIComponent(id)) },
  imageError(e) {
    const group = e.currentTarget.dataset.group
    if (!['items', 'invalidItems'].includes(group) || !hasCartLists(this.data.cart)) return
    const index = this.data.cart[group].findIndex(item => String(item.id) === String(e.currentTarget.dataset.id))
    if (index >= 0) this.setData({ ['cart.' + group + '[' + index + '].image']: '' })
  },
  toggleManage() { if (!this.data.busy && !this.data.loading && !this.data.checkingOut && !this.data.editingQuantity) this.setData({ managing: !this.data.managing }) },
  item(id) {
    const cart = this.data.cart
    if (!cart) return null
    if (!hasCartLists(cart)) {
      this.setData({ stale: true, editingQuantity: '', error: '购物车数据异常，请刷新后重试' })
      return null
    }
    return cart.items.find(item => String(item.id) === String(id)) || cart.invalidItems.find(item => String(item.id) === String(id))
  },
  async mutate(work, question) {
    if (this._disposed || this.data.busy || this.data.loading || this.data.stale || this.data.checkingOut || this.data.editingQuantity || !api.requireLogin()) return
    const token = api.session().token
    if (this._owner && this._owner !== token) {
      this.reset(true)
      this.setData({ error: '登录状态已变化，请刷新购物车' })
      return
    }
    this._owner = token
    this.setData({ busy: true, error: '', notice: '' })
    let error, started = false
    try {
      if (question && !await confirm(question.title, question.content)) return
      if (!this.checkOwner(token)) return
      started = true
      await work(token)
    } catch (e) { error = e }
    finally {
      if (this.checkOwner(token)) {
        // A cancelled confirmation needs no network refresh.
        if (started) await this.load()
        if (this.checkOwner(token)) {
          this.setData({ busy: false })
          if (error) this.setData({ error: (error.message || '操作未完成，请刷新重试') + (error.uncertain ? '；提交结果尚未确认，请刷新后核对。' : '') })
        }
      }
    }
  },
  async update(e) {
    if (this.data.busy || this.data.loading || this.data.stale) return
    const { id, action } = e.currentTarget.dataset, item = this.item(id)
    if (!item || !positiveId(id)) return
    if (action === 'remove') {
      const question = { title: '删除商品', content: '确认从购物车移除“' + item.productName + '”？' }
      return this.mutate(() => api.request('/cart/items/' + encodeURIComponent(id), 'DELETE'), question)
    }
    if (action === 'select') {
      if (!item.available && !item.selected) return
      return this.mutate(() => api.request('/cart/items/' + encodeURIComponent(id) + '/selection', 'PUT', { selected: !item.selected }))
    }
    if (item.available && ['plus', 'minus'].includes(action)) return this.setQuantity(item, item.quantity + (action === 'plus' ? 1 : -1))
  },
  quantityFocus(e) {
    if (this.data.busy || this.data.loading || this.data.stale || this.data.checkingOut) return
    const item = this.item(e.currentTarget.dataset.id)
    if (item && item.available) this.setData({ editingQuantity: String(item.id) })
  },
  quantityInput(e) {
    if (this.data.busy || this.data.loading || this.data.stale) return
    const id = e.currentTarget.dataset.id
    if (this.item(id)) this.setData({ ['quantityValues.' + id]: e.detail.value, editingQuantity: String(id) })
  },
  async quantity(e) {
    if (this.data.busy || this.data.loading || this.data.stale) return
    this.setData({ editingQuantity: '' })
    const item = this.item(e.currentTarget.dataset.id)
    if (item && item.available) return this.setQuantity(item, Number(e.detail.value))
  },
  async setQuantity(item, quantity) {
    if (this._disposed || this.data.busy || this.data.loading || this.data.stale || this.data.checkingOut || !item.available) return
    const max = Math.min(999, Number(item.availableStock) || 0)
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > max) {
      const error = max > 0 ? '购买数量须为 1–' + max + ' 件，已恢复原数量' : '当前商品暂无库存，请刷新购物车'
      this.setData({ ['quantityValues.' + item.id]: String(item.quantity), editingQuantity: '', error })
      wx.showToast({ title: '请按现有库存调整数量', icon: 'none' })
      return
    }
    this.setData({ ['quantityValues.' + item.id]: String(quantity), editingQuantity: '' })
    if (quantity === item.quantity) return
    if (!api.requireLogin()) return
    const token = api.session().token
    if (!this.checkOwner(token) || this._owner !== token) {
      if (!this._disposed) { this.reset(true); this.setData({ error: '登录状态已变化，请刷新购物车' }) }
      return
    }
    const generation = this.generation
    this.setData({ busy: true, quantityBusy: true, error: '', notice: '' })
    try {
      // PUT already returns authoritative totals. Do not reload/reorder the list on every tap.
      const value = await api.request('/cart/items/' + encodeURIComponent(item.id), 'PUT', { quantity }, { 'Idempotency-Key': api.id() })
      if (!this.checkOwner(token) || generation !== this.generation) return
      if (!this.patchQuantityCart(value)) await this.load()
    } catch (error) {
      if (!this.checkOwner(token) || generation !== this.generation) return
      // A timeout may have committed on the server: reconcile before permitting checkout.
      this.setData({ ['quantityValues.' + item.id]: String(item.quantity) })
      await this.load()
      if (this.checkOwner(token)) this.setData({ error: (error.message || '数量更新失败，请重试') + (error.uncertain ? '；提交结果尚未确认，请刷新后核对。' : '') })
    } finally {
      if (this.checkOwner(token)) this.setData({ busy: false, quantityBusy: false })
    }
  },
  patchQuantityCart(value) {
    const current = this.data.cart
    if (!hasCartLists(current) || !hasCartLists(value)) return false
    const items = new Map(value.items.map(item => [String(item.id), item]))
    const invalid = new Map(value.invalidItems.map(item => [String(item.id), item]))
    // Structural changes require a normal refresh, but server ordering alone must never move cards.
    if (value.total !== current.total || invalid.size !== current.invalidItems.length ||
      current.items.some(item => !items.has(String(item.id))) || current.invalidItems.some(item => !invalid.has(String(item.id)))) return false
    const patch = {}
    ;['items', 'invalidItems'].forEach(group => {
      const source = group === 'items' ? items : invalid
      current[group].forEach((old, index) => {
        const next = viewItem(source.get(String(old.id)))
        // Retain image failure placeholders and avoid setting unchanged image/list nodes.
        if (next.coverUrl === old.coverUrl) next.image = old.image
        Object.keys(next).forEach(key => {
          if (next[key] !== old[key]) patch['cart.' + group + '[' + index + '].' + key] = next[key]
        })
        if (group === 'items') patch['quantityValues.' + old.id] = String(next.quantity)
      })
    })
    ;['total', 'totalQuantity', 'selectedQuantity', 'selectedAmount'].forEach(key => { patch['cart.' + key] = value[key] })
    const selectedInvalid = value.invalidItems.filter(item => item.selected)
    const validQuantity = value.totalQuantity - value.invalidItems.reduce((sum, item) => sum + item.quantity, 0)
    Object.assign(patch, { amountText: money(value.selectedAmount), selectedInvalid: selectedInvalid.length,
      selectedTotal: value.selectedQuantity + selectedInvalid.reduce((sum, item) => sum + item.quantity, 0),
      allSelected: validQuantity > 0 && validQuantity === value.selectedQuantity })
    this.setData(patch)
    return true
  },
  all() {
    if (!this.data.cart) return
    // The existing backend atomically selects valid items across all pages and clears invalid selections.
    return this.mutate(() => api.request('/cart/selection', 'PUT', { selected: !this.data.allSelected }))
  },
  deselectInvalid() {
    if (!this.data.cart || !this.data.selectedInvalid) return
    const ids = this.data.cart.invalidItems.filter(item => item.selected).map(item => item.id)
    // Keep valid shopping selections; this action never removes products.
    return this.mutate(async token => {
      for (const id of ids) {
        if (!this.owns(token)) throw new Error('登录状态已变化，请刷新后核对')
        await api.request('/cart/items/' + encodeURIComponent(id) + '/selection', 'PUT', { selected: false })
      }
    })
  },
  batch(e) {
    const action = e.currentTarget.dataset.action, cart = this.data.cart
    if (!cart || !['selected', 'invalid', 'clear'].includes(action)) return
    if (action === 'invalid' && !cart.invalidItems.length) return
    if (action === 'selected' && !cart.selectedQuantity && !this.data.selectedInvalid) return
    const question = {
      title: action === 'clear' ? '清空购物车' : action === 'selected' ? '删除已选商品' : '清理失效商品',
      content: action === 'selected' ? '将删除所有分页中已勾选的商品（包含已选失效商品），确认继续？' : action === 'invalid' ? '将移除下架、库存不足或暂时无法查询的商品，确认继续？' : '将移除购物车中全部商品，确认继续？',
    }
    return this.mutate(async token => {
      if (action !== 'invalid') { await api.request(action === 'clear' ? '/cart' : '/cart/selected', 'DELETE'); return }
      const ids = cart.invalidItems.map(item => String(item.id))
      for (let i = 0; i < ids.length; i += 100) {
        if (!this.owns(token)) throw new Error('登录状态已变化，请刷新后核对')
        await api.request('/cart/items', 'DELETE', { ids: ids.slice(i, i + 100) })
      }
    }, question)
  },
  checkout() {
    if (this._navigating || this.data.checkingOut || this.data.busy || this.data.loading || this.data.stale || this.data.managing || this.data.editingQuantity || !hasCartLists(this.data.cart) || !this.data.cart.selectedQuantity || this.data.selectedInvalid || !this.data.amountText || !api.requireLogin()) return
    if (this._owner && !this.checkOwner(this._owner)) return
    this._navigating = true
    this.setData({ checkingOut: true })
    wx.navigateTo({
      url: '/pages/checkout/index',
      complete: () => { this._navigating = false },
      fail: () => {
        this._navigating = false
        if (this._disposed) return
        this.setData({ checkingOut: false, error: '确认订单页面打开失败，请重试' })
        wx.showToast({ title: '页面打开失败，请重试', icon: 'none' })
      },
    })
  },
  previous() { if (!this.data.busy && !this.data.loading && this.data.page > 1) { this.setData({ page: this.data.page - 1 }); return this.load() } },
  next() { if (!this.data.busy && !this.data.loading && this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); return this.load() } },
})
