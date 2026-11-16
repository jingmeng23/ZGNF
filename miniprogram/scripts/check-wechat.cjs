const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const { execFileSync } = require('node:child_process')
const root = path.resolve(__dirname, '..')
const directory = process.env.WECHAT_COMPILER_DIR || 'C:/Program Files (x86)/Tencent/微信web开发者工具/resources/app.asar.unpacked/node_modules/wcc-exec'
const wcc = path.join(directory, process.platform === 'win32' ? 'wcc.exe' : 'wcc')
const wcsc = path.join(directory, process.platform === 'win32' ? 'wcsc.exe' : 'wcsc')
for (const compiler of [wcc, wcsc]) if (!fs.existsSync(compiler)) throw new Error('WeChat compiler not found. Set WECHAT_COMPILER_DIR to the wcc-exec directory.')
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
const templates = app.pages.map(page => page + '.wxml')
const styles = ['app.wxss'].concat(app.pages.map(page => page + '.wxss')).filter(file => fs.existsSync(path.join(root, file)))
const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'zgnf-mini-compile-'))
const compiledWxml = path.join(temporary, 'pages.js'), compiledWxss = path.join(temporary, 'styles.js')
try {
  execFileSync(wcc, ['-o', compiledWxml, ...templates], { cwd: root, windowsHide: true, stdio: 'pipe', maxBuffer: 16 * 1024 * 1024 })
  execFileSync(wcsc, ['-o', compiledWxss, ...styles], { cwd: root, windowsHide: true, stdio: 'pipe', maxBuffer: 16 * 1024 * 1024 })
  for (const output of [compiledWxml, compiledWxss]) if (!fs.existsSync(output) || !fs.statSync(output).size) throw new Error('WeChat compiler produced no output')
  console.log('WeChat native compilers accepted ' + templates.length + ' WXML pages and ' + styles.length + ' WXSS files. Device, API and merchant acceptance remain separate.')
} catch (error) {
  if (error.stderr) process.stderr.write(error.stderr)
  throw error
} finally {
  for (const output of [compiledWxml, compiledWxss]) if (fs.existsSync(output)) fs.unlinkSync(output)
  fs.rmdirSync(temporary)
}
