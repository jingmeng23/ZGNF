// All API calls use fixtures so browser checks do not alter business records.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'

const origin = process.env.SMOKE_BASE_URL || process.argv[2] || 'http://127.0.0.1:5173'
assert(
  ['127.0.0.1', 'localhost'].includes(new URL(origin).hostname),
  'Use a local frontend server.',
)
const browserPath =
  process.env.BROWSER_BIN || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
assert(existsSync(browserPath), 'Set BROWSER_BIN to your Chrome or Edge executable.')
assert((await fetch(origin)).ok, `Start the frontend on ${origin} first.`)

const profile = mkdtempSync(join(tmpdir(), 'zgnf-storefront-check-'))
const artifacts = resolve('../target/storefront-visual-check')
mkdirSync(artifacts, { recursive: true })
const browser = spawn(
  browserPath,
  [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--remote-debugging-port=0',
    `--user-data-dir=${profile}`,
    'about:blank',
  ],
  { windowsHide: true, stdio: 'ignore' },
)
let socket
let id = 0
let scenario = 'normal'
const pending = new Map()
const errors = []
const requests = []

async function until(check, description) {
  for (let index = 0; index < 120; index++) {
    if (await check()) return
    await delay(100)
  }
  throw new Error(`Timed out: ${description}`)
}

function send(method, params = {}) {
  return new Promise((resolveCall, reject) => {
    const requestId = ++id
    const timeout = setTimeout(() => {
      pending.delete(requestId)
      reject(new Error(`CDP timeout: ${method}`))
    }, 10000)
    pending.set(requestId, {
      resolve: (value) => {
        clearTimeout(timeout)
        resolveCall(value)
      },
      reject: (error) => {
        clearTimeout(timeout)
        reject(error)
      },
    })
    socket.send(JSON.stringify({ id: requestId, method, params }))
  })
}

async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
  return result.result.value
}

// Fixtures are isolated to CDP interception; no business API writes reach the server.
const fixtures = Array.from({ length: 16 }, (_, i) => ({
  id: String(i + 1),
  categoryId: i % 2 ? '2' : '1',
  name: `${i % 2 ? '唯怡饮品' : '百事可乐'}（接口测试 ${i + 1}）`,
  subtitle: '自动化验收数据，仅用于验证页面与购物流程',
  coverUrl: i % 2 ? '/images/products/唯怡.jpg' : '/images/products/pepsi.png',
  price: i + 3.5,
  originalPrice: null,
  sales: i,
  availableStock: 5,
  status: 'on_sale',
  statusMessage: '已上架',
  purchasable: true,
  skuCount: i === 1 ? 2 : 0,
}))
let cartQuantity = 0,
  failAdd = false
const cartData = () => ({
  items: [],
  invalidItems: [],
  total: 0,
  page: 1,
  size: 20,
  totalQuantity: cartQuantity,
  selectedQuantity: 0,
  selectedAmount: 0,
})
const matrixData = () => ({
  dimensions: [
    {
      name: '测试规格',
      options: [
        { value: 'A', disabled: false },
        { value: 'B', disabled: true },
      ],
    },
  ],
  selectedSkuId: null,
  skus: [
    {
      id: '201',
      name: '接口测试规格 A',
      specs: { 测试规格: 'A' },
      price: 12,
      imageUrl: '/images/products/pepsi.png',
      availableStock: 3,
      selectable: true,
      code: 'TEST-A',
    },
    {
      id: '202',
      name: '接口测试规格 B',
      specs: { 测试规格: 'B' },
      price: 14,
      availableStock: 0,
      selectable: false,
      reason: '售罄',
      code: 'TEST-B',
    },
  ],
})
async function intercept({ requestId, request }) {
  const url = new URL(request.url),
    path = url.pathname
  requests.push({
    path,
    query: Object.fromEntries(url.searchParams),
    method: request.method,
    body: request.postData,
  })
  let data = null,
    status = 200,
    message = 'success'
  if (path.endsWith('/categories'))
    data = [
      { id: '1', name: '碳酸饮品', sort: 0 },
      { id: '2', name: '其他饮品', sort: 1 },
    ]
  else if (path.endsWith('/mall/search') || path.endsWith('/mall/products')) {
    const q = Object.fromEntries(url.searchParams)
    if (q.keyword === 'slow') await delay(600)
    if (scenario === 'failure') {
      status = 503
      message = '测试：商品服务暂时不可用'
    }
    let rows = fixtures.filter(
      (p) =>
        (!q.keyword || p.name.includes(q.keyword)) &&
        (!q.categoryId || p.categoryId === q.categoryId) &&
        (!q.minPrice || p.price >= Number(q.minPrice)) &&
        (!q.maxPrice || p.price <= Number(q.maxPrice)),
    )
    if (q.sort === 'price' || q.sort === 'sales')
      rows.sort((a, b) => (a[q.sort] - b[q.sort]) * (q.order === 'asc' ? 1 : -1))
    const page = Number(q.page || 1),
      size = Number(q.size || 12)
    const results = {
      list: rows.slice((page - 1) * size, page * size),
      total: rows.length,
      pages: Math.ceil(rows.length / size),
      page,
      size,
    }
    data = path.endsWith('/search')
      ? { results, recommendations: [], message: '', degraded: false }
      : results
  } else if (/\/mall\/products\/[^/]+\/skus$/.test(path)) {
    data = matrixData()
    const selection = JSON.parse(url.searchParams.get('selection') || '{}')
    if (selection.测试规格 === 'A') data.selectedSkuId = '201'
  } else if (/\/mall\/products\/[^/]+$/.test(path)) {
    const id = path.split('/').at(-1)
    if (id === 'missing') {
      status = 404
      message = '商品不存在或已下架'
    }
    let p = fixtures.find((p) => p.id === id) || fixtures[0]
    if (id === 'sold')
      p = {
        ...p,
        status: 'sold_out',
        availableStock: 0,
        purchasable: false,
        statusMessage: '商品已售罄',
      }
    if (id === 'off')
      p = { ...p, status: 'off_sale', purchasable: false, statusMessage: '商品已下架' }
    data = {
      product: p,
      mainImages: [p.coverUrl, '/images/products/pepsi.png'].filter(
        (x, i, a) => a.indexOf(x) === i,
      ),
      description:
        '<script>未经信任的内容应作为文本展示</script>\n这是接口测试介绍，不代表真实商品资料。',
      detailImages: [],
      servicePromises: [],
      review: { count: 0 },
      attributes: { 数据来源: ['自动化测试响应'] },
      matrix: id === '2' ? matrixData() : { dimensions: [], skus: [], selectedSkuId: null },
    }
  } else if (path.endsWith('/cart/items') && request.method === 'POST') {
    await delay(250)
    if (failAdd) {
      status = 400
      message = '测试：库存不足'
    } else cartQuantity += JSON.parse(request.postData).quantity
    data = cartData()
  } else if (path.endsWith('/cart')) data = cartData()
  else if (path.includes('/favorites/')) data = false
  await send('Fetch.fulfillRequest', {
    requestId,
    responseCode: status,
    responseHeaders: [{ name: 'Content-Type', value: 'application/json; charset=utf-8' }],
    body: Buffer.from(JSON.stringify({ code: status, message, data })).toString('base64'),
  })
}
const ready = (selector) =>
  until(() => evaluate(`!!document.querySelector(${JSON.stringify(selector)})`), selector)
const click = (selector) => evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`)
const fill = (selector, value) =>
  evaluate(
    `{ const input = document.querySelector(${JSON.stringify(selector)}); input.value = ${JSON.stringify(value)}; input.dispatchEvent(new Event('input', { bubbles: true })); }`,
  )
const navigate = async (hash) => {
  await evaluate(`location.hash = ${JSON.stringify(hash)}`)
  await delay(150)
}
async function screenshot(name) {
  await evaluate(
    `{ let label = document.getElementById('fixture-label'); if (!label) { label = document.createElement('div'); label.id = 'fixture-label'; label.textContent = '接口模拟验收 · 非真实商品资料'; label.style.cssText = 'position:fixed;right:4px;top:3px;font:10px sans-serif;color:#777;z-index:1000;background:#fff'; document.body.append(label); } }`,
  )
  const mobile = await evaluate('innerWidth < 621')
  const result = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: !mobile,
  })
  writeFileSync(join(artifacts, name + '.png'), Buffer.from(result.data, 'base64'))
}
try {
  const portFile = join(profile, 'DevToolsActivePort')
  await until(() => existsSync(portFile), 'Edge startup')
  const port = readFileSync(portFile, 'utf8').split('\n')[0]
  const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json()
  socket = new WebSocket(targets.find((target) => target.type === 'page').webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    socket.onopen = resolve
    socket.onerror = reject
  })
  socket.onmessage = (event) => {
    const result = JSON.parse(event.data)
    if (result.method === 'Runtime.exceptionThrown')
      errors.push(result.params.exceptionDetails.text)
    if (result.method === 'Fetch.requestPaused')
      intercept(result.params).catch((e) => errors.push(e.message))
    if (result.id && pending.has(result.id)) {
      const call = pending.get(result.id)
      pending.delete(result.id)
      if (result.error) call.reject(new Error(result.error.message))
      else call.resolve(result.result)
    }
  }
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Fetch.enable', { patterns: [{ urlPattern: '*/api/v1/*' }] })
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `sessionStorage.setItem('zgnf.access-session', JSON.stringify({ token: 'storefront-test-token', expiresAt: Date.now() + 3600000 }))`,
  })
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1050,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await send('Page.navigate', { url: `${origin}/#/catalog` })
  await ready('.product-card')
  await ready('.category-menu button:nth-child(3)')
  assert.equal(await evaluate(`document.querySelectorAll('.product-card').length`), 12)
  assert.equal(
    await evaluate(
      `getComputedStyle(document.querySelector('.shop-grid')).gridTemplateColumns.split(' ').length`,
    ),
    4,
  )
  await screenshot('catalog-desktop')
  await click('.catalog-pagination button:last-child')
  await until(() => evaluate(`document.querySelectorAll('.product-card').length === 4`), 'page 2')
  await fill('.mall-search input', '百事')
  await evaluate(`document.querySelector('.mall-search').requestSubmit()`)
  await until(
    () => evaluate(`document.querySelectorAll('.product-card').length === 8`),
    'search resets pagination',
  )
  assert.equal(await evaluate(`!!document.querySelector('.catalog-pagination')`), false)
  await click('.category-menu button:nth-child(3)')
  await until(() => evaluate(`location.hash.includes('categoryId=1')`), 'category URL')
  await fill('.price-range label:first-of-type input', '5')
  await fill('.price-range label:last-of-type input', '12')
  await evaluate(`document.querySelector('.price-range').requestSubmit()`)
  await until(() => evaluate(`location.hash.includes('maxPrice=12')`), 'price URL')
  await click('.sort-menu button:nth-child(3)')
  await until(
    () =>
      evaluate(
        `document.querySelectorAll('.product-card').length === 4 && location.hash.includes('order=asc')`,
      ),
    'combined filters',
  )
  assert.deepEqual(
    await evaluate(`Array.from(document.querySelectorAll('.card-price'), x => x.textContent)`),
    ['¥5.50', '¥7.50', '¥9.50', '¥11.50'],
  )
  await click('.sort-menu button:nth-child(3)')
  await until(
    () =>
      evaluate(
        `location.hash.includes('order=desc') && document.querySelector('.card-price')?.textContent === '¥11.50'`,
      ),
    'descending price across full query',
  )
  await click('.sort-menu button:nth-child(3)')
  await until(
    () =>
      evaluate(
        `location.hash.includes('order=asc') && document.querySelector('.card-price')?.textContent === '¥5.50'`,
      ),
    'ascending price restored',
  )
  const combinedUrl = await evaluate('location.hash')
  assert.match(combinedUrl, /categoryId=1/)
  const countBeforeInvalid = requests.filter((x) => x.path.endsWith('/mall/search')).length
  for (const [min, max] of [
    ['-1', '12'],
    ['abc', '12'],
    ['20', '10'],
    ['1.001', '12'],
  ]) {
    await fill('.price-range label:first-of-type input', min)
    await fill('.price-range label:last-of-type input', max)
    await evaluate(`document.querySelector('.price-range').requestSubmit()`)
    await ready('#price-error')
  }
  assert.equal(requests.filter((x) => x.path.endsWith('/mall/search')).length, countBeforeInvalid)
  await send('Page.reload')
  await ready('.product-card')
  assert.equal(await evaluate(`document.querySelectorAll('.product-card').length`), 4)
  await evaluate(`window.scrollTo(0, 350)`)
  const scroll = await evaluate('window.scrollY')
  await click('.product-image')
  await ready('.purchase-info')
  await click('.breadcrumbs a:last-of-type')
  await ready('.product-card')
  await delay(100)
  assert.equal(await evaluate('location.hash'), combinedUrl)
  assert(Math.abs((await evaluate('window.scrollY')) - scroll) < 4, 'list scroll restores')
  await navigate('/catalog?q=slow')
  await delay(50)
  await navigate('/catalog?q=百事')
  await delay(700)
  assert.equal(await evaluate(`document.querySelectorAll('.product-card').length`), 8)
  await navigate('/catalog?q=unmatched')
  await ready('.shop-empty')
  assert.match(await evaluate(`document.querySelector('.result-heading').textContent`), /共 0 件/)
  await navigate('/products/1')
  await ready('.purchase-info')
  assert.equal(await evaluate(`!!document.querySelector('.detail-description script')`), false)
  await screenshot('detail-desktop')
  for (const value of ['0', '1.5', '6', '-1', 'abc']) {
    await fill('#purchase-quantity', value)
    await ready('#quantity-error')
    assert(await evaluate(`document.querySelector('.add-button').disabled`))
  }
  await fill('#purchase-quantity', '3')
  const writes = () => requests.filter((x) => x.path.endsWith('/cart/items') && x.method === 'POST')
  const before = writes().length
  await evaluate(
    `document.querySelector('.add-button').click(); document.querySelector('.add-button').click()`,
  )
  await ready('.action-notice')
  assert.equal(writes().length, before + 1)
  assert.equal(JSON.parse(writes().at(-1).body).quantity, 3)
  assert.equal(await evaluate(`document.querySelector('.shop-cart b').textContent`), '3')
  failAdd = true
  await click('.add-button')
  await ready('.purchase-info .error-text')
  failAdd = false
  await click('.detail-cover')
  await ready('dialog[open]')
  assert(await evaluate(`document.activeElement.classList.contains('preview-close')`))
  await send('Input.dispatchKeyEvent', {
    type: 'keyDown',
    key: 'Escape',
    code: 'Escape',
    windowsVirtualKeyCode: 27,
  })
  await until(() => evaluate(`!document.querySelector('dialog[open]')`), 'escape closes preview')
  await until(
    () => evaluate(`document.activeElement.classList.contains('detail-cover')`),
    'preview focus returns to gallery',
  )
  await navigate('/products/2')
  await ready('.sku-options')
  assert.match(await evaluate(`document.querySelector('.selection-hint').textContent`), /请选择/)
  assert(await evaluate(`document.querySelector('.sku-options button:last-child').disabled`))
  await click('.sku-options button:first-child')
  await until(
    () => evaluate(`document.querySelector('.detail-price-box strong').textContent === '¥12.00'`),
    'SKU price',
  )
  assert.match(await evaluate(`document.querySelector('.stock-state').textContent`), /剩余 3 件/)
  assert.match(
    await evaluate(`document.querySelector('.detail-cover img').getAttribute('src')`),
    /pepsi/,
  )
  assert(await evaluate(`document.querySelector('.add-button').disabled`))
  await screenshot('detail-sku-desktop')
  for (const state of ['sold', 'off', 'missing']) {
    await navigate(`/products/${state}`)
    if (state === 'missing') await ready('.detail-error')
    else {
      await ready('.availability')
      assert(await evaluate(`document.querySelector('.add-button').disabled`))
    }
  }
  scenario = 'failure'
  await navigate('/catalog')
  await ready('.mall-page [role="alert"]')
  scenario = 'normal'
  await click('.mall-page [role="alert"] button')
  await ready('.product-card')
  const layouts = []
  for (const width of [1024, 768, 390, 375, 320]) {
    await send('Emulation.setDeviceMetricsOverride', {
      width,
      height: 844,
      deviceScaleFactor: 1,
      mobile: width < 621,
    })
    const state = await evaluate(
      `({ width: innerWidth, overflow: document.documentElement.scrollWidth > innerWidth, columns: getComputedStyle(document.querySelector('.shop-grid')).gridTemplateColumns.split(' ').length })`,
    )
    assert.equal(state.overflow, false, `catalog overflow at ${width}`)
    layouts.push(state)
    if (width === 390) {
      await click('.mobile-filter')
      assert.equal(
        await evaluate(`getComputedStyle(document.querySelector('.price-filters')).display`),
        'block',
      )
      await screenshot('catalog-mobile')
    }
    await navigate('/products/1')
    await ready('.purchase-info')
    assert.equal(
      await evaluate(`document.documentElement.scrollWidth > innerWidth`),
      false,
      `detail overflow at ${width}`,
    )
    if (width === 390) {
      assert.equal(
        await evaluate(`getComputedStyle(document.querySelector('.mobile-purchase')).position`),
        'fixed',
      )
      assert.equal(await evaluate(`!!document.querySelector('.bottom-nav')`), false)
      await screenshot('detail-mobile')
    }
    await navigate('/catalog')
    await ready('.product-card')
  }
  await navigate('/products/1')
  await ready('.purchase-info')
  await evaluate(`sessionStorage.removeItem('zgnf.access-session')`)
  await click('.mobile-purchase .shop-button')
  await until(() => evaluate(`location.hash.startsWith('#/login')`), 'login redirect')
  assert.deepEqual(errors, [])
  const report = {
    fixtureOnly: true,
    layouts,
    passed: [
      'combined filters and totals',
      'pagination reset and one-page hiding',
      'invalid price rejection',
      'refresh and list scroll restore',
      'stale response rejection',
      'empty and failed search recovery',
      'quantity validation',
      'single product add and duplicate guard',
      'authoritative cart count',
      'add failure',
      'preview Escape and focus restoration',
      'SKU price/stock/image and unavailable options',
      'unsupported SKU purchase blocked',
      'sold/off/missing states',
      'mobile filter and fixed purchase bar',
      'login redirect',
    ],
    screenshots: artifacts,
  }
  writeFileSync(join(artifacts, 'report.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report))
} finally {
  if (socket?.readyState === WebSocket.OPEN) {
    try {
      await send('Browser.close')
    } catch {}
    socket.close()
  }
  browser.kill()
}
