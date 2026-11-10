const api = require('../../utils/api')
Page({
  data: { user: null, error: '' },
  async onShow() {
    this.setData({ user: null, error: '' })
    if (!api.session()) return
    try { this.setData({ user: await api.request('/users/me') }) } catch (e) { this.setData({ error: e.message }) }
  },
  open(e) { if (api.requireLogin()) wx.navigateTo({ url: '/pages/' + e.currentTarget.dataset.page + '/index' }) },
  login() { wx.navigateTo({ url: '/pages/login/index' }) },
  logout() { api.logout(); this.setData({ user: null }) },
})
