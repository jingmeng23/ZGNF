const api = require('../../utils/api')
Page({
  data: { phone: '', password: '', nickname: '', registering: false, busy: false, error: '', authenticated: false },
  onShow() { this.setData({ authenticated: !!api.session() }) },
  input(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  toggle() { this.setData({ registering: !this.data.registering, error: '' }) },
  async submit() {
    if (this.data.busy) return
    if (!/^1[3-9]\d{9}$/.test(this.data.phone)) return this.setData({ error: '请输入有效的11位手机号' })
    if (!this.data.password) return this.setData({ error: '请输入密码' })
    this.setData({ busy: true, error: '' })
    try {
      if (this.data.registering) {
        await api.request('/users/register', 'POST', { phone: this.data.phone, password: this.data.password, nickname: this.data.nickname || null })
        this.setData({ registering: false }); wx.showToast({ title: '注册成功，请登录', icon: 'none' }); return
      }
      const result = await api.request('/users/login/password', 'POST', { account: this.data.phone, password: this.data.password })
      api.save(result); this.setData({ authenticated: true, password: '' })
      wx.showToast({ title: '登录成功' })
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  async wechat() {
    if (this.data.busy) return
    this.setData({ busy: true, error: '' })
    try {
      const code = await api.code()
      if (api.session()) { await api.request('/users/me/wechat-binding', 'POST', { code }); wx.showToast({ title: '微信绑定成功' }) }
      else { api.save(await api.request('/auth/wechat/login', 'POST', { code })); this.setData({ authenticated: true }); wx.showToast({ title: '登录成功' }) }
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  done() { const pages = getCurrentPages(); if (pages.length > 1) wx.navigateBack(); else wx.switchTab({ url: '/pages/account/index' }) },
})
