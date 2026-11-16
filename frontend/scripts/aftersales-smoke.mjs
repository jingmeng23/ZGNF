// Browser checks use intercepted API fixtures and never submit real refunds.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync, mkdirSync } from 'node:fs'
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
const profile = mkdtempSync(join(tmpdir(), 'zgnf-aftersales-smoke-'))
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
let browserError,
  socket,
  id = 0,
  failList = false,
  wechatProcessing = true
const pending = new Map(),
  errors = [],
  requests = []
const records = Array.from({ length: 15 }, (_, index) => {
  const status = ['pending', 'approved', 'completed', 'rejected', 'pending'][index % 5]
  return {
    id: String(9007199254740993n + BigInt(index)),
    aftersaleNo: `AS20261112${String(index + 1).padStart(4, '0')}`,
    orderId: String(9007199254750993n + BigInt(index)),
    orderNo: `GN20261110${String(index + 1).padStart(4, '0')}`,
    amount: 299 + index * 100.5,
    status,
    reason:
      index === 0
        ? '收到商品后发现点火异常，已联系客服核实，希望申请整单退款。'
        : ['商品质量问题，申请退款', '订单重复支付', '商品与预期不符'][index % 3],
    createTime: `2026-11-${String(12 - Math.floor(index / 3)).padStart(2, '0')}T09:30:00`,
    updateTime: '2026-11-12T10:00:00',
    reviewRemark:
      status === 'pending' ? null : status === 'rejected' ? '用户撤销申请' : '已核实，同意退款',
    reviewedBy: ['pending', 'rejected'].includes(status) ? null : '7',
    refundReference: status === 'completed' ? `BANK-${index}` : null,
    completionRemark: status === 'completed' ? '已核实退款到账' : null,
    completedTime: status === 'completed' ? '2026-11-12T10:00:00' : null,
  }
})
const histories = new Map(
  records.map((row) => [
    row.id,
    [
      {
        id: `apply-${row.id}`,
        action: 'apply',
        fromStatus: null,
        toStatus: 'pending',
        operatorId: '1001',
        operatorType: 'user',
        remark: row.reason,
        occurredAt: row.createTime,
      },
    ],
  ]),
)
browser.on('error', (error) => {
  browserError = error
})
async function until(check, description) {
  for (let i = 0; i < 120; i++) {
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
    }, 15000)
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
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails))
  return result.result.value
}
async function click(selector) {
  assert(
    await evaluate(`!!document.querySelector(${JSON.stringify(selector)})`),
    `Missing ${selector}`,
  )
  await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`)
}
async function input(selector, value) {
  await evaluate(
    `(() => { const el = document.querySelector(${JSON.stringify(selector)}); el.value = ${JSON.stringify(value)}; el.dispatchEvent(new Event('input', { bubbles: true })); })()`,
  )
}
async function select(selector, value) {
  await evaluate(
    `(() => { const el = document.querySelector(${JSON.stringify(selector)}); el.value = ${JSON.stringify(value)}; el.dispatchEvent(new Event('change', { bubbles: true })); })()`,
  )
}
async function screenshot(name) {
  const shot = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(join(resolve('.screenshots'), name), Buffer.from(shot.data, 'base64'))
}
async function listReady() {
  await until(
    () => evaluate(`!!document.querySelector('.admin-table-wrap[aria-busy="false"]')`),
    'list settled',
  )
}
async function drawerReady() {
  await until(
    () =>
      evaluate(
        `document.querySelector('dialog').open && !!document.querySelector('.processing-panel') && document.querySelector('.drawer-body').getAttribute('aria-busy') === 'false'`,
      ),
    'drawer settled',
  )
}
async function intercept({ requestId, request }) {
  const url = new URL(request.url),
    path = url.pathname
  requests.push({
    path,
    query: Object.fromEntries(url.searchParams),
    method: request.method,
    body: request.postData,
  })
  let data = {},
    responseCode = 200,
    message = 'success'
  if (path.endsWith('/admin/auth/me'))
    data = {
      employeeId: '7',
      username: 'browser-check',
      roles: ['admin'],
      permissions: ['aftersale:read', 'aftersale:write'],
    }
  else if (path.endsWith('/cart')) data = []
  else if (path.endsWith('/admin/aftersales')) {
    if (failList && url.searchParams.get('size') !== '1') {
      failList = false
      responseCode = 503
      message = '服务暂时不可用，请重试'
    } else {
      const query = url.searchParams
      const filtered = records.filter(
        (row) =>
          (!query.get('status') || row.status === query.get('status')) &&
          (!query.get('aftersaleNo') || row.aftersaleNo === query.get('aftersaleNo')) &&
          (!query.get('orderNo') || row.orderNo === query.get('orderNo')),
      )
      const key = query.get('sort') || 'createTime',
        direction = query.get('order') === 'asc' ? 1 : -1
      filtered.sort((a, b) => (a[key] < b[key] ? -1 : a[key] > b[key] ? 1 : 0) * direction)
      const page = Number(query.get('page') || 1),
        size = Number(query.get('size') || 10)
      data = {
        list: filtered.slice((page - 1) * size, page * size),
        total: filtered.length,
        pages: Math.ceil(filtered.length / size),
        page,
        size,
      }
    }
  } else if (/\/admin\/aftersales\//.test(path)) {
    const [recordId, action] = path.split('/aftersales/')[1].split('/')
    const row = records.find((item) => item.id === recordId)
    assert(row, `Unknown record ${recordId}`)
    if (action === 'operations') data = histories.get(recordId)
    else if (!action) data = row
    else {
      const body = JSON.parse(request.postData || '{}')
      if (action === 'wechat-refund' && wechatProcessing) {
        wechatProcessing = false
        responseCode = 409
        message = '微信退款处理中，请稍后点击查询退款结果'
      } else {
        const fromStatus = row.status
        if (action === 'review')
          Object.assign(row, { status: body.status, reviewRemark: body.remark, reviewedBy: '7' })
        else
          Object.assign(row, {
            status: 'completed',
            refundReference: action === 'wechat-refund' ? `WXAS${recordId}` : body.refundReference,
            completionRemark: body.remark || '微信原路退款已确认',
            completedTime: '2026-11-12T10:00:00',
          })
        histories.get(recordId).push({
          id: `${action}-${Date.now()}`,
          action:
            action === 'review' ? (body.status === 'approved' ? 'approve' : 'reject') : 'complete',
          fromStatus,
          toStatus: row.status,
          operatorId: '7',
          operatorType: 'admin',
          remark: body.remark || '退款已确认',
          occurredAt: '2026-11-12T10:00:00',
        })
        data = row
      }
    }
  }
  await send('Fetch.fulfillRequest', {
    requestId,
    responseCode,
    responseHeaders: [{ name: 'Content-Type', value: 'application/json; charset=utf-8' }],
    body: Buffer.from(JSON.stringify({ code: responseCode, message, data })).toString('base64'),
  })
}

try {
  const portFile = join(profile, 'DevToolsActivePort')
  await until(() => {
    if (browserError) throw browserError
    return existsSync(portFile)
  }, 'browser startup')
  const port = readFileSync(portFile, 'utf8').split('\n')[0]
  const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json()
  socket = new WebSocket(targets.find((target) => target.type === 'page').webSocketDebuggerUrl)
  await new Promise((done, reject) => {
    socket.onopen = done
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
  const token = `test.${Buffer.from(JSON.stringify({ type: 'access', role: 'admin', exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')}.test`
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `sessionStorage.setItem('zgnf.access-session', ${JSON.stringify(JSON.stringify({ token, expiresAt: Date.now() + 3600000 }))}); window.__confirmAccepted = true; window.__confirmMessages = []; window.confirm = message => { window.__confirmMessages.push(message); return window.__confirmAccepted; };`,
  })
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1050,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await send('Page.navigate', { url: `${origin}/#/admin/aftersales` })
  await until(
    () =>
      evaluate(
        `document.querySelector('h1')?.textContent === '售后管理' && document.querySelectorAll('.record-link').length === 10`,
      ),
    'aftersales list',
  )
  await until(
    () =>
      evaluate(
        `document.querySelector('.summary-card .summary-text strong')?.textContent === '15单'`,
      ),
    'global overview counts',
  )
  mkdirSync(resolve('.screenshots'), { recursive: true })
  await screenshot('aftersales-desktop.png')
  await click('[aria-label="下一页"]')
  await until(
    () => evaluate(`document.querySelectorAll('.record-link').length === 5`),
    'pagination',
  )
  await input('.aftersale-filters label:nth-child(2) input', ' GN202611100001 ')
  await click('.aftersale-filters .primary-button')
  await until(
    () => evaluate(`document.querySelectorAll('.record-link').length === 1`),
    'exact order filter',
  )
  assert.equal(
    await evaluate(`document.querySelector('.summary-card .summary-text strong').textContent`),
    '15单',
  )
  await click('.filter-actions .secondary-button')
  await listReady()
  await select('.list-caption select', 'amount-desc')
  await listReady()
  assert(
    requests.some((request) => request.query.sort === 'amount' && request.query.order === 'desc'),
  )
  await input('.aftersale-filters label:first-child input', 'NO-SUCH-AFTERSALE')
  await click('.aftersale-filters .primary-button')
  await until(
    () => evaluate(`document.querySelector('.list-empty')?.textContent.includes('没有找到')`),
    'empty filtered state',
  )
  await click('.list-empty button')
  await listReady()
  failList = true
  await click('.heading-actions button:last-child')
  await until(() => evaluate(`!!document.querySelector('.list-failure')`), 'failed list state')
  await click('.list-failure button')
  await listReady()
  await click('.summary-card.pending')
  await listReady()
  await click('.row-action')
  await drawerReady()
  await screenshot('aftersales-review-desktop.png')
  assert.equal(await evaluate(`document.querySelector('.submit-action').disabled`), true)
  await input('.processing-form textarea', '核实质量问题，同意退款')
  await evaluate(`window.__confirmAccepted = false`)
  await click('[aria-label="关闭售后详情"]')
  assert.equal(await evaluate(`document.querySelector('dialog').open`), true)
  await evaluate(`window.__confirmAccepted = true`)
  await click('.submit-action')
  await until(
    () =>
      evaluate(
        `document.querySelector('.processing-heading')?.textContent.includes('处理退款') && document.querySelector('.drawer-body').getAttribute('aria-busy') === 'false'`,
      ),
    'review completion',
  )
  await click('.submit-action')
  await until(
    () => evaluate(`document.querySelector('.action-error')?.textContent.includes('处理中')`),
    'WeChat processing result',
  )
  assert(
    await evaluate(`document.querySelector('.detail-summary').textContent.includes('待退款登记')`),
  )
  await click('.submit-action')
  await until(
    () =>
      evaluate(
        `document.querySelector('.processing-result')?.textContent.includes('售后已完成') && document.querySelector('.drawer-body').getAttribute('aria-busy') === 'false'`,
      ),
    'WeChat confirmed refund',
  )
  await screenshot('aftersales-completed-desktop.png')
  await send('Input.dispatchKeyEvent', {
    type: 'keyDown',
    key: 'Escape',
    code: 'Escape',
    windowsVirtualKeyCode: 27,
  })
  await until(() => evaluate(`!document.querySelector('dialog').open`), 'Escape closes drawer')
  assert.equal(await evaluate(`document.body.style.overflow`), '')
  await click('.summary-card.approved')
  await listReady()
  await click('.row-action')
  await drawerReady()
  await click('input[value="manual"]')
  assert.equal(await evaluate(`document.querySelector('.submit-action').disabled`), true)
  await input('.processing-form input[maxlength="100"]', 'BANK-REAL-123')
  await input('.processing-form textarea', '已在原支付渠道退款')
  await click('.refund-confirmation input')
  await click('.submit-action')
  await until(
    () =>
      evaluate(
        `document.querySelector('.processing-result')?.textContent.includes('售后已完成') && document.querySelector('.drawer-body').getAttribute('aria-busy') === 'false'`,
      ),
    'manual refund registration',
  )
  assert(
    requests.some(
      (request) =>
        request.path.endsWith('/complete') &&
        JSON.parse(request.body).refundReference === 'BANK-REAL-123',
    ),
  )
  await click('[aria-label="关闭售后详情"]')
  await click('.feedback button')
  await click('.summary-card.all')
  await listReady()
  await send('Emulation.setDeviceMetricsOverride', {
    width: 390,
    height: 844,
    deviceScaleFactor: 1,
    mobile: true,
  })
  await delay(200)
  assert(
    await evaluate(`document.documentElement.scrollWidth <= 390`),
    'Mobile list must fit the viewport',
  )
  await screenshot('aftersales-mobile.png')
  assert(
    await evaluate(
      `document.querySelector('.admin-table-wrap').scrollWidth <= document.querySelector('.admin-table-wrap').clientWidth`,
    ),
    'Mobile records must not need horizontal scrolling',
  )
  await click('.summary-card.pending')
  await listReady()
  await click('.row-action')
  await drawerReady()
  assert(
    await evaluate(
      `document.querySelector('dialog').scrollWidth <= 390 && document.querySelector('.drawer-body').scrollWidth <= 390`,
    ),
    'Mobile drawer must fit the viewport',
  )
  await screenshot('aftersales-detail-mobile.png')
  await send('Emulation.setDeviceMetricsOverride', {
    width: 320,
    height: 740,
    deviceScaleFactor: 1,
    mobile: true,
  })
  await delay(100)
  assert(
    await evaluate(
      `document.querySelector('dialog').scrollWidth <= 320 && document.querySelector('.drawer-body').scrollWidth <= 320`,
    ),
    'Narrow mobile drawer must fit the viewport',
  )
  assert.deepEqual(errors, [], 'No browser exceptions')
  console.log(
    'Aftersales browser checks passed: overview, exact filters, reset, sorting, pagination, empty/error recovery, review, draft protection, WeChat processing/retry, manual refund, Escape and 390/320px layouts. API calls were intercepted; no real refunds were sent.',
  )
} catch (error) {
  if (socket?.readyState === WebSocket.OPEN) {
    console.error(
      await evaluate(
        `JSON.stringify({ url: location.href, body: document.body.innerText.slice(0, 2500) })`,
      ),
    )
    console.error(JSON.stringify({ errors, requests }))
    mkdirSync(resolve('.screenshots'), { recursive: true })
    await screenshot('aftersales-failure.png')
  }
  throw error
} finally {
  if (socket?.readyState === WebSocket.OPEN) {
    try {
      await send('Browser.close')
    } catch {
      /* Browser may close before acknowledging. */
    }
    socket.close()
  }
  browser.kill()
  await delay(500)
  if (
    dirname(resolve(profile)) === resolve(tmpdir()) &&
    basename(profile).startsWith('zgnf-aftersales-smoke-')
  ) {
    try {
      rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 })
    } catch {
      /* A crash handler may retain a temporary file. */
    }
  }
}
