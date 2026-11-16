const api = require('../../utils/api')
const { normalize, label, confirm, pageCount } = require('../../utils/business')
const tabs = [
  { key: 'profile', title: '个人资料' }, { key: 'security', title: '修改密码' }, { key: 'favorites', title: '商品收藏' }, { key: 'history', title: '浏览记录' },
  { key: 'points', title: '积分明细' }, { key: 'privacy', title: '隐私设置' }, { key: 'logins', title: '登录记录' }, { key: 'feedback', title: '意见反馈' },
]
Page({
  data: { tabs, tab: 'profile', title: '个人资料', user: null, form: {}, rows: [], page: 1, pages: 1, total: 0, busy: false, ready: false, error: '', notice: '', genders: [{ id: 'unknown', name: '未设置' }, { id: 'male', name: '男' }, { id: 'female', name: '女' }], genderIndex: 0, genderLabel: '未设置', profileAuthorized: true, pushEnabled: true },
  onLoad(q) { const tab = tabs.find(t => t.key === q.tab) || tabs[0]; this.setData({ tab: tab.key, title: tab.title }) },
  onShow() { if (!this.data.busy && api.requireLogin()) this.load() },
  async load() {
    this.setData({ busy: true, ready: false, error: '', rows: [] })
    try {
      const tab = this.data.tab
      if (tab === 'profile') {
        const user = normalize(await api.request('/users/me'))
        const form = { nickname: user.nickname || '', avatar: user.avatar || '', email: user.email || '', gender: user.gender || 'unknown', birthday: user.birthday || '' }
        const genderIndex = Math.max(0, this.data.genders.findIndex(g => g.id === form.gender))
        this.setData({ user, form, genderIndex, genderLabel: this.data.genders[genderIndex].name })
      } else if (tab === 'privacy') {
        const value = normalize(await api.request('/users/me/privacy'))
        this.setData({ profileAuthorized: !!value.profileAuthorized, pushEnabled: !!value.pushEnabled })
      } else if (['favorites', 'history'].includes(tab)) {
        const r = await api.request('/mall/me/' + tab + '?page=' + this.data.page + '&size=10')
        const pages = pageCount(r)
        if (this.data.page > pages) { this.setData({ page: pages }); await this.load(); return }
        this.setData({ rows: r.list.map(entry => Object.assign({}, entry.product || {}, { id: entry.productId, time: entry.time, unavailable: entry.unavailable, image: api.image(entry.product && entry.product.coverUrl), name: entry.product ? entry.product.name : '商品已下架或暂不可用' })), pages, total: r.total })
      } else if (['points', 'logins'].includes(tab)) {
        const all = normalize(await api.request(tab === 'points' ? '/users/me/points' : '/users/me/security/logins'))
        const pages = Math.max(1, Math.ceil(all.length / 10)), page = Math.min(this.data.page, pages)
        this.ledger = all
        this.setData({ page, pages, total: all.length, rows: all.slice((page - 1) * 10, page * 10).map(r => Object.assign({}, r, { typeLabel: label(r.loginType) })) })
      }
      this.setData({ ready: true })
    } catch (e) { this.setData({ error: e.message }); if (e.status === 401) this.setData({ user: null, form: {} }) }
    finally { this.setData({ busy: false }) }
  },
  async changeTab(e) {
    if (this.data.busy) return
    const tab = tabs.find(t => t.key === e.currentTarget.dataset.key)
    if (!tab) return
    this.setData({ tab: tab.key, title: tab.title, page: 1, pages: 1, rows: [], form: {}, error: '', notice: '' }); await this.load()
  },
  input(e) {
    if (this.data.busy) return
    const key = e.currentTarget.dataset.key
    if (['nickname', 'avatar', 'email', 'currentPassword', 'newPassword', 'confirmPassword', 'content', 'contact'].includes(key)) this.setData({ form: Object.assign({}, this.data.form, { [key]: e.detail.value }) })
  },
  pick(e) {
    if (this.data.busy) return
    const key = e.currentTarget.dataset.key
    if (key === 'birthday') this.setData({ form: Object.assign({}, this.data.form, { birthday: e.detail.value }) })
    if (key === 'gender') { const genderIndex = Number(e.detail.value), gender = this.data.genders[genderIndex]; this.setData({ genderIndex, genderLabel: gender.name, form: Object.assign({}, this.data.form, { gender: gender.id }) }) }
  },
  birthdayClear() { if (!this.data.busy) this.setData({ form: Object.assign({}, this.data.form, { birthday: '' }) }) },
  privacy(e) { if (!this.data.busy && ['profileAuthorized', 'pushEnabled'].includes(e.currentTarget.dataset.key)) this.setData({ [e.currentTarget.dataset.key]: e.detail.value }) },
  async save() {
    if (this.data.busy || !this.data.ready) return
    const tab = this.data.tab, form = this.data.form
    let path, method = 'PUT', body
    try {
      if (tab === 'profile') {
        const email = (form.email || '').trim(), nickname = (form.nickname || '').trim(), avatar = (form.avatar || '').trim()
        if (nickname.length > 100 || email.length > 320 || (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))) throw new Error('请填写有效昵称和邮箱')
        if (avatar && !api.image(avatar)) throw new Error('头像须使用 HTTP(S) 地址或站内图片路径')
        if (form.birthday && form.birthday > new Date().toISOString().slice(0, 10)) throw new Error('生日不能晚于今天')
        path = '/users/me'; body = { nickname: nickname || null, email: email || null, avatar: avatar || null, gender: form.gender, birthday: form.birthday || null }
      } else if (tab === 'security') {
        if (!form.currentPassword) throw new Error('请填写原密码')
        if (!form.newPassword || form.newPassword.length < 8 || form.newPassword.length > 64 || !/[A-Z]/.test(form.newPassword) || !/[a-z]/.test(form.newPassword) || !/\d/.test(form.newPassword)) throw new Error('新密码须为 8–64 位，并包含大小写字母和数字')
        if (form.newPassword !== form.confirmPassword) throw new Error('两次输入的新密码不一致')
        path = '/users/me/password'; body = { currentPassword: form.currentPassword, newPassword: form.newPassword }
      } else if (tab === 'privacy') {
        path = '/users/me/privacy'; body = { profileAuthorized: this.data.profileAuthorized, pushEnabled: this.data.pushEnabled }
      } else if (tab === 'feedback') {
        if (!(form.content || '').trim()) throw new Error('请填写反馈内容')
        path = '/users/me/feedback'; method = 'POST'; body = { content: form.content.trim(), contact: (form.contact || '').trim() || null }
      } else return
    } catch (e) { this.setData({ error: e.message }); return }
    this.setData({ busy: true, error: '', notice: '' })
    try {
      await api.request(path, method, body)
      this.setData({ notice: tab === 'feedback' ? '反馈已提交' : '保存成功' })
      if (tab === 'security' || tab === 'feedback') this.setData({ form: {} })
      wx.showToast({ title: '保存成功' })
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  product(e) { const row = this.data.rows.find(r => String(r.id) === String(e.currentTarget.dataset.id)); if (row && !row.unavailable) wx.navigateTo({ url: '/pages/product/index?id=' + encodeURIComponent(row.id) }) },
  async remove(e) {
    if (this.data.busy || !['favorites', 'history'].includes(this.data.tab)) return
    this.setData({ busy: true })
    const yes = await confirm(this.data.tab === 'favorites' ? '取消收藏' : '删除浏览记录', '确认移除这条记录？')
    if (!yes) { this.setData({ busy: false }); return }
    try { await api.request('/mall/me/' + this.data.tab + '/' + encodeURIComponent(e.currentTarget.dataset.id), 'DELETE'); await this.load() }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  async clearHistory() {
    if (this.data.busy || this.data.tab !== 'history') return
    this.setData({ busy: true })
    if (!await confirm('清空浏览记录', '将删除全部浏览记录，确认继续？')) { this.setData({ busy: false }); return }
    try { await api.request('/mall/me/history', 'DELETE'); this.setData({ page: 1 }); await this.load() }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  turnPage(e) {
    if (this.data.busy) return
    const page = this.data.page + Number(e.currentTarget.dataset.delta)
    if (page < 1 || page > this.data.pages) return
    this.setData({ page })
    if (['points', 'logins'].includes(this.data.tab)) this.setData({ rows: this.ledger.slice((page - 1) * 10, page * 10).map(r => Object.assign({}, r, { typeLabel: label(r.loginType) })) })
    else this.load()
  },
})
