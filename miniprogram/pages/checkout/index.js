const api = require('../../utils/api')
Page({
  data: { address: null, quote: null, coupons: [], couponIndex: 0, busy: false, error: '', notice: '' },
  onLoad() { this.couponId = null },
  async onShow() {
    if (!api.requireLogin()) return
    try {
      const addresses = await api.request('/users/me/addresses')
      const chosen = getApp().globalData.selectedAddress
      const address = addresses.find(a => chosen && a.id === chosen.id) || addresses.find(a => a.isDefault) || addresses[0] || null
      this.setData({ address, quote: null, error: '' })
      if (address) await this.preview()
    } catch (e) { this.setData({ error: e.message }) }
  },
  address() { wx.navigateTo({ url: '/pages/addresses/index?choose=1' }) },
  async preview() {
    if (!this.data.address) return
    this.setData({ quote: null })
    const quote = await api.request('/orders/preview', 'POST', { addressId: this.data.address.id, userCouponId: this.couponId })
    this.setData({ quote })
  },
  async coupons() {
    try {
      // Page through the ledger; the backend is authoritative for eligibility and price.
      let page = 1, pages = 1, list = []
      do { const r = await api.request('/user-coupons?status=unused&page=' + page + '&size=100'); list = list.concat(r.list); pages = r.pages; page++ } while (page <= pages)
      wx.showActionSheet({ itemList: ['不使用优惠券'].concat(list.slice(0, 5).map(c => c.name)), success: async r => {
        this.couponId = r.tapIndex === 0 ? null : list[r.tapIndex - 1].id
        try { await this.preview() } catch (e) { this.setData({ error: e.message }) }
      } })
    } catch (e) { this.setData({ error: e.message }) }
  },
  async submit() {
    if (this.data.busy || !this.data.quote) return
    this.setData({ busy: true, error: '' })
    try {
      const quote = this.data.quote
      // Keep the same orderNo/token after timeout so a retry cannot create a second order.
      const order = await api.request('/orders', 'POST', { addressId: this.data.address.id, userCouponId: this.couponId, orderNo: quote.orderNo, checkoutToken: quote.checkoutToken })
      wx.redirectTo({ url: '/pages/order/index?id=' + order.id })
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
})
