const admin = require('../../utils/admin')
const api = require('../../utils/api')
const business = require('../../utils/business')
const { modules, payload, f, choices, allowed, prepareFields } = require('../../utils/management')
const view = require('../../utils/management-view')
const logTypes = ['initial', 'inbound', 'outbound', 'adjustment', 'order_lock', 'order_release', 'order_deduct']
Page({
  data: {
    profile: null, modules: [], username: '', password: '', busy: false, error: '', notice: '', current: null,
    rows: [], total: 0, page: 1, pages: 1, keyword: '', filters: [], filterValues: {}, showFilters: false,
    detail: null, detailLines: [], sections: [], detailImages: [], cover: '', actions: [], canWrite: false, canRemove: false,
    fields: null, form: {}, operation: null, formReady: true, formLocked: false, records: null,
  },
  onShow() { if (!this.data.busy) this.restore() },
  onUnload() { this.generation = (this.generation || 0) + 1 },
  error(e) {
    if (e.status === 401) { this.logout(); this.setData({ error: '管理登录已过期，请重新登录' }); return }
    this.setData({ error: e.message || '操作失败，请重试' })
  },
  async refreshProfile() {
    const profile = await admin.request('/auth/me')
    const permitted = modules.filter(m => admin.can(profile, m.permission + ':read'))
    this.setData({ profile, modules: permitted })
    const m = this.data.current
    if (m && !permitted.some(v => v.key === m.key)) {
      this.setData({ current: null, rows: [], detail: null, fields: null, records: null, notice: '当前模块授权已调整' })
      return
    }
    if (m) {
      this.setData({ canWrite: admin.can(profile, m.permission + ':write') })
      if (this.data.detail) this.showDetail(this.data.detail)
      const op = this.data.operation
      if (this.data.fields && op && !admin.can(profile, op.permission || m.permission + ':write')) { this.frozenBody = null; this.setData({ fields: null, operation: null, form: {}, formLocked: false }) }
    }
  },
  async restore() {
    if (!admin.session()) { this.logout(); return }
    this.setData({ busy: true, error: '' })
    try { await this.refreshProfile() } catch (e) { this.error(e) } finally { this.setData({ busy: false }) }
  },
  input(e) { const key = e.currentTarget.dataset.field; if (['username', 'password', 'keyword'].includes(key)) this.setData({ [key]: e.detail.value }) },
  async login() {
    if (this.data.busy) return
    if (!this.data.username.trim() || !this.data.password) { this.setData({ error: '请填写员工账号和密码' }); return }
    this.setData({ busy: true, error: '' })
    try { await admin.login(this.data.username.trim(), this.data.password); this.setData({ password: '' }); await this.refreshProfile() }
    catch (e) { this.error(e) } finally { this.setData({ busy: false, password: '' }) }
  },
  logout() {
    admin.logout(); this.generation = (this.generation || 0) + 1; this.source = {}; this.frozenBody = null
    this.setData({ profile: null, modules: [], current: null, rows: [], detail: null, detailLines: [], sections: [], fields: null, form: {}, operation: null, records: null, password: '', error: '', notice: '', busy: false, canWrite: false, canRemove: false })
  },
  async sourceFor(fields) {
    const sources = new Set((fields || []).map(field => field.source).filter(Boolean)), m = this.data.current, source = {}
    const jobs = []
    if (['products', 'inventory', 'employees'].includes(m.key) && sources.size) jobs.push(admin.request('/' + m.key + '/options').then(r => Object.assign(source, r)))
    if (m.key === 'categories' && sources.has('categories')) jobs.push(admin.request('/categories/management').then(r => { source.categories = r }))
    if (sources.has('permissions')) jobs.push(admin.request('/roles/permissions').then(r => { source.permissions = r }))
    const results = await Promise.allSettled(jobs)
    const failed = results.find(r => r.status === 'rejected')
    if (failed) throw failed.reason
    this.source = Object.assign({}, this.source, source)
    return this.source
  },
  async open(e) {
    if (this.data.busy) return
    const current = modules.find(m => m.key === e.currentTarget.dataset.key)
    if (!current || !admin.can(this.data.profile, current.permission + ':read')) return
    this.source = {}; this.frozenBody = null
    const filterValues = {}
    ;(current.filters || []).forEach(field => { filterValues[field.key] = field.value })
    this.setData({ current, page: 1, pages: 1, keyword: '', detail: null, fields: null, operation: null, records: null, rows: [], filters: [], filterValues, showFilters: false, error: '', notice: '', busy: true, canWrite: admin.can(this.data.profile, current.permission + ':write') })
    try {
      const source = await this.sourceFor(current.filters)
      this.setData({ filters: prepareFields(current.filters || [], filterValues, source, current.key) })
    } catch (e) { this.error(e) } finally { this.setData({ busy: false }) }
    if (this.data.current) await this.load()
  },
  home() { if (!this.data.busy) this.setData({ current: null, detail: null, fields: null, records: null, error: '', notice: '' }) },
  toggleFilters() { this.setData({ showFilters: !this.data.showFilters }) },
  filterInput(e) {
    if (this.data.busy) return
    const key = e.currentTarget.dataset.key, field = this.data.filters.find(v => v.key === key)
    if (!field) return
    const value = field.choices ? (field.choices[Number(e.detail.value)] || {}).id : e.detail.value
    if (value == null) return
    const filterValues = Object.assign({}, this.data.filterValues, { [key]: value })
    this.setData({ filterValues, filters: prepareFields(this.data.current.filters || [], filterValues, this.source || {}, this.data.current.key) })
  },
  async resetFilters() {
    if (this.data.busy) return
    const filterValues = {}
    ;(this.data.current.filters || []).forEach(field => { filterValues[field.key] = field.value })
    this.setData({ filterValues, keyword: '', page: 1, filters: prepareFields(this.data.current.filters || [], filterValues, this.source || {}, this.data.current.key) })
    await this.load()
  },
  listQuery() {
    const m = this.data.current, filters = payload((m.filters || []).filter(v => v.type !== 'date'), this.data.filterValues)
    if (m.search && this.data.keyword.trim()) filters[m.search] = this.data.keyword.trim()
    if (m.key === 'orders') {
      const { startDate, endDate } = this.data.filterValues
      if (startDate && endDate && startDate > endDate) throw new Error('起始日期不能晚于结束日期')
      if (startDate) filters.startTime = startDate + ' 00:00:00'
      if (endDate) filters.endTime = endDate + ' 23:59:59'
    }
    return Object.assign({ page: this.data.page, size: 10 }, filters)
  },
  async load() {
    const m = this.data.current
    if (!m || !admin.can(this.data.profile, m.permission + ':read')) return
    let values
    try { values = this.listQuery() } catch (e) { this.error(e); return }
    const generation = (this.generation || 0) + 1; this.generation = generation
    this.setData({ busy: true, error: '' })
    try {
      const r = await admin.request((m.listPath || '/' + m.key) + '?' + business.query(values))
      if (generation !== this.generation) return
      let rows, total, pages
      if (m.key === 'categories') {
        rows = business.categories(r).filter(v => (!values.keyword || v.name.toLowerCase().includes(values.keyword.toLowerCase())) && (!values.status || values.status === v.status))
        total = rows.length; pages = Math.max(1, Math.ceil(total / 10))
        const page = Math.min(this.data.page, pages); this.setData({ page }); rows = rows.slice((page - 1) * 10, page * 10)
      } else { rows = r.list || []; total = Number(r.total || 0); pages = business.pageCount(r) }
      if (this.data.page > pages) { this.setData({ page: pages }); await this.load(); return }
      this.setData({ rows: rows.map(v => view.rowView(m.key, v)), total, pages, canWrite: admin.can(this.data.profile, m.permission + ':write') })
    } catch (e) { if (generation === this.generation) { this.setData({ rows: [] }); this.error(e) } }
    finally { if (generation === this.generation) this.setData({ busy: false }) }
  },
  search() { if (!this.data.busy) { this.setData({ page: 1 }); this.load() } },
  previous() { if (!this.data.busy && this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.load() } },
  next() { if (!this.data.busy && this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.load() } },
  showDetail(value) {
    const m = this.data.current, d = business.normalize(value), source = this.source || {}
    if (m.key === 'employees') {
      for (const [key, rows] of [['department', source.departments], ['position', source.positions]]) {
        const option = (rows || []).find(v => String(v.id) === String(d[key + 'Id']))
        if (option) d[key + 'Name'] = option.name
      }
      d.roleNames = (d.roleIds || []).map(id => { const role = (source.roles || []).find(r => String(r.id) === String(id)); return role ? role.name : '角色 ' + id })
    }
    const canWrite = admin.can(this.data.profile, m.permission + ':write')
    let canRemove = canWrite && !!m.removable, removalHint = ''
    if (m.key === 'roles' && (d.system || d.roleKey === 'admin' || d.members > 0)) { canRemove = false; removalHint = '内置管理员或已分配员工的角色不能删除' }
    if (m.key === 'products' && (!['draft', 'off_sale'].includes(d.status) || d.stock > 0)) { canRemove = false; removalHint = '删除前请先下架，并处理实物库存和订单锁定' }
    if (m.key === 'categories' && (d.childCount > 0 || d.productCount > 0)) { canRemove = false; removalHint = '请先迁移子分类及关联商品，再删除分类' }
    const related = m.key === 'products' ? ['inventory'] : m.key === 'categories' ? ['products'] : m.key === 'users' || m.key === 'aftersales' ? ['orders'] : []
    const relatedLinks = related.map(key => modules.find(v => v.key === key)).filter(v => admin.can(this.data.profile, v.permission + ':read')).map(v => ({ key: v.key, title: v.key === 'inventory' ? '查看商品库存' : v.key === 'products' ? '查看分类商品' : '查看关联订单' }))
    const details = view.detailView(m.key, d)
    if (m.key === 'roles') details.sections.push({ title: '已授权权限', entries: (d.permissionIds || []).map(id => { const permission = (source.permissions || []).find(p => String(p.id) === String(id)); return { title: permission ? permission.name : '权限 ' + id, lines: permission ? [{ label: '权限标识', value: permission.permissionKey }] : [] } }) })
    this.setData(Object.assign(details, {
      relatedLinks,
      canWrite, canRemove, removalHint,
      actions: m.actions.filter(a => allowed(m, d, a) && admin.can(this.data.profile, a.permission || m.permission + (a.method === 'GET' ? ':read' : ':write'))),
    }))
  },
  async detail(e) {
    if (this.data.busy) return
    const id = e.currentTarget.dataset.id
    if (!business.positiveId(id)) return
    this.setData({ busy: true, error: '', fields: null, records: null })
    try {
      const m = this.data.current, d = await admin.request('/' + m.key + '/' + encodeURIComponent(id))
      if (m.key === 'roles') await this.sourceFor([{ source: 'permissions' }])
      if (m.key === 'categories') Object.assign(d, this.data.rows.find(r => String(r.id) === String(id)) || {}, { name: d.name })
      this.showDetail(d)
    } catch (e) { this.error(e) } finally { this.setData({ busy: false }) }
  },
  back() { if (!this.data.busy) this.setData({ detail: null, fields: null, records: null, error: '', notice: '' }) },
  formInput(e) {
    if (this.data.busy || this.data.formLocked) return
    const key = e.currentTarget.dataset.key, field = (this.data.fields || []).find(v => v.key === key)
    if (!field || field.disabled) return
    this.setData({ form: Object.assign({}, this.data.form, { [key]: e.detail.value }) })
  },
  async chooseImages(e) {
    if (this.data.busy || this.data.formLocked || !admin.can(this.data.profile, 'product:write') || !this.data.current || this.data.current.key !== 'products') return
    const field = (this.data.fields || []).find(v => v.key === e.currentTarget.dataset.key)
    if (!field || !['image', 'images'].includes(field.type)) return
    const existing = field.type === 'image' ? [] : String(this.data.form[field.key] || '').split(/\r?\n/).filter(Boolean)
    const remaining = field.type === 'image' ? 1 : 20 - existing.length
    if (remaining <= 0) { this.setData({ error: '详情图片最多 20 张，请先移除部分图片' }); return }
    this.setData({ busy: true, error: '', notice: '' })
    try {
      const result = await new Promise((resolve, reject) => wx.chooseMedia({
        count: Math.min(9, remaining), mediaType: ['image'], sourceType: ['album', 'camera'], sizeType: ['original', 'compressed'],
        success: resolve, fail: reject,
      }))
      if (result.tempFiles.some(file => !file.size || file.size > 10 * 1024 * 1024)) throw new Error('请选择非空图片，单张图片不能超过 10MB')
      for (let index = 0; index < result.tempFiles.length; index++) {
        this.setData({ notice: '正在上传图片 ' + (index + 1) + '/' + result.tempFiles.length + '…' })
        const image = await admin.uploadImage(result.tempFiles[index].tempFilePath)
        existing.push(image.url)
        const form = Object.assign({}, this.data.form, { [field.key]: field.type === 'image' ? image.url : existing.join('\n') })
        this.setData({ form, fields: prepareFields(this.baseFields, form, this.source || {}, this.data.current.key, this.editingDetail) })
      }
    } catch (e) {
      if (!/cancel/i.test(e.errMsg || '')) this.error(e)
    } finally { this.setData({ busy: false, notice: '' }) }
  },
  changeImage(e) {
    if (this.data.busy || this.data.formLocked || !admin.can(this.data.profile, 'product:write') || !this.data.current || this.data.current.key !== 'products') return
    const { key, index, offset } = e.currentTarget.dataset, field = (this.data.fields || []).find(v => v.key === key)
    if (!field || !['image', 'images'].includes(field.type)) return
    const urls = field.images.map(image => image.url), position = Number(index)
    if (!Number.isInteger(position) || position < 0 || position >= urls.length) return
    if (offset == null) urls.splice(position, 1)
    else {
      const target = position + Number(offset)
      if (!Number.isInteger(target) || target < 0 || target >= urls.length) return
      urls.splice(target, 0, urls.splice(position, 1)[0])
    }
    const form = Object.assign({}, this.data.form, { [key]: field.type === 'image' ? (urls[0] || '') : urls.join('\n') })
    this.setData({ form, fields: prepareFields(this.baseFields, form, this.source || {}, this.data.current.key, this.editingDetail) })
  },
  pick(e) {
    if (this.data.busy || this.data.formLocked) return
    const field = (this.data.fields || []).find(v => v.key === e.currentTarget.dataset.key), option = field && field.choices[Number(e.detail.value)]
    if (!option || field.disabled) return
    const form = Object.assign({}, this.data.form, { [field.key]: option.id })
    this.setData({ form, fields: prepareFields(this.baseFields, form, this.source || {}, this.data.current.key, this.editingDetail) })
  },
  checks(e) {
    if (this.data.busy || this.data.formLocked) return
    const field = (this.data.fields || []).find(v => v.key === e.currentTarget.dataset.key)
    if (!field || !field.multiple) return
    const group = field.groups.find(g => g.key === e.currentTarget.dataset.group)
    if (!group) return
    const groupIds = group.choices.map(c => c.id)
    const selected = field.choices.filter(c => c.checked && !groupIds.includes(c.id)).map(c => c.id).concat(e.detail.value)
    this.setSelection(field.key, selected)
  },
  groupSelect(e) {
    if (this.data.busy || this.data.formLocked) return
    const field = this.data.fields.find(v => v.key === e.currentTarget.dataset.key), group = field && field.groups.find(g => g.key === e.currentTarget.dataset.group)
    if (!group) return
    const ids = group.choices.map(c => c.id), selected = field.choices.filter(c => c.checked && !ids.includes(c.id)).map(c => c.id)
    this.setSelection(field.key, group.allChecked ? selected : selected.concat(ids))
  },
  setSelection(key, ids) {
    const form = Object.assign({}, this.data.form, { [key]: ids.join(',') })
    this.setData({ form, fields: prepareFields(this.baseFields, form, this.source || {}, this.data.current.key, this.editingDetail) })
  },
  async prepareChoices() {
    this.setData({ busy: true, formReady: false, error: '' })
    try {
      const source = await this.sourceFor(this.baseFields)
      this.setData({ fields: prepareFields(this.baseFields, this.data.form, source, this.data.current.key, this.editingDetail), formReady: true })
    } catch (e) { this.error(e) } finally { this.setData({ busy: false }) }
  },
  async edit(e) {
    const m = this.data.current, create = e.currentTarget.dataset.create === 'yes'
    if (this.data.busy || !admin.can(this.data.profile, m.permission + ':write') || !m.fields || (!create && m.createOnly)) return
    this.editingDetail = create ? null : this.data.detail
    this.baseFields = m.fields.filter(field => !(m.key === 'roles' && this.editingDetail && (this.editingDetail.system || this.editingDetail.roleKey === 'admin') && field.key === 'permissionIds')).map(v => Object.assign({}, v))
    if (create && m.employee) this.baseFields.push(f('password', '初始密码（12–72 个 UTF-8 字节）', 'password', true, '', { maxLength: 72 }))
    const form = {}
    this.baseFields.forEach(field => { const value = this.editingDetail ? this.editingDetail[field.key] : undefined; form[field.key] = value == null ? field.value : Array.isArray(value) ? value.join(field.type === 'ids' ? ',' : '\n') : value })
    this.requestId = api.id(); this.frozenBody = null
    this.setData({ fields: this.baseFields, form, operation: { create, edit: true, label: create ? '新增' + m.title.replace('管理', '') : '编辑资料' }, formLocked: false, formReady: false, records: null, error: '', notice: '' })
    await this.prepareChoices()
  },
  async action(e) {
    if (this.data.busy || !this.data.detail) return
    const m = this.data.current, a = this.data.actions.find(a => a.key === e.currentTarget.dataset.key)
    if (!a || !allowed(m, this.data.detail, a) || !admin.can(this.data.profile, a.permission || m.permission + (a.method === 'GET' ? ':read' : ':write'))) return
    if (a.method === 'GET') {
      this.setData({ records: { key: a.key, title: a.label, page: 1, pages: 1, total: 0, rows: [], changeType: '', referenceNo: '', typeChoices: [{ id: '', name: '全部变动类型' }].concat(choices(logTypes)), typeLabel: '全部变动类型' } })
      await this.loadRecords(); return
    }
    const form = {}
    a.fields.forEach(field => { form[field.key] = field.value })
    if (a.key === 'roles') form.ids = (this.data.detail.roleIds || []).join(',')
    if (a.key === 'permissions') form.ids = (this.data.detail.permissionIds || []).join(',')
    if (a.key === 'status') form.status = this.data.detail.status
    if (a.key === 'adjust') form.quantity = this.data.detail.quantity
    if (a.key === 'shipment' && this.data.detail.logistics) Object.assign(form, { carrier: this.data.detail.logistics.carrier, trackingNo: this.data.detail.logistics.trackingNo })
    this.baseFields = a.fields; this.editingDetail = null; this.requestId = api.id(); this.frozenBody = null
    this.setData({ fields: a.fields, form, operation: a, formLocked: false, formReady: false, records: null, error: '', notice: a.key === 'complete' ? '请先在退款渠道完成退款并核实结果，再登记退款凭证；微信订单请使用原路退款操作。' : a.key === 'wechat-refund' ? '确认后将发起微信原路全额退款，或查询同一笔退款结果。退款处理中时可稍后重试查询。' : '' })
    await this.prepareChoices()
  },
  async closeForm() {
    if (this.data.busy) return
    const refreshInventory = this.data.current && this.data.current.key === 'inventory' && this.data.formLocked && this.data.detail
    this.frozenBody = null; this.setData({ fields: null, form: {}, operation: null, formLocked: false, formReady: true, error: '', notice: '' })
    if (refreshInventory) {
      this.setData({ busy: true })
      try { this.showDetail(await admin.request('/inventory/' + encodeURIComponent(refreshInventory.id))) }
      catch (e) { this.error(e) } finally { this.setData({ busy: false }) }
    }
  },
  async submit() {
    if (this.data.busy || !this.data.operation || !this.data.formReady) return
    const m = this.data.current, op = this.data.operation, d = this.data.detail
    if (!admin.can(this.data.profile, op.permission || m.permission + ':write')) return
    if (!op.edit && (!d || !allowed(m, d, op))) return
    let body
    try {
      body = this.frozenBody || payload(this.data.fields, this.data.form)
      if (m.key === 'inventory' && op.key === 'outbound' && body.quantity > d.availableQuantity) throw new Error('出库数量不能超过可用库存')
      if (m.key === 'inventory' && op.key === 'adjust' && body.quantity < d.lockedQuantity) throw new Error('盘点实物库存不能小于订单锁定库存')
    } catch (e) { this.error(e); return }
    this.setData({ busy: true, error: '' })
    const confirmed = await business.confirm(op.key === 'wechat-refund' ? '确认微信退款 / 查询结果' : '确认' + op.label, op.key === 'wechat-refund' ? '将为此售后单发起微信原路全额退款或查询原退款结果，金额 ¥' + d.amount + '，确认继续？' : op.key === 'complete' ? '确认已核实渠道退款成功，并将凭证登记到此售后单？' : op.key === 'permissions' || op.key === 'roles' ? '本次选择将替换原有全部授权，请核对后提交。' : '请核对填写内容，确认后保存业务数据。')
    if (!confirmed) { this.setData({ busy: false }); return }
    let path = '/' + m.key, method = op.method || (op.create ? 'POST' : 'PUT')
    if (op.edit) {
      if (!op.create) path += '/' + encodeURIComponent(d.id)
      if (op.create && m.employee) { const password = body.password; body = { employee: Object.assign({}, body), password }; delete body.employee.password }
    } else path += '/' + encodeURIComponent(d.id) + '/' + op.key
    if (m.idempotent) { body.requestId = this.requestId; this.frozenBody = body }
    try {
      await admin.request(path, method, body)
      this.frozenBody = null
      this.setData({ fields: null, operation: null, form: {}, formLocked: false, notice: '操作已保存', records: null })
      wx.showToast({ title: '操作成功' })
      if (m.key === 'roles' || m.key === 'employees') await this.refreshProfile()
      if (!this.data.current) return
      if (op.create) { this.setData({ detail: null, page: 1 }); await this.load() }
      else { const detail = await admin.request('/' + m.key + '/' + encodeURIComponent(d.id)); this.showDetail(detail) }
    } catch (e) {
      if (this.frozenBody && (e.uncertain || !e.status || e.status >= 500)) this.setData({ formLocked: true, notice: '请求结果暂未确认，表单内容已保留。重试会使用同一请求编号；也可返回详情核对库存。' })
      else { this.frozenBody = null; this.setData({ formLocked: false }) }
      this.error(e)
    } finally { this.setData({ busy: false }) }
  },
  async remove() {
    const m = this.data.current, d = this.data.detail
    if (this.data.busy || !d || !this.data.canRemove || !admin.can(this.data.profile, m.permission + ':write')) return
    this.setData({ busy: true, error: '' })
    const confirmed = await business.confirm('确认删除', '确认删除“' + (d.name || d.nickname || d.id) + '”？删除后无法在此恢复。')
    if (!confirmed) { this.setData({ busy: false }); return }
    try { await admin.request('/' + m.key + '/' + encodeURIComponent(d.id), 'DELETE'); this.setData({ detail: null, page: 1, notice: '记录已删除' }); await this.load() }
    catch (e) { this.error(e) } finally { this.setData({ busy: false }) }
  },
  async loadRecords() {
    const records = this.data.records, d = this.data.detail, m = this.data.current
    if (!records || !d) return
    this.setData({ busy: true, error: '' })
    try {
      let rows, pages, total
      if (records.key === 'logs') {
        const result = await admin.request('/inventory/logs?' + business.query({ inventoryId: d.id, page: records.page, size: 10, changeType: records.changeType, referenceNo: records.referenceNo.trim() }))
        rows = result.list; pages = business.pageCount(result); total = result.total
      } else {
        const result = await admin.request('/' + m.key + '/' + encodeURIComponent(d.id) + '/operations')
        total = result.length; pages = Math.max(1, Math.ceil(total / 10)); rows = result.slice((records.page - 1) * 10, records.page * 10)
      }
      this.setData({ records: Object.assign({}, records, { rows: rows.map(view.recordView), pages, total }) })
    } catch (e) { this.error(e) } finally { this.setData({ busy: false }) }
  },
  recordFilter(e) {
    if (this.data.busy || !this.data.records) return
    const records = Object.assign({}, this.data.records), key = e.currentTarget.dataset.key
    if (key === 'changeType') { const option = records.typeChoices[Number(e.detail.value)]; records.changeType = option.id; records.typeLabel = option.name }
    else if (key === 'referenceNo') records.referenceNo = e.detail.value
    this.setData({ records })
  },
  searchRecords() { if (!this.data.busy) { this.setData({ records: Object.assign({}, this.data.records, { page: 1 }) }); this.loadRecords() } },
  recordPage(e) {
    if (this.data.busy) return
    const r = this.data.records, page = r.page + Number(e.currentTarget.dataset.delta)
    if (page < 1 || page > r.pages) return
    this.setData({ records: Object.assign({}, r, { page }) }); this.loadRecords()
  },
  closeRecords() { if (!this.data.busy) this.setData({ records: null, error: '' }) },
  previewImage(e) {
    const url = e.currentTarget.dataset.url
    if (url) {
      const formImages = (this.data.fields || []).flatMap(field => (field.images || []).map(image => image.src))
      wx.previewImage({ current: url, urls: [...new Set([url, this.data.cover].concat(this.data.detailImages, formImages).filter(Boolean))] })
    }
  },
  async related(e) {
    if (this.data.busy || !this.data.detail) return
    const d = this.data.detail, key = e.currentTarget.dataset.key, module = modules.find(m => m.key === key)
    if (!module || !admin.can(this.data.profile, module.permission + ':read')) return
    if (key === 'orders' && d.orderId) {
      await this.open({ currentTarget: { dataset: { key } } })
      await this.detail({ currentTarget: { dataset: { id: d.orderId } } }); return
    }
    await this.open({ currentTarget: { dataset: { key } } })
    const filterValues = Object.assign({}, this.data.filterValues)
    if (key === 'inventory') filterValues.productId = d.id
    if (key === 'products') filterValues.categoryId = d.id
    if (key === 'orders') filterValues.userId = d.id
    this.setData({ filterValues, filters: prepareFields(module.filters || [], filterValues, this.source || {}, key), page: 1, showFilters: true })
    await this.load()
  },
})
