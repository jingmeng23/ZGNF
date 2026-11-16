const states = {
  active: '启用', inactive: '停用', draft: '草稿', on_sale: '在售', off_sale: '下架', sold_out: '售罄', presale: '预售', scheduled: '定时上架',
  pending_payment: '待付款', paid: '已付款', processing: '履约中', completed: '已完成', cancelled: '已取消', refunded: '已退款',
  pending: '待审核', approved: '审核通过', rejected: '已拒绝 / 已撤销', success: '成功', failed: '失败', closed: '已关闭',
  initial: '初始建账', inbound: '入库', outbound: '出库', adjustment: '盘点', order_lock: '订单锁定', order_release: '订单释放', order_deduct: '履约扣减',
  apply: '提交申请', cancel: '撤销 / 取消', approve: '审核通过', reject: '审核拒绝', complete: '完成退款', process: '开始履约', ship: '登记发货', shipment_update: '修改物流', note: '添加备注',
  user: '顾客', admin: '管理员', wechat: '微信支付', wechat_mini: '微信小程序', password: '密码登录', male: '男', female: '女', unknown: '未设置',
}
function label(value) { return value == null || value === '' ? '—' : states[value] || String(value) }
function query(values) {
  return Object.keys(values).filter(k => values[k] !== '' && values[k] != null).map(k => encodeURIComponent(k) + '=' + encodeURIComponent(String(values[k]))).join('&')
}
function confirm(title, content) {
  return new Promise(resolve => wx.showModal({ title, content, success: r => resolve(!!r.confirm), fail: () => resolve(false) }))
}
function normalize(value) {
  if (Array.isArray(value)) return value.map(normalize)
  if (!value || typeof value !== 'object') return value
  return Object.keys(value).reduce((out, key) => { out[key.replace(/_([a-z])/g, (_, c) => c.toUpperCase())] = normalize(value[key]); return out }, {})
}
// Both flat admin categories and the public tree are supported. IDs remain strings.
function categories(rows, excludedId) {
  const flat = [], seen = new Set()
  function collect(list, parentId) {
    ;(list || []).forEach(row => {
      const id = String(row.id)
      if (seen.has(id)) return
      seen.add(id)
      flat.push(Object.assign({}, row, { id, parentId: String(row.parentId == null ? parentId || '0' : row.parentId) }))
      collect(row.children, id)
    })
  }
  collect(rows)
  const children = new Map()
  flat.forEach(row => { if (!children.has(row.parentId)) children.set(row.parentId, []); children.get(row.parentId).push(row) })
  const result = [], visited = new Set()
  function walk(parent, path, disabled) {
    ;(children.get(parent) || []).sort((a, b) => Number(a.sort || 0) - Number(b.sort || 0)).forEach(row => {
      if (visited.has(row.id)) return
      visited.add(row.id)
      const blocked = disabled || row.id === String(excludedId), names = path.concat(row.name)
      if (!blocked) result.push(Object.assign({}, row, { name: names.join(' / '), depth: path.length }))
      walk(row.id, names, blocked)
    })
  }
  walk('0', [], false)
  return result
}
function positiveId(value) { return /^\d+$/.test(String(value)) && /[1-9]/.test(String(value)) }
function pageCount(result, size = 10) { return Math.max(1, Number(result.pages) || Math.ceil(Number(result.total || 0) / size)) }
module.exports = { states, label, query, confirm, normalize, categories, positiveId, pageCount }
