const api = require('../../utils/api')
const blank = () => ({ receiver: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false })
Page({
  data: { rows: [], form: blank(), editing: false, region: [], error: '', busy: false },
  onLoad(q) { this.choose = q.choose === '1' },
  onShow() { if (api.requireLogin()) this.load() },
  async load() { try { this.setData({ rows: await api.request('/users/me/addresses'), error: '' }) } catch (e) { this.setData({ error: e.message }) } },
  input(e) { this.setData({ ['form.' + e.currentTarget.dataset.field]: e.detail.value }) },
  region(e) {
    const values = e.detail.value, codes = e.detail.code || []
    this.setData({ region: values, 'form.province': values[0], 'form.city': values[1], 'form.district': values[2],
      'form.provinceCode': codes[0] || null, 'form.cityCode': codes[1] || null, 'form.districtCode': codes[2] || null })
  },
  add() { this.editId = null; this.setData({ editing: true, form: blank(), region: [], error: '' }) },
  edit(e) { const row = this.data.rows.find(r => r.id === e.currentTarget.dataset.id); this.editId = row.id; this.setData({ editing: true, form: Object.assign({}, row), region: [row.province, row.city, row.district] }) },
  async save() {
    if (this.data.busy) return
    this.setData({ busy: true, error: '' })
    try {
      await api.request('/users/me/addresses' + (this.editId ? '/' + this.editId : ''), this.editId ? 'PUT' : 'POST', this.data.form)
      this.setData({ editing: false }); await this.load()
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  select(e) { if (!this.choose) return; getApp().globalData.selectedAddress = this.data.rows.find(r => r.id === e.currentTarget.dataset.id); wx.navigateBack() },
  remove(e) {
    if (this.data.busy) return
    const id = e.currentTarget.dataset.id
    wx.showModal({ title: '删除收货地址？', success: async r => { if (!r.confirm) return; try { await api.request('/users/me/addresses/' + id, 'DELETE'); await this.load() } catch (e) { this.setData({ error: e.message }) } } })
  },
})
