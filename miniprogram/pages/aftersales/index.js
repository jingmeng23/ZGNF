const api = require('../../utils/api')
const { label, confirm, query, pageCount, positiveId } = require('../../utils/business')
const decorate = row => Object.assign({}, row, { label: row.status === 'rejected' && row.reviewRemark === '用户撤销申请' ? '已撤销' : label(row.status) })
Page({
  data: {
    orderId: '', order: null, reason: '', rows: [], page: 1, pages: 1, total: 0, status: '', orderNo: '', error: '', notice: '', busy: false, loading: false, detail: null, operations: [], formLocked: false,
    statuses: [{ id: '', name: '全部申请' }].concat(['pending', 'approved', 'rejected', 'completed'].map(id => ({ id, name: label(id) }))),
  },
  onLoad(q) { this.setData({ orderId: positiveId(q.orderId) ? q.orderId : '', orderNo: q.orderNo || '' }); this.requestId = api.id() },
  async onShow() {
    if (!api.requireLogin() || this.data.busy) return
    if (this.data.orderId) {
      try {
        const order = await api.request('/orders/' + encodeURIComponent(this.data.orderId))
        this.setData({ order, orderNo: order.orderNo })
        if (!['paid', 'processing', 'completed'].includes(order.status)) this.setData({ orderId: '', error: '当前订单状态不支持申请退款，可查看已有售后记录' })
      } catch (e) { this.setData({ error: e.message, orderId: '' }) }
    }
    await this.load()
  },
  input(e) {
    if (this.data.busy || this.data.formLocked) return
    const key = e.currentTarget.dataset.field
    if (['reason', 'orderNo'].includes(key)) this.setData({ [key]: e.detail.value })
  },
  filter(e) { if (!this.data.loading && !this.data.busy) { this.setData({ status: e.currentTarget.dataset.status || '', page: 1 }); this.load() } },
  search() { if (!this.data.loading && !this.data.busy) { this.setData({ page: 1 }); this.load() } },
  async load() {
    this.setData({ loading: true })
    try {
      const r = await api.request('/aftersales?' + query({ page: this.data.page, size: 10, status: this.data.status, orderNo: this.data.orderNo.trim() }))
      const pages = pageCount(r)
      if (this.data.page > pages) { this.setData({ page: pages }); await this.load(); return }
      this.setData({ rows: r.list.map(decorate), pages, total: r.total })
    } catch (e) { this.setData({ error: e.message, rows: [] }) } finally { this.setData({ loading: false }) }
  },
  async submit() {
    if (this.data.busy || this.data.loading) return
    if (!positiveId(this.data.orderId) || !this.data.reason.trim()) { this.setData({ error: '请填写退款原因' }); return }
    this.setData({ busy: true, error: '' })
    const yes = await confirm('提交整单退款申请', '退款申请将交由商城审核，退款金额以该订单实付为准。')
    if (!yes) { this.setData({ busy: false }); return }
    const body = this.submittedBody || { orderId: this.data.orderId, reason: this.data.reason.trim(), requestId: this.requestId }
    this.submittedBody = body
    try {
      const detail = await api.request('/aftersales', 'POST', body)
      this.submittedBody = null; this.requestId = api.id()
      this.setData({ reason: '', orderId: '', formLocked: false, page: 1, notice: '申请已提交，可在详情中查看审核进度' })
      await this.load(); await this.fetchDetail(detail.id)
    } catch (e) {
      const uncertain = e.uncertain || !e.status || e.status >= 500
      if (!uncertain) this.submittedBody = null
      this.setData({ error: e.message, formLocked: uncertain, notice: uncertain ? '申请结果暂未确认，重试会沿用原申请编号和内容。' : '' })
    } finally { this.setData({ busy: false }) }
  },
  async cancel(e) {
    if (this.data.busy || this.data.loading) return
    const id = e.currentTarget.dataset.id
    const row = this.data.detail && String(this.data.detail.id) === String(id) ? this.data.detail : this.data.rows.find(r => String(r.id) === String(id))
    if (!row || row.status !== 'pending') return
    this.setData({ busy: true, error: '' })
    const yes = await confirm('撤销售后申请', '确认撤销这笔尚未审核的退款申请？')
    if (!yes) { this.setData({ busy: false }); return }
    try { await api.request('/aftersales/' + encodeURIComponent(id) + '/cancel', 'PUT'); await this.load(); if (this.data.detail) await this.fetchDetail(id) }
    catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  async fetchDetail(id) {
    const path = '/aftersales/' + encodeURIComponent(id), results = await Promise.allSettled([api.request(path), api.request(path + '/operations')])
    if (results[0].status === 'rejected') throw results[0].reason
    this.setData({ detail: decorate(results[0].value), operations: results[1].status === 'fulfilled' ? results[1].value.map(o => Object.assign({}, o, { label: label(o.action), statusLabel: label(o.toStatus), operatorLabel: label(o.operatorType) })) : [] })
    if (results[1].status === 'rejected') this.setData({ error: '处理记录加载失败：' + results[1].reason.message })
  },
  async detail(e) {
    if (this.data.busy || this.data.loading) return
    this.setData({ busy: true, error: '' })
    try { await this.fetchDetail(e.currentTarget.dataset.id) } catch (e) { this.setData({ error: e.message }) } finally { this.setData({ busy: false }) }
  },
  close() { if (!this.data.busy) this.setData({ detail: null, operations: [], error: '' }) },
  order() { if (this.data.detail) wx.navigateTo({ url: '/pages/order/index?id=' + encodeURIComponent(this.data.detail.orderId) }) },
  previous() { if (!this.data.busy && !this.data.loading && this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (!this.data.busy && !this.data.loading && this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.load() } },
})
