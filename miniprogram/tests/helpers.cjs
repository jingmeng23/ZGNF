const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { createRequire } = require('node:module')
function page(name, app = { globalData: {} }) {
  const file = path.resolve(__dirname, '../pages', name, 'index.js')
  let definition
  vm.runInNewContext(fs.readFileSync(file, 'utf8'), { require: createRequire(file), wx: global.wx, getApp: () => app, getCurrentPages: () => app.pages || [], Page: value => { definition = value } })
  definition.data = JSON.parse(JSON.stringify(definition.data))
  definition.setData = function(values) {
    for (const [key, value] of Object.entries(values)) {
      // WeChat uses list[0].field for array elements. Reject numeric dot paths
      // rather than accepting them as ordinary JS access and hiding runtime bugs.
      const parts = Array.from(key.matchAll(/([^.[\]]+)|\[(\d+)\]/g), match => ({ key: match[1] || match[2], index: match[2] !== undefined }))
      const last = parts.pop()
      let target = this.data
      for (let i = 0; i < parts.length; i++) {
        const part = parts[i], next = parts[i + 1] || last
        if (Array.isArray(target) && !part.index) throw new TypeError('Use bracket indices in setData array paths: ' + key)
        if (!target[part.key]) target[part.key] = next.index ? [] : {}
        target = target[part.key]
      }
      if (Array.isArray(target) && !last.index) throw new TypeError('Use bracket indices in setData array paths: ' + key)
      target[last.key] = value
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
