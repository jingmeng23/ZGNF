const api = require('../../utils/api')
const checkout = require('../../utils/checkout')
const { pageCount } = require('../../utils/business')
Page({
  data: { address: null, quote: null, remark: '', coupon: null, couponRows: [], couponPage: 1, couponPages: 1, showCoupons: false, busy: false, error: '', notice: '', pending: false },
  onLoad() { this.couponId = null },
  async onShow() {
    if (!api.requireLogin() || this.data.busy) return
    const pending = checkout.pending()
    if (pending) {
      this.setData({ address: pending.address, quote: pending.quote, remark: pending.body.remark || '', pending: true, showCoupons: false, notice: '上次订单提交结果尚未确认，请重试原提交以查询或完成该订单。' })
      return
    }
    this.setData({ busy: true, error: '', quote: null, pending: false })
    try {
      const addresses = await api.request('/users/me/addresses'), chosen = getApp().globalData.selectedAddress
      const address = addresses.find(a => chosen && String(a.id) === String(chosen.id)) || addresses.find(a => a.isDefault) || addresses[0] || null
      this.setData({ address })
      if (address) await this.refreshQuote()
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  address() { if (!this.data.busy && !this.data.pending) wx.navigateTo({ url: '/pages/addresses/index?choose=1' }) },
  input(e) { if (!this.data.busy && !this.data.pending) this.setData({ remark: e.detail.value }) },
  async refreshQuote() {
    this.setData({ quote: null, error: '', notice: '' })
    if (!this.data.address) return
    try {
      const quote = await api.request('/orders/preview', 'POST', { addressId: this.data.address.id, userCouponId: this.couponId })
      this.setData({ quote })
    } catch (e) {
      let notice = ''
      if (/未配置.*规则/.test(e.message)) notice = '当前收货地区尚未开通配送，请联系商家确认配送范围和运费；配置完成后可点击“刷新结算信息”。'
      else if (/不在配送范围|地址不可达/.test(e.message)) notice = '请更换可配送的收货地址，或联系商家确认配送范围。'
      else if (/省市区编码|行政区|收货地址不存在/.test(e.message)) notice = '请在“收货地址”中编辑该地址，重新选择并保存省市区后再结算。'
      else if (/选择购物车商品|商品已下架|不可售|商品库存不足/.test(e.message)) notice = '请返回购物车，核对已选商品和数量后重新结算。'
      this.setData({ notice })
      throw e
    }
  },
  async preview() {
    if (this.data.busy || this.data.pending) return
    this.setData({ busy: true })
    try { await this.refreshQuote() } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  async coupons() {
    if (this.data.busy || this.data.pending) return
    this.setData({ showCoupons: true, couponPage: 1 })
    await this.loadCoupons()
  },
  async loadCoupons() {
    this.setData({ busy: true, error: '' })
    try {
      const r = await api.request('/user-coupons?status=unused&page=' + this.data.couponPage + '&size=10')
      this.setData({ couponRows: r.list, couponPages: pageCount(r) })
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  couponPage(e) {
    if (this.data.busy) return
    const page = this.data.couponPage + Number(e.currentTarget.dataset.delta)
    if (page < 1 || page > this.data.couponPages) return
    this.setData({ couponPage: page }); this.loadCoupons()
  },
  async chooseCoupon(e) {
    if (this.data.busy || this.data.pending) return
    const id = e.currentTarget.dataset.id, coupon = id ? this.data.couponRows.find(c => String(c.id) === String(id)) : null
    this.couponId = coupon ? coupon.id : null
    this.setData({ coupon, showCoupons: false, busy: true })
    try { await this.refreshQuote() } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  closeCoupons() { if (!this.data.busy) this.setData({ showCoupons: false }) },
  async submit() {
    if (this.data.busy || !this.data.quote || !api.requireLogin()) return
    this.setData({ busy: true, error: '' })
    try {
      let pending = checkout.pending()
      if (!pending) {
        const quote = this.data.quote
        pending = checkout.save({ addressId: this.data.address.id, userCouponId: this.couponId, remark: this.data.remark.trim() || null, orderNo: quote.orderNo, checkoutToken: quote.checkoutToken }, this.data.address, quote)
      }
      this.setData({ pending: true })
      const order = await api.request('/orders', 'POST', pending.body)
      checkout.clear()
      this.setData({ quote: null, pending: false, notice: '订单已创建，正在前往付款' })
      wx.redirectTo({ url: '/pages/order/index?id=' + encodeURIComponent(order.id), fail: () => wx.navigateTo({ url: '/pages/orders/index' }) })
    } catch (e) {
      const uncertain = checkout.retryable(e)
      if (!uncertain) { checkout.clear(); this.setData({ pending: false, quote: null }) }
      this.setData({ error: e.message, notice: uncertain ? '网络结果尚未确认，原订单信息已保存。重试会使用同一订单号，避免重复下单。' : '请刷新结算信息，核对库存、价格和优惠券后再次提交。' })
    } finally { this.setData({ busy: false }) }
  },
})
