const api = require('../../utils/api')
const { confirm } = require('../../utils/business')
const blank = () => ({ receiver: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false })
Page({
  data: { rows: [], form: blank(), editing: false, choosing: false, region: [], error: '', busy: false, loading: false },
  onLoad(q) { this.setData({ choosing: q.choose === '1' }) },
  onShow() { if (!this.data.busy && api.requireLogin()) this.load() },
  async load() {
    this.setData({ loading: true, error: '' })
    try { this.setData({ rows: await api.request('/users/me/addresses') }) }
    catch (e) { this.setData({ error: e.message }); if (e.status === 401) this.setData({ rows: [], editing: false }) }
    finally { this.setData({ loading: false }) }
  },
  input(e) { if (!this.data.busy) this.setData({ form: Object.assign({}, this.data.form, { [e.currentTarget.dataset.field]: e.detail.value }) }) },
  region(e) {
    if (this.data.busy) return
    const values = e.detail.value, codes = e.detail.code || []
    this.setData({ region: values, form: Object.assign({}, this.data.form, { province: values[0], city: values[1], district: values[2], provinceCode: codes[0] || null, cityCode: codes[1] || null, districtCode: codes[2] || null, latitude: null, longitude: null, adcode: null, fullAddress: null }) })
  },
  add() { if (!this.data.busy) { this.editId = null; this.setData({ editing: true, form: blank(), region: [], error: '' }) } },
  edit(e) {
    if (this.data.busy || this.data.loading) return
    const row = this.data.rows.find(r => String(r.id) === String(e.currentTarget.dataset.id))
    if (!row) return
    this.editId = row.id; this.setData({ editing: true, form: Object.assign({}, row), region: [row.province, row.city, row.district], error: '' })
  },
  closeEdit() { if (!this.data.busy) this.setData({ editing: false, form: blank(), error: '' }) },
  async save() {
    if (this.data.busy || this.data.loading) return
    const form = this.data.form, body = {}
    for (const key of ['receiver', 'phone', 'province', 'city', 'district', 'detail']) body[key] = String(form[key] || '').trim()
    if (body.receiver.length < 2 || body.receiver.length > 20 || !/^1[3-9]\d{9}$/.test(body.phone) || !body.province || !body.city || !body.district || body.detail.length < 5) { this.setData({ error: '请填写 2–20 字收件人、有效手机号、完整省市区和至少 5 字的详细地址' }); return }
    for (const key of ['isDefault', 'label', 'provinceCode', 'cityCode', 'districtCode', 'latitude', 'longitude', 'fullAddress', 'adcode']) if (form[key] != null) body[key] = form[key]
    this.setData({ busy: true, error: '' })
    try { await api.request('/users/me/addresses' + (this.editId ? '/' + encodeURIComponent(this.editId) : ''), this.editId ? 'PUT' : 'POST', body); this.setData({ editing: false }); await this.load() }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  select(e) {
    if (!this.data.choosing || this.data.busy || this.data.loading) return
    const row = this.data.rows.find(r => String(r.id) === String(e.currentTarget.dataset.id))
    if (!row) return
    getApp().globalData.selectedAddress = row; wx.navigateBack()
  },
  async defaultAddress(e) {
    if (this.data.busy || this.data.loading) return
    this.setData({ busy: true, error: '' })
    try { await api.request('/users/me/addresses/' + encodeURIComponent(e.currentTarget.dataset.id) + '/default', 'PUT'); await this.load() }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  async remove(e) {
    if (this.data.busy || this.data.loading) return
    this.setData({ busy: true, error: '' })
    if (!await confirm('删除收货地址？', '删除地址簿记录不会修改已有订单的收货信息。')) { this.setData({ busy: false }); return }
    try { await api.request('/users/me/addresses/' + encodeURIComponent(e.currentTarget.dataset.id), 'DELETE'); await this.load() }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
})
