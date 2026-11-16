const { label, normalize } = require('./business')
const api = require('./api')
const names = {
  id: '编号', name: '名称', username: '登录账号', nickname: '昵称', status: '状态', price: '销售价', originalPrice: '原价', quantity: '实物库存', availableQuantity: '可用库存', lockedQuantity: '订单锁定库存',
  productName: '商品', productId: '商品编号', orderNo: '订单号', aftersaleNo: '售后单号', orderId: '订单编号', userId: '用户编号', reason: '申请原因', remark: '备注', reviewRemark: '审核说明', completionRemark: '退款说明',
  phone: '手机号', phoneLast4: '手机尾号', departmentName: '部门', positionName: '岗位', departmentId: '部门编号', positionId: '岗位编号', roleKey: '角色标识', roleNames: '角色', members: '关联员工数',
  totalAmount: '商品总额', payAmount: '应付金额', amount: '退款金额', discountAmount: '优惠合计', freightAmount: '运费', fullReductionAmount: '满减优惠', couponDiscountAmount: '优惠券抵扣',
  carrier: '物流公司', trackingNo: '物流单号', summary: '物流说明', createTime: '创建时间', updateTime: '更新时间', createdAt: '记录时间', occurredAt: '操作时间', completedTime: '退款完成时间', expireTime: '付款截止时间',
  changeType: '变动类型', changeQuantity: '变动数量', quantityBefore: '变更前实物库存', quantityAfter: '变更后实物库存', lockedBefore: '变更前锁定库存', lockedAfter: '变更后锁定库存',
  points: '积分', memberLevel: '会员等级', email: '邮箱', gender: '性别', birthday: '生日', operatorId: '操作人编号', operatorType: '操作人类型', requestId: '请求编号', referenceNo: '关联单号', refundReference: '退款渠道凭证',
  reviewedBy: '审核人编号', parentId: '上级分类编号', sort: '排序', childCount: '直接子分类数', productCount: '直接商品数', category: '分类', brandName: '品牌', subtitle: '副标题', description: '商品说明', stock: '可用库存', sales: '销量',
  action: '操作', fromStatus: '原状态', toStatus: '目标状态', paymentNo: '支付单号', channel: '支付渠道', paidTime: '支付时间', receiver: '收货人', unitPrice: '单价', subtotal: '小计',
}
const translated = ['status', 'changeType', 'action', 'fromStatus', 'toStatus', 'operatorType', 'channel', 'gender']
const money = ['price', 'originalPrice', 'totalAmount', 'payAmount', 'amount', 'discountAmount', 'freightAmount', 'fullReductionAmount', 'couponDiscountAmount', 'unitPrice', 'subtotal']
function lines(value, keys, labels = {}) {
  const row = normalize(value)
  return (keys || Object.keys(row)).filter(key => names[key] && row[key] != null && row[key] !== '' && typeof row[key] !== 'object').map(key => ({
    label: labels[key] || names[key], value: translated.includes(key) ? label(row[key]) : money.includes(key) ? '¥' + row[key] : String(row[key]),
  }))
}
function rowView(key, row) {
  const d = normalize(row)
  const subtitle = {
    products: '¥' + d.price + ' · 可用库存 ' + (d.stock == null ? '—' : d.stock) + ' · 销量 ' + (d.sales || 0),
    categories: '子分类 ' + (d.childCount || 0) + ' · 商品 ' + (d.productCount || 0) + ' · 排序 ' + d.sort,
    inventory: '实物 ' + d.quantity + ' · 锁定 ' + d.lockedQuantity + ' · 可用 ' + d.availableQuantity,
    orders: '实付 ¥' + d.payAmount + ' · ' + (d.createTime || ''),
    employees: [d.username, d.phone, d.departmentName, d.positionName].filter(Boolean).join(' · '),
    users: '手机尾号 ' + (d.phoneLast4 || '—') + ' · 积分 ' + (d.points || 0),
    roles: d.roleKey + ' · ' + (d.members || 0) + ' 位员工',
    aftersales: '¥' + d.amount + ' · 订单 ' + (d.orderNo || d.orderId),
  }
  return Object.assign({}, d, {
    title: d.name || d.productName || d.nickname || d.aftersaleNo || d.orderNo || '编号 ' + d.id,
    statusLabel: label(d.status), subtitle: subtitle[key] || '', image: api.image(d.coverUrl),
    warning: key === 'inventory' && d.availableQuantity <= 10 ? '低库存' : '',
  })
}
function detailView(key, value) {
  const d = normalize(value), sections = []
  if (key === 'orders') {
    if (d.address) sections.push({ title: '收货信息', entries: [{ lines: lines(d.address, ['receiver', 'phone']).concat([{ label: '地址', value: [d.address.province, d.address.city, d.address.district, d.address.detail].filter(Boolean).join('') }]) }] })
    sections.push({ title: '订单商品', entries: (d.items || []).map(item => ({ title: item.productName, lines: lines(item, ['quantity', 'unitPrice', 'subtotal'], { quantity: '购买数量' }) })) })
    if (d.logistics) sections.push({ title: '物流信息', entries: [{ lines: lines(d.logistics) }] })
    sections.push({ title: '状态时间线', entries: (d.timeline || []).map(item => ({ title: label(item.toStatus), lines: lines(item, ['fromStatus', 'occurredAt']) })) })
    sections.push({ title: '支付记录', entries: (d.payments || []).map(item => ({ lines: lines(item, undefined, { amount: '支付金额' }) })) })
  }
  if (key === 'employees' && Array.isArray(d.roleNames)) sections.push({ title: '已分配角色', entries: d.roleNames.map(name => ({ title: name, lines: [] })) })
  return { detail: d, detailLines: lines(d), sections, detailImages: (d.detailImages || []).map(api.image).filter(Boolean), cover: api.image(d.coverUrl) }
}
function recordView(row) {
  const d = normalize(row)
  return { id: d.id, title: label(d.changeType || d.action), lines: lines(d) }
}
module.exports = { lines, rowView, detailView, recordView }
