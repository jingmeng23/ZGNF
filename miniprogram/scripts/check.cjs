const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
for (const page of app.pages) {
  const file = path.join(root, page)
  const js = fs.readFileSync(file + '.js', 'utf8')
  const wxml = fs.readFileSync(file + '.wxml', 'utf8')
  new vm.Script(js, { filename: page + '.js' })
  let definition
  vm.runInNewContext(js, { require: () => ({}), Page: value => { definition = value } })
  for (const match of wxml.matchAll(/(?:bind|catch)(?:tap|input|change|confirm)="([a-zA-Z0-9_]+)"/g)) {
    if (typeof definition[match[1]] !== 'function') throw new Error(page + ': missing handler ' + match[1])
  }
  if (/<(?:div|span|br|a|form)\b/.test(wxml)) throw new Error(page + ': non-native HTML element')
}
for (const file of ['app.js', 'config.js', 'utils/api.js', 'utils/payment.js']) new vm.Script(fs.readFileSync(path.join(root, file), 'utf8'), { filename: file })
for (const tab of app.tabBar.list) if (!app.pages.includes(tab.pagePath)) throw new Error('Unknown tab ' + tab.pagePath)
console.log('Validated ' + app.pages.length + ' native pages, routes, JS syntax and event handlers. WeChat Developer Tools compilation remains required.')
