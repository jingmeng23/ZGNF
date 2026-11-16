const api = require('../../utils/api')
const { normalize, confirm } = require('../../utils/business')

// Labels and destinations stay together; no extra requests for invented badges.
const orders = [
  { key: 'pending', name: '待付款', icon: 'wallet', page: 'orders', status: 'pending_payment' },
  { key: 'paid', name: '已付款', icon: 'box', page: 'orders', status: 'paid' },
  { key: 'processing', name: '进行中', icon: 'truck', page: 'orders', status: 'processing' },
  { key: 'aftersales', name: '退款/售后', icon: 'aftersales', page: 'aftersales' },
]
const tools = [
  { key: 'favorites', name: '商品收藏', icon: 'heart', page: 'personal', tab: 'favorites' },
  { key: 'history', name: '浏览记录', icon: 'clock', page: 'personal', tab: 'history' },
  { key: 'coupons', name: '优惠券', icon: 'ticket', page: 'coupons' },
  { key: 'points', name: '积分明细', icon: 'points', page: 'personal', tab: 'points' },
]
const services = [
  { key: 'addresses', name: '收货地址', icon: 'pin', page: 'addresses' },
  { key: 'services', name: '服务预约', icon: 'calendar', page: 'services' },
  { key: 'feedback', name: '意见反馈', icon: 'message', page: 'personal', tab: 'feedback' },
]
const settings = [
  { key: 'profile', name: '个人资料', page: 'personal', tab: 'profile' },
  { key: 'security', name: '修改密码', page: 'personal', tab: 'security' },
  { key: 'privacy', name: '隐私设置', page: 'personal', tab: 'privacy' },
  { key: 'logins', name: '登录记录', page: 'personal', tab: 'logins' },
  { key: 'wechat', name: '绑定当前微信', page: 'login' },
]
const destinations = orders.concat(tools, services, settings, [{ key: 'orders', page: 'orders' }])

Page({
  data: {
    user: null, authenticated: false, loading: false, error: '', avatar: '',
    settingsOpen: false, loggingOut: false, orders, tools, services, settings,
  },
  onShow() { this._navigating = false; return this.loadProfile() },
  onUnload() { this._requestId = (this._requestId || 0) + 1 },
  resetAccount() {
    this._requestId = (this._requestId || 0) + 1
    this._token = ''; this._loadingToken = ''
    this.setData({ user: null, authenticated: false, loading: false, error: '', avatar: '', settingsOpen: false })
  },
  async loadProfile() {
    const current = api.session()
    if (!current) { this.resetAccount(); return }
    const token = current.token
    if (this._loadingToken === token) return
    if (this._token !== token) this.setData({ user: null, avatar: '', settingsOpen: false })
    this._token = token
    this._loadingToken = token
    const requestId = this._requestId = (this._requestId || 0) + 1
    this.setData({ authenticated: true, loading: true, error: '' })
    try {
      const user = normalize(await api.request('/users/me'))
      if (requestId !== this._requestId) return
      if (!api.session() || api.session().token !== token) { this.resetAccount(); return }
      if (!user) throw new Error('暂时无法读取个人资料，请重试')
      this.setData({ user, avatar: api.image(user.avatar) })
    } catch (e) {
      if (requestId !== this._requestId) return
      if (!api.session() || api.session().token !== token) { this.resetAccount(); return }
      this.setData({ error: e.message || '个人资料加载失败，请重试' })
    } finally {
      if (requestId === this._requestId) { this._loadingToken = ''; this.setData({ loading: false }) }
    }
  },
  avatarError() { this.setData({ avatar: '' }) },
  open(e) {
    const target = destinations.find(item => item.key === e.currentTarget.dataset.key)
    if (!target || !this.ensureLogin()) return
    const query = target.tab ? '?tab=' + encodeURIComponent(target.tab) : target.status ? '?status=' + encodeURIComponent(target.status) : ''
    this.navigate('/pages/' + target.page + '/index' + query)
  },
  ensureLogin() {
    if (api.session()) return true
    this.navigate('/pages/login/index')
    return false
  },
  profile() { if (this.ensureLogin()) this.navigate('/pages/personal/index?tab=profile') },
  navigate(url) {
    if (this._navigating) return
    this._navigating = true
    wx.navigateTo({ url, complete: () => { this._navigating = false } })
  },
  toggleSettings() { this.setData({ settingsOpen: !this.data.settingsOpen }) },
  manage() { this.navigate('/pages/manage/index') },
  async logout() {
    if (this.data.loggingOut) return
    const current = api.session()
    if (!current) { this.resetAccount(); return }
    this.setData({ loggingOut: true })
    try {
      if (!await confirm('退出登录', '退出后仍可浏览商品，查看订单等操作需要重新登录。确定退出吗？')) return
      if (!api.session() || api.session().token !== current.token) return
      api.logout()
      this.resetAccount()
      wx.showToast({ title: '已退出登录', icon: 'none' })
    } finally { this.setData({ loggingOut: false }) }
  },
})
