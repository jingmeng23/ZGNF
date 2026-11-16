// Only declared fields and filters are sent to the existing server DTOs.
const { states, categories, positiveId, normalize } = require('./business')
const { image } = require('./api')
const f = (key, label, type = 'text', required = true, value = '', extra = {}) => Object.assign({ key, label, type, required, value, maxLength: 200 }, extra)
const choices = codes => codes.map(id => ({ id, name: states[id] || id }))
const enabled = ['active', 'inactive']
const productStates = ['draft', 'on_sale', 'off_sale', 'sold_out', 'presale', 'scheduled']
const orderStates = ['pending_payment', 'paid', 'processing', 'completed', 'cancelled', 'refunded']
const aftersaleStates = ['pending', 'approved', 'rejected', 'completed']
const status = (codes = enabled, value = 'active') => f('status', '状态', 'select', true, value, { choices: choices(codes) })
const remark = (maxLength = 500) => f('remark', '操作说明', 'textarea', true, '', { maxLength })
const relation = (key, label, source, required = true, value = '') => f(key, label, 'id', required, value, { source })
const action = (key, label, fields = [], method = 'PUT', permission) => ({ key, label, fields, method, permission })
const stateFilter = codes => f('status', '状态', 'select', false, '', { choices: [{ id: '', name: '全部状态' }].concat(choices(codes)) })
const modules = [
  { key: 'products', title: '商品管理', description: '商品资料、分类、价格与上下架', permission: 'product', search: 'keyword', searchLabel: '搜索商品名称',
    fields: [f('name', '商品名称'), relation('categoryId', '商品分类', 'categories'), f('brandName', '品牌名称', 'text', false, '', { maxLength: 100 }), f('subtitle', '副标题', 'text', false, '', { maxLength: 500 }), f('coverUrl', '商品封面图片', 'image', false, '', { maxLength: 512 }), f('price', '销售价格（元）', 'money'), f('originalPrice', '原价（元）', 'money', false), f('description', '商品说明', 'textarea', false, '', { maxLength: 16000 }), f('detailImages', '商品详情图片（最多 20 张）', 'images', false, '', { maxLength: 11000 })],
    filters: [stateFilter(productStates), relation('categoryId', '分类', 'categories', false), f('sort', '排序', 'select', false, 'createTime', { choices: [{ id: 'createTime', name: '创建时间' }, { id: 'price', name: '价格' }, { id: 'sales', name: '销量' }, { id: 'id', name: '编号' }] }), f('order', '顺序', 'select', false, 'desc', { choices: [{ id: 'desc', name: '从高到低 / 从新到旧' }, { id: 'asc', name: '从低到高 / 从旧到新' }] })],
    actions: [action('status', '调整上下架', [status(productStates, 'draft')])], removable: true },
  { key: 'categories', title: '分类管理', description: '分类层级、排序、启停与商品数量', permission: 'category', search: 'keyword', searchLabel: '搜索分类名称', listPath: '/categories/management',
    fields: [f('name', '分类名称', 'text', true, '', { maxLength: 64 }), f('parentId', '上级分类', 'rootId', true, '0', { source: 'categories' }), f('sort', '排序（数值越小越靠前）', 'number', true, 0), status()], filters: [stateFilter(enabled)], actions: [action('status', '启用 / 停用', [status()])], removable: true },
  { key: 'inventory', title: '库存管理', description: '台账、低库存、出入库、盘点与流水', permission: 'inventory', search: 'keyword', searchLabel: '搜索商品名称',
    fields: [relation('productId', '商品', 'products'), f('quantity', '初始实物库存', 'number', true, 0), remark(255)], createOnly: true, idempotent: true,
    filters: [f('availableMax', '可用库存上限（留空不限，0 为无库存）', 'number', false), relation('productId', '商品', 'products', false)],
    actions: [action('inbound', '入库', [f('quantity', '入库数量', 'positive'), remark(255)], 'POST'), action('outbound', '出库', [f('quantity', '出库数量', 'positive'), remark(255)], 'POST'), action('adjust', '盘点', [f('quantity', '盘点后实物总量', 'number'), remark(255)]), action('logs', '库存流水', [], 'GET')] },
  { key: 'orders', title: '订单管理', description: '订单查询、履约、物流与操作记录', permission: 'order', search: 'orderNo', searchLabel: '输入完整订单号',
    filters: [stateFilter(orderStates), f('userId', '用户编号', 'id', false), f('startDate', '起始日期', 'date', false), f('endDate', '结束日期', 'date', false)],
    actions: [action('process', '开始履约'), action('cancel', '取消待付款订单', [remark()]), action('shipment', '登记 / 修改物流', [f('carrier', '物流公司', 'text', true, '', { maxLength: 100 }), f('trackingNo', '物流单号', 'text', true, '', { maxLength: 100 })]), action('notes', '添加内部备注', [remark()], 'POST'), action('operations', '操作记录', [], 'GET')] },
  { key: 'employees', title: '员工管理', description: '员工资料、部门岗位、角色与账号状态', permission: 'employee', search: 'keyword', searchLabel: '搜索员工姓名或账号',
    fields: [f('username', '登录账号（3–64 位字母、数字、_.-）', 'text', true, '', { maxLength: 64 }), f('name', '员工姓名', 'text', true, '', { maxLength: 100 }), f('phone', '手机号', 'text', true, '', { maxLength: 11 }), relation('departmentId', '部门', 'departments', false), relation('positionId', '岗位', 'positions', false), status()],
    filters: [stateFilter(enabled), relation('roleId', '角色', 'roles', false)], employee: true, removable: true,
    actions: [action('status', '启用 / 停用员工', [status()]), action('roles', '分配角色', [f('ids', '角色', 'ids', false, '', { source: 'roles' })], 'PUT', 'role:write'), action('password', '重置密码', [f('password', '新密码（12–72 个 UTF-8 字节）', 'password', true, '', { maxLength: 72 })], 'PUT', 'role:write')] },
  { key: 'users', title: '用户管理', description: '顾客资料、会员积分与账号状态', permission: 'user', search: 'keyword', searchLabel: '搜索昵称、手机尾号或用户编号', filters: [stateFilter(enabled)], removable: true, actions: [action('status', '启用 / 停用用户', [status()])] },
  { key: 'roles', title: '角色权限', description: '角色资料、成员数量与按模块授权', permission: 'role', search: 'keyword', searchLabel: '搜索角色名称或标识',
    fields: [f('name', '角色名称', 'text', true, '', { maxLength: 100 }), f('roleKey', '角色标识（创建后不可修改）', 'text', true, '', { maxLength: 64 }), status(), f('permissionIds', '权限（可按模块全选）', 'ids', false, '', { source: 'permissions' })],
    filters: [stateFilter(enabled)], removable: true, actions: [action('permissions', '分配权限', [f('ids', '权限（留空将清空授权）', 'ids', false, '', { source: 'permissions' })])] },
  { key: 'aftersales', title: '售后管理', description: '申请查询、审核、退款凭证与处理记录', permission: 'aftersale', search: 'aftersaleNo', searchLabel: '输入完整售后单号', filters: [stateFilter(aftersaleStates), f('orderNo', '关联订单号', 'text', false, '', { maxLength: 40 })],
    actions: [action('review', '审核申请', [status(['approved', 'rejected'], ''), remark()]), action('wechat-refund', '微信原路退款 / 查询结果', [], 'POST'), action('complete', '其他渠道退款凭证登记', [f('refundReference', '已完成退款的渠道凭证号', 'text', true, '', { maxLength: 100 }), f('remark', '退款说明', 'textarea', false, '', { maxLength: 500 })]), action('operations', '处理记录', [], 'GET')] },
]
function payload(fields, values) {
  const result = {}
  for (const field of fields) {
    const source = values[field.key]
    const raw = source == null ? '' : Array.isArray(source) ? source.join(['lines', 'images'].includes(field.type) ? '\n' : ',') : field.type === 'password' ? String(source) : String(source).trim()
    if (!raw && field.required) throw new Error('请填写 / 选择' + field.label)
    if (!raw && !field.required) { result[field.key] = ['ids', 'lines', 'images'].includes(field.type) ? [] : null; continue }
    if (field.type !== 'ids' && field.maxLength && raw.length > field.maxLength) throw new Error(field.label + '内容过长')
    let value = raw
    if (['number', 'positive', 'id', 'rootId'].includes(field.type)) {
      if (!/^\d+$/.test(raw)) throw new Error(field.label + '必须是整数')
      if (['id', 'rootId'].includes(field.type)) { if (field.type === 'id' && !positiveId(raw)) throw new Error(field.label + '必须大于 0'); value = raw }
      else { value = Number(raw); if (!Number.isSafeInteger(value) || value > 2147483647 || value < (field.type === 'positive' ? 1 : 0)) throw new Error(field.label + '数值无效') }
    }
    if (field.type === 'money' && (!/^\d{1,8}(\.\d{1,2})?$/.test(raw) || Number(raw) < (field.key === 'price' ? 0.01 : 0))) throw new Error('价格须为有效金额，最多两位小数')
    if (field.type === 'select' && field.choices && !field.choices.some(c => String(c.id) === raw)) throw new Error('请选择有效的' + field.label)
    if (field.type === 'password' && (raw.length < 12 || unescape(encodeURIComponent(raw)).length > 72)) throw new Error('密码至少 12 个字符，最多 72 个 UTF-8 字节')
    if (field.key === 'phone' && !/^1[3-9]\d{9}$/.test(raw)) throw new Error('请填写有效手机号')
    if (field.key === 'username' && !/^[a-zA-Z0-9_.-]{3,64}$/.test(raw)) throw new Error('员工账号格式不正确')
    if (field.key === 'roleKey' && !/^[a-z][a-z0-9_]{1,63}$/.test(raw)) throw new Error('角色标识须为 2–64 位小写字母、数字或下划线，以字母开头')
    if (field.type === 'ids') { value = [...new Set(raw.split(/[,，\s]+/))]; if (value.some(v => !positiveId(v))) throw new Error('所选编号无效') }
    if (field.type === 'lines' || field.type === 'images') { value = raw.split(/\r?\n/).map(v => v.trim()).filter(Boolean); if (value.length > 20 || value.some(v => !validImage(v))) throw new Error('详情图片无效或超过 20 张，请重新上传') }
    if (['url', 'image'].includes(field.type) && !validImage(raw)) throw new Error('图片无效，请重新上传')
    result[field.key] = value
  }
  if (result.originalPrice != null && result.price != null && Number(result.originalPrice) < Number(result.price)) throw new Error('原价不能低于销售价格')
  return result
}
function validImage(value) { return value.length <= 512 && /^(https?:\/\/[^\s/]+\/\S*|https?:\/\/[^\s/]+|\/(?!\/)\S*)$/.test(value) && !/[\r\n]/.test(value) }
function allowed(m, detail, a) {
  if (m.key === 'orders') return a.key === 'process' ? detail.status === 'paid' : a.key === 'cancel' ? detail.status === 'pending_payment' : a.key === 'shipment' ? ['paid', 'processing'].includes(detail.status) : true
  if (m.key === 'aftersales') return a.key === 'review' ? detail.status === 'pending' : ['complete', 'wechat-refund'].includes(a.key) ? detail.status === 'approved' : true
  if (m.key === 'roles' && a.key === 'permissions') return !detail.system && detail.roleKey !== 'admin'
  return true
}
const permissionNames = { product: '商品', category: '分类', inventory: '库存', order: '订单', employee: '员工', user: '用户', role: '角色权限', aftersale: '售后', workorder: '工单', operation: '运营', statistics: '统计' }
function prepareFields(fields, form, source, moduleKey, detail) {
  return fields.map(field => {
    const out = Object.assign({}, field), value = form[field.key]
    if (field.type === 'image' || field.type === 'images') {
      const urls = field.type === 'image' ? (value ? [value] : []) : String(value || '').split(/\r?\n/).filter(Boolean)
      out.images = urls.map(url => ({ url, src: image(url) }))
    }
    if (field.source) {
      let rows = normalize(source[field.source] || [])
      if (field.source === 'categories') rows = categories(rows, field.key === 'parentId' && detail ? detail.id : undefined)
      out.choices = rows.map(row => ({ id: String(row.id), name: row.name + (row.status === 'inactive' ? '（停用）' : ''), status: row.status, permissionKey: row.permissionKey || row.code }))
      if (field.key === 'parentId') out.choices.unshift({ id: '0', name: '顶级分类' })
      else if (!field.required && field.type !== 'ids') out.choices.unshift({ id: '', name: '全部 / 不设置' })
      if (value && field.type !== 'ids' && !out.choices.some(c => c.id === String(value))) out.choices.push({ id: String(value), name: '原关联（' + value + '）' })
    }
    if (out.choices && field.type !== 'ids') {
      out.choices = out.choices.slice()
      if (field.required && !out.choices.some(c => c.id === '')) out.choices.unshift({ id: '', name: '请选择' })
      out.selectedIndex = Math.max(0, out.choices.findIndex(c => String(c.id) === String(value == null ? '' : value)))
      out.selectedLabel = out.choices[out.selectedIndex] ? out.choices[out.selectedIndex].name : '暂无选项'
    }
    if (field.type === 'ids') {
      const selected = Array.isArray(value) ? value.map(String) : String(value || '').split(',').filter(Boolean)
      out.multiple = true
      selected.forEach(id => { if (!out.choices.some(c => c.id === id)) out.choices.push({ id, name: '原授权（' + id + '）', permissionKey: 'other' }) })
      out.choices = out.choices.map(c => Object.assign({}, c, { checked: selected.includes(c.id) }))
      const groups = {}
      out.choices.forEach(c => { const key = field.source === 'permissions' ? (c.permissionKey || 'other').split(':')[0] : 'roles'; if (!groups[key]) groups[key] = { key, name: permissionNames[key] || (key === 'roles' ? '可分配角色' : '其他权限'), choices: [] }; groups[key].choices.push(c) })
      out.groups = Object.keys(groups).map(key => Object.assign(groups[key], { allChecked: groups[key].choices.every(c => c.checked) }))
    }
    if (moduleKey === 'roles' && detail) out.disabled = field.key === 'roleKey' || (field.key === 'status' && (detail.system || detail.roleKey === 'admin'))
    return out
  })
}
module.exports = { modules, payload, f, choices, allowed, prepareFields, productStates, orderStates, aftersaleStates }
