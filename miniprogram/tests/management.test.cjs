const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const admin = require('../utils/admin')
const api = require('../utils/api')
const { modules, payload } = require('../utils/management')
beforeEach(() => {
  const storage = new Map()
  global.wx = { getStorageSync: k => storage.get(k), setStorageSync: (k,v) => storage.set(k,v), removeStorageSync: k => storage.delete(k) }
})
test('admin credentials remain separate from consumer credentials including on 401', async () => {
  api.save({accessToken:'consumer',expiresIn:3600})
  wx.request = o => { assert.equal(o.header.Authorization,undefined); assert.match(o.url,/\/admin\/auth\/login$/); o.success({statusCode:200,data:{code:200,data:{accessToken:'employee',expiresIn:3600,admin:{username:'staff'}}}}) }
  await admin.login('staff','password')
  wx.request = o => { assert.equal(o.header.Authorization,'Bearer employee'); o.success({statusCode:401,data:{message:'expired'}}) }
  await assert.rejects(admin.request('/orders'),/expired/)
  assert.equal(admin.session(),null)
  assert.equal(api.session().token,'consumer')
})
test('management privilege checks do not grant write access to read-only staff', () => {
  assert.equal(admin.can({permissions:['product:read']},'product:read'),true)
  assert.equal(admin.can({permissions:['product:read']},'product:write'),false)
  assert.equal(admin.can(null,'role:write'),false)
})
test('DTO serialization preserves large IDs and omits undeclared privilege fields', () => {
  const m=modules.find(m=>m.key==='products')
  const r=payload(m.fields,{name:'Test',categoryId:'9007199254740993',price:'12.30',status:'on_sale',detailImages:'/one.jpg\n/two.jpg'})
  assert.equal(r.categoryId,'9007199254740993')
  assert.equal(r.price,'12.30')
  assert.equal(r.status,undefined)
  assert.deepEqual(r.detailImages,['/one.jpg','/two.jpg'])
})
test('inventory rejects negative, fractional, zero outbound and unsafe counts', () => {
  const m=modules.find(m=>m.key==='inventory'), fields=m.actions.find(a=>a.key==='outbound').fields
  for(const quantity of ['-1','0','1.2','9007199254740993']) assert.throws(()=>payload(fields,{quantity,remark:'test'}))
  assert.equal(payload(fields,{quantity:'2',remark:'test'}).quantity,2)
})
test('permission assignment supports explicit clearing and rejects invalid IDs', () => {
  const fields=modules.find(m=>m.key==='roles').actions[0].fields
  assert.deepEqual(payload(fields,{ids:''}),{ids:[]})
  assert.deepEqual(payload(fields,{ids:'1,1,9007199254740993'}),{ids:['1','9007199254740993']})
  assert.throws(()=>payload(fields,{ids:'0,-1'}))
})
test('order cancellation requires an audit reason', () => {
  const fields=modules.find(m=>m.key==='orders').actions.find(a=>a.key==='cancel').fields
  assert.throws(()=>payload(fields,{}))
  assert.equal(payload(fields,{remark:'Customer requested cancellation'}).remark,'Customer requested cancellation')
})
function page() {
  return require('./helpers.cjs').page('manage')
}
test('inventory retry retains request ID when server response is lost', async () => {
  const p=page(), original=admin.request, requests=[]
  p.data.current=modules.find(m=>m.key==='inventory')
  p.data.profile={permissions:['inventory:write']}
  p.data.detail={id:'12'}
  p.data.operation=p.data.current.actions.find(a=>a.key==='inbound')
  p.data.fields=p.data.operation.fields;p.data.form={quantity:'3',remark:'delivery'};p.requestId='stable-request'
  wx.showModal=o=>o.success({confirm:true})
  admin.request=async (path,method,body)=>{requests.push({path,method,body});throw new Error('timeout')}
  try {await p.submit(); await p.submit(); assert.equal(requests.length,2); assert.equal(requests[0].path,'/inventory/12/inbound'); assert.equal(requests[0].body.requestId,requests[1].body.requestId)} finally {admin.request=original}
})
test('read-only staff cannot submit forged product edits', async () => {
  const p=page(), original=admin.request
  p.data.current=modules[0];p.data.profile={permissions:['product:read']};p.data.operation={edit:true,create:true};p.data.fields=[];p.data.form={}
  admin.request=async()=>{throw new Error('should not reach server')}
  try {await p.submit(); assert.equal(p.data.busy,false)} finally {admin.request=original}
})

test('image uploads send the employee token as multipart and preserve consumer sessions on expiry', async () => {
  api.save({ accessToken: 'consumer', expiresIn: 3600 })
  wx.request = o => o.success({ statusCode: 200, data: { code: 200, data: { accessToken: 'employee', expiresIn: 3600, admin: {} } } })
  await admin.login('staff', 'password')
  wx.uploadFile = o => {
    assert.match(o.url, /\/admin\/products\/images$/)
    assert.equal(o.filePath, '/tmp/cover.png')
    assert.equal(o.name, 'file')
    assert.equal(o.header.Authorization, 'Bearer employee')
    assert.equal(o.header['Content-Type'], undefined)
    o.success({ statusCode: 200, data: JSON.stringify({ code: 200, data: { url: '/api/v1/media/products/cover.png' } }) })
  }
  assert.deepEqual(await admin.uploadImage('/tmp/cover.png'), { url: '/api/v1/media/products/cover.png' })
  wx.uploadFile = o => o.success({ statusCode: 401, data: JSON.stringify({ message: 'expired' }) })
  await assert.rejects(admin.uploadImage('/tmp/cover.png'), /expired/)
  assert.equal(admin.session(), null)
  assert.equal(api.session().token, 'consumer')
})

function imageEditor() {
  const p = page()
  p.data.current = modules.find(m => m.key === 'products')
  p.data.profile = { permissions: ['product:write'] }
  p.baseFields = p.data.current.fields
  p.data.form = { coverUrl: '/existing-cover.png', detailImages: '/existing-detail.png' }
  p.data.fields = require('../utils/management').prepareFields(p.baseFields, p.data.form, {}, 'products')
  return p
}

test('product detail image selection keeps successful uploads when another file fails', async () => {
  const p = imageEditor(), original = admin.uploadImage, paths = []
  wx.chooseMedia = o => {
    assert.equal(o.count, 9)
    o.success({ tempFiles: [{ tempFilePath: '/tmp/one.png', size: 20 }, { tempFilePath: '/tmp/two.png', size: 20 }] })
  }
  admin.uploadImage = async path => {
    paths.push(path)
    if (path.includes('two')) throw new Error('网络中断')
    return { url: '/api/v1/media/products/one.png' }
  }
  try {
    await p.chooseImages({ currentTarget: { dataset: { key: 'detailImages' } } })
    assert.deepEqual(paths, ['/tmp/one.png', '/tmp/two.png'])
    assert.equal(p.data.form.detailImages, '/existing-detail.png\n/api/v1/media/products/one.png')
    assert.match(p.data.error, /网络中断/)
    assert.equal(p.data.busy, false)
    assert.equal(p.data.fields.find(f => f.key === 'detailImages').images.length, 2)
    p.changeImage({ currentTarget: { dataset: { key: 'detailImages', index: 1, offset: -1 } } })
    assert.equal(p.data.form.detailImages, '/api/v1/media/products/one.png\n/existing-detail.png')
    p.changeImage({ currentTarget: { dataset: { key: 'detailImages', index: 0 } } })
    assert.equal(p.data.form.detailImages, '/existing-detail.png')
  } finally { admin.uploadImage = original }
})

test('cancelled or failed cover selection preserves the existing image and blocks read-only uploads', async () => {
  const p = imageEditor(), original = admin.uploadImage
  wx.chooseMedia = o => o.fail({ errMsg: 'chooseMedia:fail cancel' })
  await p.chooseImages({ currentTarget: { dataset: { key: 'coverUrl' } } })
  assert.equal(p.data.error, '')
  assert.equal(p.data.form.coverUrl, '/existing-cover.png')
  wx.chooseMedia = o => o.success({ tempFiles: [{ tempFilePath: '/tmp/cover.png', size: 20 }] })
  admin.uploadImage = async () => { throw new Error('图片无效') }
  try {
    await p.chooseImages({ currentTarget: { dataset: { key: 'coverUrl' } } })
    assert.equal(p.data.form.coverUrl, '/existing-cover.png')
    assert.match(p.data.error, /图片无效/)
    p.data.profile = { permissions: ['product:read'] }
    wx.chooseMedia = () => { throw new Error('must not open file selection') }
    await p.chooseImages({ currentTarget: { dataset: { key: 'coverUrl' } } })
  } finally { admin.uploadImage = original }
})

test('oversized images and a full detail gallery are rejected before HTTP upload', async () => {
  const p = imageEditor()
  wx.chooseMedia = o => o.success({ tempFiles: [{ tempFilePath: '/tmp/large.png', size: 10 * 1024 * 1024 + 1 }] })
  await p.chooseImages({ currentTarget: { dataset: { key: 'coverUrl' } } })
  assert.match(p.data.error, /10MB/)
  p.data.form.detailImages = Array.from({ length: 20 }, (_, i) => '/' + i + '.png').join('\n')
  wx.chooseMedia = () => { throw new Error('must not open file selection') }
  await p.chooseImages({ currentTarget: { dataset: { key: 'detailImages' } } })
  assert.match(p.data.error, /最多 20 张/)
})

test('newly uploaded image previews use the API origin while built-in images use the asset origin', () => {
  const config = require('../config')
  assert.equal(api.image('/api/v1/media/products/one.png'), config.apiBase + '/media/products/one.png')
  assert.equal(api.image('/images/products/legacy.png'), config.assetBase + '/images/products/legacy.png')
})
