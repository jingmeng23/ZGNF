const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const http = require('node:http')
const os = require('node:os')
const path = require('node:path')
const { once } = require('node:events')
const { createAssetServer } = require('../scripts/serve-assets.cjs')

test('home quick add is authenticated, idempotent and blocked for multi-SKU products', async () => {
  const api = require('../utils/api')
  const { page, storage } = require('./helpers.cjs')
  storage()
  api.save({ accessToken: 'customer', expiresIn: 3600 })
  const originalRequest = api.request
  const requests = []
  api.request = async (...args) => { requests.push(args); return {} }
  try {
    const home = page('home')
    home.data.products = [{ id: '2', purchasable: true, skuCount: 0 }]
    await home.addProduct({ currentTarget: { dataset: { id: '2' } } })
    assert.equal(requests.length, 1)
    assert.equal(requests[0][0], '/cart/items')
    assert.deepEqual(JSON.parse(JSON.stringify(requests[0][2])), { productId: '2', quantity: 1 })
    assert.ok(requests[0][3]['Idempotency-Key'])
    home.data.products = [{ id: '3', purchasable: true, skuCount: 2 }]
    await home.addProduct({ currentTarget: { dataset: { id: '3' } } })
    assert.equal(requests.length, 1)
  } finally { api.request = originalRequest }
})

test('product detail resolves a spec combination and refuses to add an unsupported multi-SKU cart item', async () => {
  const api = require('../utils/api')
  const { page, storage } = require('./helpers.cjs')
  storage()
  api.save({ accessToken: 'customer', expiresIn: 3600 })
  const originalRequest = api.request
  const product = page('product')
  const skus = [
    { id: '10', name: '天然气款', specs: { 气源: '天然气' }, selectable: true, price: 100, availableStock: 4 },
    { id: '11', name: '液化气款', specs: { 气源: '液化气' }, selectable: true, price: 120, availableStock: 2 },
  ]
  const matrix = { dimensions: [{ name: '气源', options: [{ value: '天然气' }, { value: '液化气' }] }] }
  product.data.detail = { matrix, product: { id: '4', purchasable: true } }
  product.data.skus = skus
  product.data.skuDimensions = product.dimensionOptions(matrix, {}, skus)
  product.chooseSpec({ currentTarget: { dataset: { index: '0' } }, detail: { value: '1' } })
  assert.equal(product.data.selectedSku.id, '11')
  let requests = 0
  api.request = async () => { requests++ }
  try {
    await product.add()
    assert.equal(requests, 0)
    assert.match(product.data.error, /尚不能保存规格编号/)
  } finally { api.request = originalRequest }
})

test('home and catalog use packaged covers while uploaded covers keep the API origin', async () => {
  const api = require('../utils/api')
  const config = require('../config')
  const { page, storage } = require('./helpers.cjs')
  storage()
  const products = [
    { id: '2', coverUrl: '/images/products/pepsi.png', skuCount: 0 },
    { id: '3', coverUrl: '/images/products/唯怡.jpg', skuCount: 0 },
    { id: '4', coverUrl: '/api/v1/media/products/uploaded.jpg', skuCount: 0 },
  ]
  wx.request = options => {
    const result = { list: products, total: 3, pages: 1 }
    options.success({ statusCode: 200, data: { code: 200, data: options.url.includes('/mall/search?') ? { results: result } : result } })
  }
  for (const name of ['home', 'catalog']) {
    const instance = page(name)
    await (name === 'home' ? instance.loadProducts() : instance.load())
    assert.equal(instance.data.error, '')
    assert.deepEqual(Array.from(instance.data.products, product => product.image), [
      '/assets/products/pepsi.png', '/assets/products/weiyi.jpg',
      config.apiBase.replace(/\/$/, '') + '/media/products/uploaded.jpg',
    ])
    for (const product of instance.data.products.slice(0, 2)) {
      const packaged = await fs.readFile(path.join(__dirname, '..', product.image))
      const original = await fs.readFile(path.join(__dirname, '../../frontend/public', product.coverUrl))
      assert.ok(packaged.length > 0)
      assert.deepEqual(packaged, original)
    }
  }
  assert.equal(api.image('/images/products/additional.jpg'), config.assetBase.replace(/\/$/, '') + '/images/products/additional.jpg')
})

test('local image server returns image bytes and limits access to public images', async t => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'zgnf-image-test-'))
  const root = path.join(directory, 'images')
  await fs.mkdir(path.join(root, 'products'), { recursive: true })
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aEAAAAABJRU5ErkJggg==', 'base64')
  await fs.writeFile(path.join(root, 'products', 'pepsi.png'), png)
  await fs.writeFile(path.join(root, 'products', '唯怡.png'), png)
  await fs.writeFile(path.join(root, 'private.json'), '{"private":true}')
  await fs.writeFile(path.join(root, '.hidden.png'), png)
  await fs.writeFile(path.join(directory, 'outside.png'), png)
  const server = createAssetServer(root)
  t.after(async () => {
    server.closeAllConnections()
    await new Promise(resolve => server.close(resolve))
    await fs.rm(directory, { recursive: true, force: true })
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')
  function request(url, method = 'GET') {
    return new Promise((resolve, reject) => {
      const req = http.request({ hostname: '127.0.0.1', port: server.address().port, path: url, method }, response => {
        const chunks = []
        response.on('data', chunk => chunks.push(chunk))
        response.on('end', () => resolve({ status: response.statusCode, headers: response.headers, body: Buffer.concat(chunks) }))
        response.on('error', reject)
      })
      req.on('error', reject)
      req.end()
    })
  }

  await t.test('English and encoded Chinese filenames return the original image with its MIME type', async () => {
    for (const url of ['/images/products/pepsi.png', '/images/products/' + encodeURIComponent('唯怡.png') + '?version=1']) {
      const response = await request(url)
      assert.equal(response.status, 200)
      assert.equal(response.headers['content-type'], 'image/png')
      assert.equal(response.headers['content-length'], String(png.length))
      assert.equal(response.headers['access-control-allow-origin'], '*')
      assert.equal(response.headers['x-content-type-options'], 'nosniff')
      assert.deepEqual(response.body, png)
    }
    const response = await request('/images/products/pepsi.png', 'HEAD')
    assert.equal(response.status, 200)
    assert.equal(response.headers['content-length'], String(png.length))
    assert.equal(response.body.length, 0)
  })
  await t.test('missing images and unrelated resources return 404 without an API or HTML fallback', async () => {
    for (const url of ['/images/missing.jpg', '/api/v1/mall/products', '/images/private.json', '/', '/images/products/']) {
      const response = await request(url)
      assert.equal(response.status, 404, url)
      assert.match(response.headers['content-type'], /^text\/plain/)
    }
  })
  await t.test('traversal, hidden files, invalid encoding and writes are rejected', async () => {
    for (const url of ['/images/../outside.png', '/images/%2e%2e/outside.png', '/images/%2e%2e%5coutside.png', '/images/.hidden.png', '/images/a%00.png', '/images/%E5']) {
      assert.equal((await request(url)).status, 400, url)
    }
    const response = await request('/images/products/pepsi.png', 'POST')
    assert.equal(response.status, 405)
    assert.equal(response.headers.allow, 'GET, HEAD')
  })
  await t.test('symlinks cannot expose images outside the public image directory', async t => {
    try { await fs.symlink(path.join(directory, 'outside.png'), path.join(root, 'outside.png')) }
    catch (error) { if (['EPERM', 'EACCES'].includes(error.code)) return t.skip('Creating file symlinks requires Windows permission'); throw error }
    assert.equal((await request('/images/outside.png')).status, 404)
  })
})
