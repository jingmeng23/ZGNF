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
const artifacts = resolve('../target/cart-visual-check')
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
  if (result.exceptionDetails)
    throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
  return result.result.value
}

// Fixtures are isolated to CDP interception; no business API writes reach the server.
const initialRows = () => [
  {
    id: '9007199254740993',
    productId: '1',
    productName: '百事可乐 · 自动化接口验收商品',
    coverUrl: '/images/products/pepsi.png',
    quantity: 2,
    unitPrice: 12.35,
    availableStock: 8,
    selected: true,
    available: true,
    priceChanged: false,
  },
  {
    id: '2',
    productId: '2',
    productName: '唯怡饮品（长商品名称与大金额排版测试，非真实商品资料）',
    coverUrl: '/images/products/唯怡.jpg',
    quantity: 3,
    unitPrice: 12345.67,
    availableStock: 20,
    selected: false,
    available: true,
    priceChanged: false,
  },
  {
    id: '3',
    productId: '3',
    productName: '图片缺失测试商品',
    coverUrl: '/missing-cart-image.png',
    quantity: 1,
    unitPrice: 30,
    availableStock: 1,
    selected: true,
    available: true,
    priceChanged: true,
  },
  {
    id: '4',
    productId: '4',
    productName: '失效测试商品',
    coverUrl: null,
    quantity: 1,
    unitPrice: 9,
    availableStock: 0,
    selected: true,
    available: false,
    priceChanged: false,
  },
]
let rows = initialRows(),
  failQuantity = false,
  failOrder = false,
  failDelete = false,
  stalePrice = false,
  failPreview = false
const couponRows = [
  {
    id: 'c1',
    couponId: 'c',
    name: '验收订单券',
    amount: 5,
    minimumAmount: 30,
    status: 'unused',
    startTime: '2020-01-01 00:00:00',
    expireTime: '2099-01-01 00:00:00',
    usedOrderId: null,
  },
  {
    id: 'c2',
    couponId: 'c',
    name: '未达门槛测试券',
    amount: 20,
    minimumAmount: 999999,
    status: 'unused',
    startTime: '2020-01-01 00:00:00',
    expireTime: '2099-01-01 00:00:00',
    usedOrderId: null,
  },
]
const total = () =>
  rows
    .filter((r) => r.selected && r.available)
    .reduce((s, r) => s + Math.round(r.unitPrice * 100) * r.quantity, 0) / 100
const cartData = () => ({
  items: rows
    .filter((r) => r.available)
    .map((r) => ({ ...r, subtotal: (Math.round(r.unitPrice * 100) * r.quantity) / 100 })),
  invalidItems: rows
    .filter((r) => !r.available)
    .map((r) => ({ ...r, subtotal: r.quantity * r.unitPrice })),
  total: rows.filter((r) => r.available).length,
  page: 1,
  size: 100,
  totalQuantity: rows.reduce((s, r) => s + r.quantity, 0),
  selectedQuantity: rows
    .filter((r) => r.available && r.selected)
    .reduce((s, r) => s + r.quantity, 0),
  selectedAmount: total(),
})
const address = {
  id: 'a1',
  receiver: '接口验收收货人',
  phone: '13800000000',
  province: '四川省',
  city: '自贡市',
  district: '自流井区',
  detail: '自动化测试地址',
  fullAddress: '四川省自贡市自流井区自动化测试地址',
  isDefault: true,
}
async function intercept({ requestId, request }) {
  const url = new URL(request.url),
    path = url.pathname.replace('/api/v1', ''),
    method = request.method,
    body = request.postData ? JSON.parse(request.postData) : {}
  requests.push({ path, method, body, query: Object.fromEntries(url.searchParams) })
  let data = null,
    status = 200,
    message = 'success'
  if (path === '/cart' && method === 'GET') {
    if (scenario === 'failure') {
      status = 503
      message = '测试：购物车加载失败'
    }
    if (scenario === 'loading') await delay(800)
    if (stalePrice) {
      rows[0].unitPrice += 1
      stalePrice = false
    }
    data = cartData()
  } else if (/^\/cart\/items\/[^/]+$/.test(path) && method === 'PUT') {
    await delay(180)
    if (failQuantity) {
      status = 400
      message = '测试：库存不足'
      failQuantity = false
    } else rows.find((r) => r.id === path.split('/').at(-1)).quantity = body.quantity
    data = cartData()
  } else if (path.endsWith('/selection')) {
    const id = path.split('/')[3]
    for (const row of rows) if (!id || row.id === id) row.selected = body.selected && row.available
    data = cartData()
  } else if (path === '/cart/items' && method === 'DELETE') {
    if (failDelete) {
      status = 503
      message = '测试：删除失败'
      failDelete = false
    } else rows = rows.filter((r) => !body.ids.includes(r.id))
  } else if (path === '/users/me/addresses') data = [address]
  else if (path === '/user-coupons') {
    const amount = url.searchParams.get('orderAmount')
    const list = couponRows.filter(
      (c) => !amount || (Number(amount) >= c.minimumAmount && Number(amount) > c.amount),
    )
    data = { list, total: list.length, page: 1, size: 12, pages: 1 }
  } else if (path === '/orders/preview') {
    if (failPreview) {
      status = 503
      message = '测试：金额计算失败'
    }
    if (rows.some((r) => !r.available && r.selected)) {
      status = 400
      message = '失效商品不可结算'
    }
    const discount = body.userCouponId === 'c1' ? 5 : 0
    data = {
      orderNo: 'TEST-ORDER',
      checkoutToken: 'TEST-QUOTE',
      totalAmount: total(),
      discountAmount: discount,
      freightAmount: 7.5,
      fullReductionAmount: 0,
      couponDiscountAmount: discount,
      payAmount: Math.round((total() - discount + 7.5) * 100) / 100,
      items: rows
        .filter((r) => r.available && r.selected)
        .map((r) => ({
          productId: r.productId,
          productName: r.productName,
          quantity: r.quantity,
          unitPrice: r.unitPrice,
        })),
    }
  } else if (path === '/orders' && method === 'POST') {
    await delay(200)
    if (failOrder) {
      status = 503
      message = '测试：提交失败'
      failOrder = false
    } else {
      rows = rows.filter((r) => !r.selected || !r.available)
      data = { orderNo: 'TEST-ORDER' }
    }
  } else if (path.includes('/favorites/')) data = false
  await send('Fetch.fulfillRequest', {
    requestId,
    responseCode: status,
    responseHeaders: [{ name: 'Content-Type', value: 'application/json; charset=utf-8' }],
    body: Buffer.from(JSON.stringify({ code: status, message, data })).toString('base64'),
  })
}
const ready = (selector) =>
  until(() => evaluate(`!!document.querySelector(${JSON.stringify(selector)})`), selector)
const click = (selector) =>
  evaluate(
    `{ const element = document.querySelector(${JSON.stringify(selector)}); element.focus(); element.click(); }`,
  )
const fill = (selector, value) =>
  evaluate(
    `{ const input = document.querySelector(${JSON.stringify(selector)}); input.value = ${JSON.stringify(value)}; input.dispatchEvent(new Event('input', { bubbles: true })); }`,
  )
const settle = () =>
  until(
    () =>
      evaluate(
        `!document.querySelector('.cart-summary')?.getAttribute('aria-busy') || document.querySelector('.cart-summary').getAttribute('aria-busy') === 'false'`,
      ),
    'cart settled',
  )
async function pageReload() {
  const before = await evaluate('window.__cartDocument')
  await send('Page.reload')
  await until(
    () =>
      evaluate(`!!window.__cartDocument && window.__cartDocument !== ${JSON.stringify(before)}`),
    'new document after reload',
  )
}
async function reload() {
  await pageReload()
  await ready('.cart-product-row')
  await settle()
}
async function screenshot(name) {
  await evaluate(
    `{ let label = document.getElementById('fixture-label'); if (!label) { label = document.createElement('div'); label.id = 'fixture-label'; label.textContent = '接口模拟验收 · 非真实商品资料'; label.style.cssText = 'position:fixed;right:4px;top:3px;font:10px sans-serif;color:#777;z-index:1000;background:#fff'; document.body.append(label); } }`,
  )
  const result = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: false,
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
      result.error ? call.reject(new Error(result.error.message)) : call.resolve(result.result)
    }
  }
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Fetch.enable', { patterns: [{ urlPattern: '*/api/v1/*' }] })
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `window.__cartDocument = crypto.randomUUID(); if (!sessionStorage.getItem('cart-test-logged-out')) sessionStorage.setItem('zgnf.access-session', JSON.stringify({ token: 'cart-test-token', expiresAt: Date.now() + 3600000 }))`,
  })
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1050,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await send('Page.navigate', { url: `${origin}/#/cart` })
  await ready('.cart-product-row')
  await settle()
  assert.equal(await evaluate(`document.querySelector('.cart-toolbar input').indeterminate`), true)
  assert.equal(
    await evaluate(`document.querySelector('[data-testid=cart-total]').textContent`),
    '¥54.70',
  )
  assert.equal(rows.find((r) => r.id === '4').selected, false)
  await screenshot('cart-desktop')
  await click('.cart-toolbar input')
  await settle()
  assert(rows.filter((r) => r.available).every((r) => r.selected))
  assert.equal(rows.find((r) => r.id === '4').selected, false)
  await click('.cart-toolbar input')
  await settle()
  assert.equal(await evaluate(`document.querySelector('.checkout-button').disabled`), true)
  await click('.cart-product-row:first-of-type .row-check input')
  await settle()
  const quantityInput = '[data-item-id="9007199254740993"] .quantity-control input'
  for (const value of ['', '0', '-2', 'abc', '1.5', '9']) {
    const before = requests.filter(
      (r) => r.path === '/cart/items/9007199254740993' && r.method === 'PUT',
    ).length
    await fill(quantityInput, value)
    await delay(450)
    assert.equal(
      requests.filter((r) => r.path === '/cart/items/9007199254740993' && r.method === 'PUT')
        .length,
      before,
    )
    assert.equal(await evaluate(`document.querySelector('.checkout-button').disabled`), true)
    assert(await evaluate(`!!document.querySelector('.quantity-error')`))
  }
  await fill(quantityInput, '3')
  await delay(410)
  await fill(quantityInput, '4')
  await settle()
  assert.equal(rows[0].quantity, 4)
  assert.equal(
    await evaluate(`document.querySelector(${JSON.stringify(quantityInput)}).value`),
    '4',
  )
  failQuantity = true
  await fill(quantityInput, '5')
  await settle()
  assert.equal(rows[0].quantity, 4)
  assert.equal(
    await evaluate(`document.querySelector(${JSON.stringify(quantityInput)}).value`),
    '4',
  )
  assert.match(await evaluate(`document.querySelector('.quantity-error').textContent`), /库存不足/)
  await fill(quantityInput, '2')
  await settle()
  await evaluate(
    `{ const select = document.querySelector('#cart-address'); select.value = 'a1'; select.dispatchEvent(new Event('change', { bubbles: true })); }`,
  )
  await settle()
  failPreview = true
  await evaluate(
    `{ const select = document.querySelector('#cart-address'); select.value = ''; select.dispatchEvent(new Event('change', { bubbles: true })); }`,
  )
  await settle()
  await evaluate(
    `{ const select = document.querySelector('#cart-address'); select.value = 'a1'; select.dispatchEvent(new Event('change', { bubbles: true })); }`,
  )
  await settle()
  assert.equal(await evaluate(`document.querySelector('.checkout-button').disabled`), true)
  assert.match(await evaluate(`document.querySelector('.quote-error').textContent`), /金额计算失败/)
  failPreview = false
  await click('.quote-error .text-button')
  await settle()
  await click('.coupon-section summary')
  assert.equal(await evaluate(`document.querySelector('input[value=c2]').disabled`), true)
  // Select another product to make the coupon eligible.
  await click('[data-item-id="3"] .row-check input')
  await settle()
  await click('input[value=c1]')
  await settle()
  assert.equal(
    await evaluate(`document.querySelector('[data-testid=cart-total]').textContent`),
    '¥57.20',
  )
  await click('[data-item-id="3"] .row-check input')
  await settle()
  assert.equal(await evaluate(`document.querySelector('input[value=""]').checked`), true)
  assert.match(
    await evaluate(`document.querySelector('.cart-notice.success').textContent`),
    /不适用/,
  )
  stalePrice = true
  await click('.checkout-button')
  await settle()
  assert.match(await evaluate(`document.querySelector('.cart-notice.error').textContent`), /变化/)
  await click('.checkout-button')
  await ready('.cart-dialog[open]')
  assert.equal(await evaluate(`document.activeElement.textContent.trim()`), '返回购物车')
  await send('Input.dispatchKeyEvent', {
    type: 'keyDown',
    key: 'Tab',
    code: 'Tab',
    windowsVirtualKeyCode: 9,
  })
  assert.equal(await evaluate(`!!document.activeElement.closest('.cart-dialog')`), true)
  await send('Input.dispatchKeyEvent', {
    type: 'keyDown',
    key: 'Escape',
    code: 'Escape',
    windowsVirtualKeyCode: 27,
  })
  await until(() => evaluate(`!document.querySelector('dialog[open]')`), 'dialog Escape close')
  await until(
    () => evaluate(`document.activeElement.classList.contains('checkout-button')`),
    'dialog focus returned after close event',
  )
  await settle()
  await click('.checkout-button')
  await ready('.cart-dialog[open]')
  failOrder = true
  await evaluate(
    `document.querySelector('.dialog-actions .primary-button').click(); document.querySelector('.dialog-actions .primary-button').click()`,
  )
  await until(
    () =>
      evaluate(
        `document.querySelector('.cart-dialog .inline-error')?.textContent.includes('提交失败')`,
      ),
    'order failure',
  )
  assert.equal(requests.filter((r) => r.path === '/orders' && r.method === 'POST').length, 1)
  const failedToken = requests.find((r) => r.path === '/orders' && r.method === 'POST').body
    .checkoutToken
  await click('.dialog-actions .primary-button')
  await until(
    () =>
      evaluate(`document.querySelector('.cart-notice.success')?.textContent.includes('已创建')`),
    'order success',
  )
  assert.equal(
    requests.filter((r) => r.path === '/orders' && r.method === 'POST').at(-1).body.checkoutToken,
    failedToken,
  )
  rows = initialRows()
  await reload()
  failDelete = true
  await click('.delete-selected')
  await ready('.cart-dialog[open]')
  assert.match(
    await evaluate(`document.querySelector('.cart-dialog').textContent`),
    /2 种商品，共 3 件/,
  )
  await click('.dialog-actions .primary-button')
  await until(
    () =>
      evaluate(
        `document.querySelector('.cart-dialog .inline-error')?.textContent.includes('删除失败')`,
      ),
    'delete failure',
  )
  assert.equal(rows.length, 4)
  await click('.dialog-actions .primary-button')
  await until(() => evaluate(`!document.querySelector('dialog[open]')`), 'batch delete')
  await settle()
  assert.deepEqual(
    rows.map((r) => r.id),
    ['2', '4'],
  )
  await evaluate(
    `Array.from(document.querySelectorAll('.invalid-list button')).find(b => b.textContent.includes('清理失效')).click()`,
  )
  await ready('.cart-dialog[open]')
  await click('.dialog-actions .primary-button')
  await until(() => evaluate(`!document.querySelector('.invalid-list')`), 'invalid cleanup')
  rows = initialRows()
  await reload()
  for (const width of [1440, 1280, 1024, 768, 620, 390, 375, 320]) {
    await send('Emulation.setDeviceMetricsOverride', {
      width,
      height: width < 621 ? 844 : 1050,
      deviceScaleFactor: 1,
      mobile: width < 621,
    })
    await delay(120)
    assert.equal(
      await evaluate('document.documentElement.scrollWidth <= innerWidth'),
      true,
      `no overflow at ${width}`,
    )
    if (width === 390) {
      assert.equal(await evaluate(`!!document.querySelector('.bottom-nav')`), false)
      await screenshot('cart-mobile')
      await click('.mobile-total')
      await ready('.cart-summary.expanded')
      await delay(350)
      assert(
        await evaluate(
          `getComputedStyle(document.querySelector('.summary-content')).display !== 'none'`,
        ),
      )
      await screenshot('cart-mobile-summary')
    }
  }
  scenario = 'loading'
  await pageReload()
  await ready('.cart-skeleton')
  await ready('.cart-product-row')
  await settle()
  scenario = 'normal'
  scenario = 'failure'
  await pageReload()
  await until(
    () => evaluate(`document.querySelector('.cart-state')?.textContent.includes('暂时无法加载')`),
    'cart failure',
  )
  scenario = 'normal'
  await click('.cart-state button')
  await ready('.cart-product-row')
  await settle()
  rows = initialRows().filter((r) => !r.available)
  await pageReload()
  await ready('.invalid-list')
  assert.equal(await evaluate(`document.querySelector('.checkout-button').disabled`), true)
  rows = []
  await pageReload()
  await until(
    () => evaluate(`document.querySelector('.cart-state')?.textContent.includes('还是空的')`),
    'empty state',
  )
  await evaluate(
    `sessionStorage.removeItem('zgnf.access-session'); sessionStorage.setItem('cart-test-logged-out','1')`,
  )
  await pageReload()
  await until(
    () => evaluate(`document.querySelector('.cart-state')?.textContent.includes('登录后查看')`),
    'signed-out state',
  )
  assert.deepEqual(errors, [])
  writeFileSync(
    join(artifacts, 'cart-results.json'),
    JSON.stringify(
      {
        status: 'passed',
        checks: [
          'selection and indeterminate',
          'valid-only amount and select-all',
          'invalid quantity',
          'rapid edits',
          'mutation recovery',
          'server coupons and invalidation',
          'price change guard',
          'order retry and duplicate guard',
          'batch delete and cleanup',
          'loading empty failed signed-out invalid-only',
          '8 responsive widths',
        ],
        requests: requests.length,
      },
      null,
      2,
    ),
  )
  console.log(`Cart browser checks passed; screenshots: ${artifacts}`)
} finally {
  socket?.close()
  browser.kill()
  await delay(300)
  try {
    rmSync(profile, { recursive: true, force: true })
  } catch {
    /* Browser profile can remain locked briefly on Windows. */
  }
}
