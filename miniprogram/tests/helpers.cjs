const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { createRequire } = require('node:module')
function page(name, app = { globalData: {} }) {
  const file = path.resolve(__dirname, '../pages', name, 'index.js')
  let definition
  vm.runInNewContext(fs.readFileSync(file, 'utf8'), { require: createRequire(file), wx: global.wx, getApp: () => app, Page: value => { definition = value } })
  definition.data = JSON.parse(JSON.stringify(definition.data))
  definition.setData = function(values) {
    for (const [key, value] of Object.entries(values)) {
      const parts = key.split('.'), last = parts.pop()
      let target = this.data
      for (const part of parts) { if (!target[part]) target[part] = {}; target = target[part] }
      target[last] = value
    }
  }
  return definition
}
function event(dataset = {}, value) { return { currentTarget: { dataset }, detail: { value } } }
function storage() {
  const values = new Map()
  global.wx = {
    getStorageSync: key => values.get(key), setStorageSync: (key, value) => values.set(key, value), removeStorageSync: key => values.delete(key),
    showModal: o => o.success({ confirm: true }), showToast: () => {}, redirectTo: () => {}, navigateTo: () => {}, setClipboardData: () => {},
  }
  return values
}
module.exports = { page, event, storage }
