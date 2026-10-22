// Exercise the production UI using an installed browser; no browser download is required.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync, mkdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'

const origin = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:4173'
const browserPath = process.env.BROWSER_BIN || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
assert(existsSync(browserPath), 'Set BROWSER_BIN to your Chrome or Edge executable.')
assert((await fetch(origin)).ok, `Start npm run preview on ${origin} first.`)
const profile = mkdtempSync(join(tmpdir(), 'zgnf-admin-smoke-'))
const browser = spawn(browserPath, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank',
], { windowsHide: true, stdio: 'ignore' })
let browserError
browser.on('error', error => { browserError = error })
let socket
let id = 0
const pending = new Map()
const errors = []
async function until(check, description) {
  for (let i = 0; i < 100; i++) {
    if (await check()) return
    await delay(100)
  }
  throw new Error(`Timed out: ${description}`)
}
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const requestId = ++id
    const timeout = setTimeout(() => { pending.delete(requestId); reject(new Error(`CDP timeout: ${method}`)) }, 10000)
    pending.set(requestId, { resolve: value => { clearTimeout(timeout); resolve(value) }, reject: error => { clearTimeout(timeout); reject(error) } })
    socket.send(JSON.stringify({ id: requestId, method, params }))
  })
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
  return result.result.value
}
async function navigate(path, heading) {
  await send('Page.navigate', { url: `${origin}/#${path}` })
  await until(() => evaluate(`document.querySelector('h1')?.textContent === ${JSON.stringify(heading)}`), heading)
}

try {
  const portFile = join(profile, 'DevToolsActivePort')
  await until(() => {
    if (browserError) throw browserError
    return existsSync(portFile)
  }, 'browser startup')
  const port = readFileSync(portFile, 'utf8').split('\n')[0]
  const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json()
  socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
  socket.onmessage = message => {
    const data = JSON.parse(message.data)
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.text)
    if (data.id && pending.has(data.id)) {
      const call = pending.get(data.id)
      pending.delete(data.id)
      if (data.error) call.reject(new Error(data.error.message))
      else call.resolve(data.result)
    }
  }
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await navigate('/admin', '经营总览')
  assert.equal(await evaluate(`document.querySelectorAll('.stat-panel').length`), 4)
  const screenshotDir = resolve('.screenshots')
  mkdirSync(screenshotDir, { recursive: true })
  const screenshot = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(join(screenshotDir, 'admin-desktop.png'), Buffer.from(screenshot.data, 'base64'))
  await navigate('/admin/orders', '订单管理')
  assert.equal(await evaluate(`document.querySelectorAll('tbody tr').length`), 5)
  await evaluate(`document.querySelector('.pagination button:last-child').click()`)
  await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 1`), 'next order page')
  await evaluate(`document.querySelector('.table-search input').value = '陈女士'; document.querySelector('.table-search input').dispatchEvent(new Event('input', { bubbles: true }))`)
  await until(() => evaluate(`document.querySelector('tbody')?.textContent.includes('陈女士')`), 'order search and page reset')
  assert.equal(await evaluate(`document.querySelector('.pagination>div>span').textContent`), '1 / 1')
  await evaluate(`document.querySelector('tbody button').click()`)
  await until(() => evaluate(`document.querySelector('dialog').open`), 'record details')
  assert(await evaluate(`document.querySelector('dialog').textContent.includes('GN202610210084')`))
  await evaluate(`document.querySelector('dialog button').click()`)
  await navigate('/admin/products', '商品管理')
  assert(await evaluate(`document.querySelector('tbody').textContent.includes('智能燃气灶')`))
  await evaluate(`document.querySelector('.filter-control select').value = '安全配件'; document.querySelector('.filter-control select').dispatchEvent(new Event('change', { bubbles: true }))`)
  await until(() => evaluate(`document.querySelectorAll('tbody tr').length === 2`), 'category filter')
  const moduleNames = { inventory: '库存管理', 'work-orders': '服务工单', aftersales: '售后管理', coupons: '优惠券', stores: '门店管理', employees: '员工管理', roles: '角色权限', logs: '操作日志', config: '系统配置' }
  for (const [path, title] of Object.entries(moduleNames)) await navigate(`/admin/${path}`, title)
  await navigate('/admin/login', '登录运营管理平台')
  assert.equal(await evaluate(`document.querySelectorAll('.admin-sidebar').length`), 0)
  await navigate('/admin/orders', '订单管理')
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await delay(150)
  assert(await evaluate(`window.innerWidth <= 390 && document.documentElement.scrollWidth <= 390`), 'Mobile layout must not expand the viewport.')
  assert.equal(await evaluate(`getComputedStyle(document.querySelector('.admin-nav-link'), '::after').content`), 'none', 'Icon navigation must not render overflowing labels.')
  const mobileScreenshot = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(join(screenshotDir, 'admin-mobile.png'), Buffer.from(mobileScreenshot.data, 'base64'))
  assert.deepEqual(errors, [], 'No uncaught browser exceptions')
  console.log('Browser checks passed: overview, all 11 modules, search, filters, pagination, details, login and mobile layout.')
} finally {
  if (socket?.readyState === WebSocket.OPEN) {
    try { await send('Browser.close') } catch { /* Browser may close before acknowledging. */ }
    socket.close()
  }
  browser.kill()
  await delay(500)
  // Delete only the unique profile created above, after verifying its location.
  if (dirname(resolve(profile)) === resolve(tmpdir()) && basename(profile).startsWith('zgnf-admin-smoke-')) {
    try { rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 }) } catch { /* A browser crash handler may retain a profile file briefly. */ }
  }
}
