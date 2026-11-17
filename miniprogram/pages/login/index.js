const api = require('../../utils/api')
Page({
  data: {
    phone: '', password: '', confirmPassword: '', nickname: '',
    registering: false, passwordVisible: false, focusedField: '',
    busy: false, action: '', error: '', errorField: '', message: '',
    authenticated: false, bound: false,
  },
  onShow() {
    const current = api.session()
    const authenticated = !!current
    const token = current ? current.token : ''
    if (token !== this._token) {
      this._token = token
      this.setData({ authenticated, bound: false, error: '', errorField: '', message: '', password: '', confirmPassword: '', passwordVisible: false })
    }
  },
  onHide() { this.setData({ passwordVisible: false }) },
  onUnload() {
    this._disposed = true
    this.data.password = ''; this.data.confirmPassword = ''
  },
  input(e) {
    if (this.data.busy) return
    const field = e.currentTarget.dataset.field
    if (!['phone', 'password', 'confirmPassword', 'nickname'].includes(field)) return
    const value = field === 'phone' ? e.detail.value.trim() : e.detail.value
    this.setData({ [field]: value, error: '', errorField: '', message: '' })
  },
  focus(e) { this.setData({ focusedField: e.currentTarget.dataset.field }) },
  blur() { this.setData({ focusedField: '' }) },
  clearPhone() {
    if (!this.data.busy) this.setData({ phone: '', error: '', errorField: '', message: '' })
  },
  togglePassword() {
    if (!this.data.busy) this.setData({ passwordVisible: !this.data.passwordVisible })
  },
  toggle() {
    if (this.data.busy) return
    this.setData({ registering: !this.data.registering, password: '', confirmPassword: '', passwordVisible: false, error: '', errorField: '', message: '', focusedField: '' })
  },
  invalid(errorField, error) { this.setData({ errorField, error, message: '' }) },
  async submit() {
    if (this.data.busy || this.data.authenticated || this._navigating) return
    const { phone, password, confirmPassword, registering } = this.data
    if (!/^1[3-9]\d{9}$/.test(phone)) return this.invalid('phone', '请输入正确的 11 位手机号')
    if (password.length < 8 || password.length > 64) return this.invalid('password', '密码长度为 8–64 位')
    if (registering && (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/\d/.test(password) || /[\r\n]/.test(password))) {
      return this.invalid('password', '请使用同时包含大写字母、小写字母和数字的密码')
    }
    if (registering && password !== confirmPassword) return this.invalid('confirmPassword', '两次输入的密码不一致，请重新确认')
    this.setData({ busy: true, action: 'password', error: '', errorField: '', message: '' })
    try {
      if (registering) {
        await api.request('/users/register', 'POST', { phone, password, nickname: this.data.nickname.trim() || null })
        if (this._disposed) return
        this.setData({ registering: false, password: '', confirmPassword: '', nickname: '', passwordVisible: false, message: '注册成功，请使用刚设置的密码登录' })
        return
      }
      const result = await api.request('/users/login/password', 'POST', { account: phone, password })
      if (this._disposed) return
      api.save(result)
      this.loginSuccess()
    } catch (e) {
      if (!this._disposed) this.setData({ error: e.message || '操作未完成，请稍后重试' })
    } finally {
      if (!this._disposed) this.setData({ busy: false, action: '' })
    }
  },
  async wechat() {
    if (this.data.busy || this.data.bound || this._navigating) return
    const current = api.session()
    if (this.data.authenticated && !current) {
      this.setData({ authenticated: false, bound: false, error: '登录已过期，请重新登录后绑定微信' })
      return
    }
    this.setData({ busy: true, action: 'wechat', error: '', errorField: '', message: '' })
    try {
      const code = await api.code()
      if (this._disposed) return
      const latest = api.session()
      if ((current && (!latest || current.token !== latest.token)) || (!current && latest)) throw new Error('登录状态已变化，请重新打开页面后操作')
      if (current) {
        await api.request('/users/me/wechat-binding', 'POST', { code })
        if (this._disposed) return
        if (!api.session() || api.session().token !== current.token) throw new Error('登录状态已变化，请重新登录确认绑定结果')
        this.setData({ bound: true, message: '微信绑定成功，下次可直接使用微信登录' })
      } else {
        const result = await api.request('/auth/wechat/login', 'POST', { code })
        if (this._disposed) return
        api.save(result)
        this.loginSuccess()
      }
    } catch (e) {
      if (!this._disposed) this.setData({ authenticated: !!api.session(), bound: false, error: e.message || '微信操作未完成，请重试' })
    } finally {
      if (!this._disposed) this.setData({ busy: false, action: '' })
    }
  },
  loginSuccess() {
    this.setData({ authenticated: true, password: '', confirmPassword: '', passwordVisible: false })
    wx.showToast({ title: '登录成功', icon: 'success' })
    this.done()
  },
  done() {
    if (this._navigating) return
    this._navigating = true
    const failed = () => {
      this._navigating = false
      if (!this._disposed) this.setData({ error: '页面暂时无法返回，请点击下方按钮重试' })
    }
    const home = () => wx.switchTab({ url: '/pages/account/index', fail: failed })
    if (getCurrentPages().length > 1) wx.navigateBack({ delta: 1, fail: home })
    else home()
  },
  browse() {
    if (this.data.busy || this._navigating) return
    this._navigating = true
    wx.switchTab({ url: '/pages/home/index', fail: () => { this._navigating = false; if (!this._disposed) this.setData({ error: '页面打开失败，请重试' }) } })
  },
  help() {
    wx.showModal({
      title: '登录遇到问题',
      content: '商城网站与小程序共用账号，请使用原手机号和密码。微信登录需先登录商城账号，再在“我的 → 账户设置 → 绑定当前微信”中完成绑定。忘记密码或手机号已停用，请通过商家已有联系渠道咨询处理。',
      showCancel: false, confirmText: '我知道了', confirmColor: '#163F35',
    })
  },
})
