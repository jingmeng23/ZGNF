const api = require('../../utils/api')
Page({
  data: { rows: [], addresses: [], types: ['燃气安装', '维修服务', '安全检查'], typeIndex: 0, addressIndex: 0, title: '', description: '', date: '', time: '', error: '', busy: false, page: 1, pages: 1 },
  onLoad() { this.requestId = api.id() },
  onShow() { if (api.requireLogin()) this.load() },
  async load() { try { const r = await api.request('/work-orders?page=' + this.data.page + '&size=10'); const addresses = await api.request('/users/me/addresses'); this.setData({ rows: r.list, pages: r.pages, addresses, error: '' }) } catch (e) { this.setData({ error: e.message }) } },
  input(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  async submit() {
    if (this.data.busy) return
    const address = this.data.addresses[Number(this.data.addressIndex)]
    if (!address || !this.data.date || !this.data.time) return this.setData({ error: '请选择服务地址与预约时间' })
    this.setData({ busy: true, error: '' })
    try {
      await api.request('/work-orders', 'POST', { type: ['installation', 'repair', 'inspection'][Number(this.data.typeIndex)], title: this.data.title, description: this.data.description, addressId: address.id, appointmentTime: this.data.date + ' ' + this.data.time + ':00', requestId: this.requestId })
      this.requestId = api.id(); this.setData({ title: '', description: '' }); wx.showToast({ title: '预约已提交' }); await this.load()
    } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  async detail(e) {
    try { const id = e.currentTarget.dataset.id; const r = await api.request('/work-orders/' + id); const logs = await api.request('/work-orders/' + id + '/logs?page=1&size=100'); wx.showModal({ title: r.workOrderNo, content: r.description + '\n' + (r.result || '') + '\n' + logs.list.map(l => l.createTime + ' ' + l.content).join('\n'), showCancel: false }) }
    catch (e) { this.setData({ error: e.message }) }
  },
  cancel(e) { const id = e.currentTarget.dataset.id; wx.showModal({ title: '取消服务预约', editable: true, placeholderText: '请输入取消原因', success: async r => { if (!r.confirm || !r.content) return; try { await api.request('/work-orders/' + id + '/cancel', 'PUT', { remark: r.content }); await this.load() } catch (e) { this.setData({ error: e.message }) } } }) },
  addresses() { wx.navigateTo({ url: '/pages/addresses/index' }) },
  previous() { if (this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.load() } },
})
