const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { createRequire } = require('node:module')
const root = path.resolve(__dirname, '..')
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
function markup(source, page) {
  const stack = []
  const tags = source.matchAll(/<!--[\s\S]*?-->|<\/?[\w:-]+\b(?:[^"'<>]|"[^"]*"|'[^']*')*\/?>/g)
  for (const [tag] of tags) {
    if (tag.startsWith('<!--')) continue
    const name = tag.match(/^<\/?([\w:-]+)/)[1]
    if (tag.startsWith('</')) {
      if (stack.pop() !== name) throw new Error(page + ': unbalanced WXML tag ' + name)
    } else if (!tag.endsWith('/>')) stack.push(name)
  }
  if (stack.length) throw new Error(page + ': unclosed WXML tag ' + stack.pop())
  for (const match of source.matchAll(/{{([\s\S]*?)}}/g)) {
    if (/&(?:amp|lt|gt);/.test(match[1])) throw new Error(page + ': WXML expressions must use literal operators')
    new vm.Script('(' + match[1] + ')', { filename: page + '.wxml expression' })
  }
  if (/&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[\da-fA-F]+;)/.test(source.replace(/{{[\s\S]*?}}/g, ''))) throw new Error(page + ': unescaped WXML text ampersand')
}
for (const page of app.pages) {
  const file = path.join(root, page)
  const config = JSON.parse(fs.readFileSync(file + '.json', 'utf8'))
  if (!config || typeof config !== 'object' || Array.isArray(config)) throw new Error(page + ': invalid page JSON configuration')
  const js = fs.readFileSync(file + '.js', 'utf8')
  const wxml = fs.readFileSync(file + '.wxml', 'utf8')
  if (/wx:(?:if|elif|for)="(?!{{)/.test(wxml)) throw new Error(page + ': dynamic directives require {{ expressions }}')
  let definition
  vm.runInNewContext(js, { require: createRequire(file + '.js'), Page: value => { definition = value } }, { filename: page + '.js' })
  if (!definition || !definition.data) throw new Error(page + ': missing Page definition')
  for (const match of wxml.matchAll(/(?:bind|catch):?[a-zA-Z]+="([a-zA-Z0-9_]+)"/g)) {
    if (typeof definition[match[1]] !== 'function') throw new Error(page + ': missing handler ' + match[1])
  }
  if (/<(?:div|span|br|a|form)\b/.test(wxml)) throw new Error(page + ': non-native HTML element')
  markup(wxml, page)
}
let scripts = 0
function checkScripts(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && !['node_modules', 'miniprogram_npm', '.git'].includes(entry.name)) checkScripts(path.join(directory, entry.name))
    if (entry.isFile() && /\.(?:js|cjs)$/.test(entry.name)) {
      const file = path.join(directory, entry.name), source = fs.readFileSync(file, 'utf8')
      new vm.Script(source, { filename: path.relative(root, file) }); scripts++
      for (const match of source.matchAll(/require\(['"](\.[^'"]+)['"]\)/g)) createRequire(file).resolve(match[1])
    }
  }
}
checkScripts(root)
for (const tab of app.tabBar.list) if (!app.pages.includes(tab.pagePath)) throw new Error('Unknown tab ' + tab.pagePath)
console.log('Validated ' + app.pages.length + ' native pages, WXML structure, routes and handlers; ' + scripts + ' scripts and local dependencies. WeChat Developer Tools compilation remains required.')
