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
  await send('Page.addScriptToEvaluateOnNewDocument', {source: `(() => {
    // Only a navigation fixture: employee API responses are mocked below.
    const claims=btoa(JSON.stringify({type:'access',role:'admin',exp:Math.floor(Date.now()/1000)+3600}));
    sessionStorage.setItem('zgnf.access-session',JSON.stringify({token:'mock.'+claims+'.mock',expiresAt:Date.now()+3600000}));
    window.__calls=[];
    window.__staff=[{id:'9007199254740993',username:'technician',name:'服务员工',phone:'13800138000',departmentId:null,positionId:null,status:'active',roleIds:['1'],createTime:'2026-10-28 09:00:00',updateTime:'2026-10-28 09:00:00'}];
    window.__readonly=sessionStorage.getItem('smoke.readonly')==='true';
    const original=window.fetch.bind(window);
    window.fetch=async (url,init={})=>{
      const u=new URL(url,location.origin),path=u.pathname;
      if(path==='/api/v1/cart') return new Response(JSON.stringify({code:200,data:{items:[]}}),{headers:{'Content-Type':'application/json'}});
      if(!path.startsWith('/api/v1/admin/employees')) return original(url,init);
      const body=init.body?JSON.parse(init.body):null;
      window.__calls.push({path,method:init.method||'GET',body});
      let data;
      if(path.endsWith('/options')) data={departments:[],positions:[],roles:[{id:'1',name:'服务人员',status:'active'}],canWrite:!window.__readonly,canManageRoles:!window.__readonly,currentEmployeeId:'7'};
      else if(init.method==='POST') { data={...body.employee,id:'9007199254740994',roleIds:[],createTime:'2026-10-28 10:00:00',updateTime:'2026-10-28 10:00:00'};window.__staff.push(data); }
      else if(path.endsWith('/status')) { data=window.__staff.find(r=>path.includes('/'+r.id+'/'));data.status=body.status; }
      else if(path.endsWith('/roles')) { data=window.__staff.find(r=>path.includes('/'+r.id+'/'));data.roleIds=body.ids; }
      else if(path.endsWith('/password')) data=null;
      else if(init.method==='DELETE') {window.__staff=window.__staff.filter(r=>!path.endsWith('/'+r.id));data=null;}
      else if(init.method==='PUT') {data=window.__staff.find(r=>path.endsWith('/'+r.id));Object.assign(data,body);}
      else if(path==='/api/v1/admin/employees') {const list=window.__staff.filter(r=>!u.searchParams.get('keyword')||r.name.includes(u.searchParams.get('keyword'))||r.username.includes(u.searchParams.get('keyword')));data={list,total:list.length,page:1,size:10,pages:1};}
      else data=window.__staff.find(r=>path.endsWith('/'+r.id));
      return new Response(JSON.stringify({code:200,data}),{headers:{'Content-Type':'application/json'}});
    };
    window.confirm=()=>true;
  })()` });
  await navigate('/admin/employees','员工管理');
  assert(await evaluate("![...document.querySelectorAll('.admin-nav a')].some(a=>a.getAttribute('href')?.includes('/admin/stores'))"),'Removed module must not appear in navigation');
  await until(()=>evaluate("document.querySelector('tbody')?.textContent.includes('technician')"),'employee list');
  async function click(text,scope='document') { await evaluate(`[...${scope}.querySelectorAll('button')].find(b=>b.textContent.trim()===${JSON.stringify(text)}).click()`); }
  await click('详情');
  await until(()=>evaluate("document.querySelector('.detail-grid')?.textContent.includes('9007199254740993')"),'employee detail and Long ID');
  await click('编辑');
  await until(()=>evaluate("document.querySelector('.employee-editor h2')?.textContent.includes('编辑员工')"),'edit employee');
  await evaluate("const name=document.querySelectorAll('.employee-fields input')[1];name.value='修改后的员工';name.dispatchEvent(new Event('input',{bubbles:true}))");
  await click('保存');
  await until(()=>evaluate("document.querySelector('tbody')?.textContent.includes('修改后的员工')"),'saved edit');
  await click('停用');
  await until(()=>evaluate("document.querySelector('tbody .status-tag')?.textContent==='停用'"),'disable employee');
  await click('启用');
  await until(()=>evaluate("document.querySelector('tbody .status-tag')?.textContent==='启用'"),'enable employee');
  await click('角色');
  await until(()=>evaluate("!!document.querySelector('.role-picker')"),'role editor');
  await evaluate("document.querySelector('.role-picker input').click()");
  await click('保存');
  await until(()=>evaluate("window.__calls.some(c=>c.path.endsWith('/roles')&&c.body.ids.length===0)"),'clear employee roles');
  await click('重置密码');
  await until(()=>evaluate("!!document.querySelector('input[type=password]')"),'password form');
  await evaluate("const p=document.querySelector('input[type=password]');p.value='new-strong-password';p.dispatchEvent(new Event('input',{bubbles:true}))");
  await click('保存');
  await until(()=>evaluate("window.__calls.some(c=>c.path.endsWith('/password')&&c.body.password==='new-strong-password')"),'reset password');
  await click('新增员工');
  await until(()=>evaluate("document.querySelector('.employee-editor h2')?.textContent==='新增员工'"),'create form');
  assert(await evaluate("!document.querySelector('.employee-fields')?.textContent.includes('门店')"),'Employee creation must not require a location');
  const createShots=resolve('.screenshots');mkdirSync(createShots,{recursive:true});
  await delay(500);
  const desktopCreate=await send('Page.captureScreenshot',{format:'png'});writeFileSync(join(createShots,'employees-create-desktop.png'),Buffer.from(desktopCreate.data,'base64'));
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await evaluate("document.querySelector('.employee-editor').scrollIntoView({block:'start',behavior:'instant'})");
  assert(await evaluate('document.documentElement.scrollWidth<=390'),'mobile create form overflow');
  const mobileCreate=await send('Page.captureScreenshot',{format:'png'});writeFileSync(join(createShots,'employees-create-mobile.png'),Buffer.from(mobileCreate.data,'base64'));
  await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
  await evaluate("const inputs=document.querySelectorAll('.employee-fields input');['new_staff','新员工','13900139000','initial-password'].forEach((v,i)=>{inputs[i].value=v;inputs[i].dispatchEvent(new Event('input',{bubbles:true}))})");
  await click('创建员工');
  await until(()=>evaluate("document.querySelector('tbody')?.textContent.includes('new_staff')"),'create employee');
  await evaluate("const row=[...document.querySelectorAll('tbody tr')].find(r=>r.textContent.includes('new_staff'));[...row.querySelectorAll('button')].find(b=>b.textContent==='删除').click()");
  await until(()=>evaluate("!document.querySelector('tbody')?.textContent.includes('new_staff')"),'delete employee');
  await evaluate("var input=document.querySelector('.employee-tools input');input.value='不存在';input.dispatchEvent(new Event('input',{bubbles:true}))");
  await click('查询');
  await until(()=>evaluate("document.querySelector('.empty-state')?.textContent.includes('暂无')"),'search empty state');
  await evaluate("var input=document.querySelector('.employee-tools input');input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));sessionStorage.setItem('smoke.readonly','true')");
  await send('Page.reload');
  await until(()=>evaluate("document.querySelector('tbody')?.textContent.includes('technician')"),'readonly employee list');
  await until(()=>evaluate("document.querySelector('.page-heading button')?.disabled"),'read-only permissions loaded');
  assert.equal(await evaluate("[...document.querySelectorAll('.row-actions button')].filter(b=>!b.disabled).map(b=>b.textContent).join(',')"),'详情');
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await delay(200);
  assert(await evaluate('document.documentElement.scrollWidth<=390'),'mobile viewport overflow');
  const dir=resolve('.screenshots');mkdirSync(dir,{recursive:true});
  const shot=await send('Page.captureScreenshot',{format:'png'});writeFileSync(join(dir,'employees-mobile.png'),Buffer.from(shot.data,'base64'));
  assert.deepEqual(errors,[],'No uncaught browser errors');
  console.log('Employee browser checks passed: list, Long ID detail, edit, create, delete, status, roles, password, empty search, read-only access and mobile layout (mock API).');
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

