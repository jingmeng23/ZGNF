// All API calls use fixtures so browser checks do not alter business records.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'

const origin = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5173'
assert(
  ['127.0.0.1', 'localhost'].includes(new URL(origin).hostname),
  'Use a local frontend server.',
)
const browserPath =
  process.env.BROWSER_BIN || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
assert(existsSync(browserPath), 'Set BROWSER_BIN to your Chrome or Edge executable.')
assert((await fetch(origin)).ok, `Start the frontend on ${origin} first.`)

const profile = mkdtempSync(join(tmpdir(), 'zgnf-overview-check-'))
const artifacts = resolve('../target/overview-visual-check')
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

function reportFor(url) {
  const { startDate, endDate } = Object.fromEntries(url.searchParams)
  const first = Date.parse(`${startDate}T00:00:00Z`)
  const last = Date.parse(`${endDate}T00:00:00Z`)
  const days = []
  for (let timestamp = first; timestamp <= last; timestamp += 86400000) {
    const index = Math.round((timestamp - first) / 86400000)
    const amount = scenario === 'zero' ? 0 : index * 90
    const refunded = scenario !== 'zero' && index % 3 === 0
    days.push({
      date: new Date(timestamp).toISOString().slice(0, 10),
      metrics: {
        createdOrders: scenario === 'zero' ? 0 : index + 1,
        paidOrders: scenario === 'zero' ? 0 : Math.max(0, index - 1),
        receivedAmount: amount,
        refundedOrders: refunded ? 1 : 0,
        refundAmount: refunded ? 30 : 0,
        netReceivedAmount: amount - (refunded ? 30 : 0),
        newCustomers: scenario === 'zero' ? 0 : index % 2,
      },
    })
  }
  return days
}

async function intercept({ requestId, request }) {
  const url = new URL(request.url)
  const path = url.pathname
  requests.push({ path, query: Object.fromEntries(url.searchParams) })
  if (scenario === 'failure' && path.includes('/statistics/')) {
    await send('Fetch.fulfillRequest', {
      requestId,
      responseCode: 503,
      responseHeaders: [{ name: 'Content-Type', value: 'application/json; charset=utf-8' }],
      body: Buffer.from(JSON.stringify({ code: 503, message: '统计服务暂时不可用' })).toString(
        'base64',
      ),
    })
    return
  }
  let data = {}
  if (path.endsWith('/admin/auth/me')) {
    data = {
      employeeId: '7',
      username: 'overview-check',
      roles: ['admin'],
      permissions: [
        'statistics:read',
        'statistics:export',
        'order:read',
        'aftersale:read',
        'workorder:read',
      ],
    }
  } else if (path.endsWith('/admin/statistics/overview')) {
    const current = reportFor(url).reduce((total, day) => {
      for (const key of Object.keys(day.metrics)) total[key] = (total[key] || 0) + day.metrics[key]
      return total
    }, {})
    const total = current.createdOrders
    const pending_payment = Math.floor(total * 0.12)
    const paid = Math.floor(total * 0.22)
    const processing = Math.floor(total * 0.1)
    const cancelled = Math.floor(total * 0.08)
    const refunded = Math.floor(total * 0.06)
    const end = Date.parse(`${url.searchParams.get('startDate')}T00:00:00Z`) - 86400000
    const span = reportFor(url).length
    data = {
      startDate: url.searchParams.get('startDate'),
      endDate: url.searchParams.get('endDate'),
      previousStartDate: new Date(end - (span - 1) * 86400000).toISOString().slice(0, 10),
      previousEndDate: new Date(end).toISOString().slice(0, 10),
      current,
      previous: {
        createdOrders: 25,
        paidOrders: 20,
        receivedAmount: 2400,
        refundedOrders: 1,
        refundAmount: 120,
        netReceivedAmount: 2280,
        newCustomers: 9,
      },
      receivedGrowthPercent: ((current.receivedAmount - 2400) / 2400) * 100,
      orderGrowthPercent: ((current.createdOrders - 25) / 25) * 100,
      pendingWorkOrders: 7,
      overdueWorkOrders: 2,
      orderStatuses: {
        pending_payment,
        paid,
        processing,
        completed: total - pending_payment - paid - processing - cancelled - refunded,
        cancelled,
        refunded,
      },
      recentOrders: [
        {
          id: '101',
          orderNo: 'GN202611120001',
          status: 'paid',
          payAmount: 129.5,
          createTime: '2026-11-12T12:30:00',
        },
        {
          id: '102',
          orderNo: 'GN202611120002',
          status: 'completed',
          payAmount: 259.9,
          createTime: '2026-11-12T11:30:00',
        },
      ],
      todos: { paidOrders: 8, pendingAftersales: 3, pendingRefunds: 2 },
    }
    if (scenario === 'zero') data.recentOrders = []
  } else if (path.endsWith('/admin/statistics/trend')) {
    data = reportFor(url)
  } else if (path.endsWith('/admin/statistics/products')) {
    data = [
      {
        productId: '20',
        productName: '智能恒温燃气热水器',
        paidOrders: 13,
        quantity: 18,
        merchandiseAmount: 11980,
      },
      {
        productId: '21',
        productName: '厨房安全报警器',
        paidOrders: 10,
        quantity: 12,
        merchandiseAmount: 2560,
      },
    ]
    if (scenario === 'zero') data = []
  } else if (path.endsWith('/admin/statistics/reports/daily')) {
    const all = reportFor(url).reverse()
    const page = Number(url.searchParams.get('page') || 1)
    const size = Number(url.searchParams.get('size') || 7)
    data = {
      list: all.slice((page - 1) * size, page * size),
      total: all.length,
      page,
      size,
      pages: Math.ceil(all.length / size),
    }
  }
  await send('Fetch.fulfillRequest', {
    requestId,
    responseCode: 200,
    responseHeaders: [{ name: 'Content-Type', value: 'application/json; charset=utf-8' }],
    body: Buffer.from(JSON.stringify({ code: 200, message: 'success', data })).toString('base64'),
  })
}

try {
  const portFile = join(profile, 'DevToolsActivePort')
  await until(() => existsSync(portFile), 'Edge startup')
  const port = readFileSync(portFile, 'utf8').split('\n')[0]
  const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json()
  socket = new WebSocket(targets.find((target) => target.type === 'page').webSocketDebuggerUrl)
  await new Promise((resolveCall, reject) => {
    socket.onopen = resolveCall
    socket.onerror = reject
  })
  socket.onmessage = (event) => {
    const result = JSON.parse(event.data)
    if (result.method === 'Runtime.exceptionThrown')
      errors.push(result.params.exceptionDetails.text)
    if (result.method === 'Fetch.requestPaused')
      intercept(result.params).catch((error) => errors.push(error.message))
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
  const token = `test.${Buffer.from(
    JSON.stringify({ type: 'access', role: 'admin', exp: Math.floor(Date.now() / 1000) + 3600 }),
  ).toString('base64url')}.test`
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `sessionStorage.setItem('zgnf.access-session', ${JSON.stringify(
      JSON.stringify({ token, expiresAt: Date.now() + 3600000 }),
    )})`,
  })
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1100,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await send('Page.navigate', { url: `${origin}/#/admin` })
  await until(
    () => evaluate(`document.querySelectorAll('.business-overview .stat-panel').length === 4`),
    'overview metrics',
  )
  await until(() => evaluate(`!!document.querySelector('.business-chart svg')`), 'trend chart')
  const desktop = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: true,
  })
  writeFileSync(join(artifacts, 'overview-desktop.png'), Buffer.from(desktop.data, 'base64'))
  const desktopState = await evaluate(`({
    width: window.innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    height: document.documentElement.scrollHeight,
    dailyRows: document.querySelectorAll('.daily-table tbody tr').length,
    trendPoints: document.querySelectorAll('.business-chart circle').length
  })`)
  assert.equal(desktopState.scrollWidth, desktopState.width)
  assert.equal(desktopState.dailyRows, 7)
  assert(desktopState.trendPoints > 0)
  const layouts = []
  for (const width of [1440, 1280, 1024, 768, 390, 320]) {
    await send('Emulation.setDeviceMetricsOverride', {
      width,
      height: width < 700 ? 844 : 1100,
      deviceScaleFactor: 1,
      mobile: width < 700,
    })
    await delay(160)
    const layout = await evaluate(`({
      width: window.innerWidth,
      scrollWidth: document.documentElement.scrollWidth,
      metricColumns: getComputedStyle(document.querySelector('.stats-grid')).gridTemplateColumns,
      todoOverlaps: [...document.querySelectorAll('.todo-item')].filter(item => {
        const a = item.querySelector('div > span').getBoundingClientRect();
        const b = item.querySelector('.todo-action').getBoundingClientRect();
        return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
      }).length
    })`)
    assert(layout.scrollWidth <= layout.width, `Page overflow at ${width}px`)
    assert.equal(layout.todoOverlaps, 0, `Task label overlaps action at ${width}px`)
    layouts.push(layout)
    if (width === 390) {
      const mobile = await send('Page.captureScreenshot', {
        format: 'png',
        captureBeyondViewport: true,
      })
      writeFileSync(join(artifacts, 'overview-mobile.png'), Buffer.from(mobile.data, 'base64'))
      const viewport = await send('Page.captureScreenshot', { format: 'png' })
      writeFileSync(
        join(artifacts, 'overview-mobile-top.png'),
        Buffer.from(viewport.data, 'base64'),
      )
    }
  }
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1100,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await evaluate(`document.querySelector('.trend-metrics button:nth-child(4)').click()`)
  assert.equal(
    await evaluate(`document.querySelector('.trend-total strong').textContent.trim()`),
    '28 单',
  )
  await evaluate(
    `document.querySelector('.business-chart svg').dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }))`,
  )
  assert.equal(
    await evaluate(`document.querySelector('.chart-reading strong').textContent.trim()`),
    '1 单',
  )
  const beforeEdit = requests.length
  const applied = await evaluate(`document.querySelector('.filter-context strong').textContent`)
  await evaluate(`(() => {
    const input = document.querySelector('.custom-range input');
    input.value = '2026-10-01';
    input.dispatchEvent(new Event('input', { bubbles: true }));
  })()`)
  assert.match(
    await evaluate(`document.querySelector('#range-feedback').textContent`),
    /日期已修改/,
  )
  assert.equal(
    await evaluate(`document.querySelector('.filter-context strong').textContent`),
    applied,
  )
  assert.equal(requests.length, beforeEdit)
  await evaluate(`document.querySelector('#range-feedback button').click()`)
  assert.equal(await evaluate(`!!document.querySelector('#range-feedback')`), false)
  assert.equal(requests.length, beforeEdit)
  await evaluate(`(() => {
    const inputs = document.querySelectorAll('.custom-range input');
    inputs[0].value = '2026-11-12'; inputs[1].value = '2026-11-10';
    inputs.forEach(input => input.dispatchEvent(new Event('input', { bubbles: true })));
    document.querySelector('.custom-range').requestSubmit();
  })()`)
  assert.match(
    await evaluate(`document.querySelector('#range-feedback').textContent`),
    /开始日期不能晚于/,
  )
  assert.equal(requests.length, beforeEdit)
  await evaluate(`document.querySelector('.preset-buttons button:nth-child(4)').click()`)
  await until(
    () =>
      evaluate(
        `document.querySelector('.daily-pagination > span')?.textContent.includes('30') && !document.querySelector('.heading-actions button:last-child').disabled`,
      ),
    '30 day report',
  )
  await evaluate(`document.querySelector('[aria-label="日报下一页"]').click()`)
  await until(
    () =>
      evaluate(
        `document.querySelector('.daily-pagination > div > span')?.textContent.includes('2 / 5') && document.querySelectorAll('.daily-table tbody tr').length === 7`,
      ),
    'daily page 2',
  )
  await evaluate(`document.querySelector('.preset-buttons button').click()`)
  await until(
    () =>
      evaluate(
        `document.querySelector('.daily-pagination > span')?.textContent.includes('共 1 天') && document.querySelectorAll('.daily-table tbody tr').length === 1`,
      ),
    'today range and pagination reset',
  )
  const reload = async (mode) => {
    scenario = mode
    const before = requests.filter((request) => request.path.endsWith('/overview')).length
    await evaluate(`document.querySelector('.heading-actions button:last-child').click()`)
    await until(
      () => requests.filter((request) => request.path.endsWith('/overview')).length > before,
      `${mode} request`,
    )
    await until(
      () => evaluate(`!document.querySelector('.heading-actions button:last-child').disabled`),
      `${mode} complete`,
    )
  }
  await reload('zero')
  assert.equal(
    await evaluate(`document.querySelector('.metric-value').textContent.trim()`),
    '¥0.00',
  )
  assert.match(await evaluate(`document.querySelector('.chart-help').textContent`), /暂无此项数据/)
  assert.match(
    await evaluate(`document.querySelector('.ranking-panel').textContent`),
    /暂无已支付商品/,
  )
  const zero = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync(join(artifacts, 'overview-zero.png'), Buffer.from(zero.data, 'base64'))
  await reload('failure')
  assert.equal(
    await evaluate(`document.querySelectorAll('.business-overview .section-error').length`),
    4,
  )
  assert.equal(await evaluate(`document.querySelectorAll('.metric-value').length`), 0)
  const failure = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: true,
  })
  writeFileSync(join(artifacts, 'overview-failure.png'), Buffer.from(failure.data, 'base64'))
  await reload('normal')
  assert.equal(
    await evaluate(`document.querySelectorAll('.business-overview .section-error').length`),
    0,
  )
  assert.deepEqual(errors, [])
  console.log(
    JSON.stringify({
      desktopState,
      layouts,
      interactions:
        'date edits, reset, validation, presets, pagination, trend, keyboard, empty and failure recovery passed',
      screenshots: artifacts,
    }),
  )
} finally {
  if (socket?.readyState === WebSocket.OPEN) {
    try {
      await send('Browser.close')
    } catch {}
    socket.close()
  }
  browser.kill()
  await delay(300)
  if (
    dirname(resolve(profile)) === resolve(tmpdir()) &&
    basename(profile).startsWith('zgnf-overview-check-')
  ) {
    try {
      rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 })
    } catch {}
  }
}
