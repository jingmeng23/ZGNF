import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
const require = createRequire(import.meta.url)
const data = require('province-city-china/data')
const version = 'province-city-china@' + require('province-city-china/package.json').version
const rows = new Map()
function add(code, parent, name, level) {
  if (!/^\d{6,12}$/.test(code) || !name || rows.has(code)) throw new Error('Invalid/duplicate region: ' + code)
  rows.set(code, [code, parent, name, level])
}
for (const r of data.province) add(r.code, null, r.name, 1)
for (const r of data.city) add(r.code, r.province + '0000', r.name, 2)
for (const r of data.area) {
  const city = r.province + r.city + '00'
  if (!rows.has(city)) {
    const province = rows.get(r.province + '0000')
    if (!['11','12','31','50'].includes(r.province) || !province) throw new Error('Missing city: ' + city)
    add(city, province[0], province[2], 2)
  }
  add(r.code, city, r.name, 3)
}
// The upstream snapshot lists 北泉镇 and its aliases four times under one code.
// Keep the first record so aliases cannot produce duplicate selector keys.
for (const r of data.town) {
  const code = r.code + (r.town.endsWith('000') ? r.town.slice(0,3) : r.town)
  if (!rows.has(code)) add(code, r.code, r.name, 4)
}
for (const row of rows.values()) {
  if (row[3] > 1 && rows.get(row[1])?.[3] !== row[3] - 1) throw new Error('Invalid parent: ' + row[0])
}
const dir = fileURLToPath(new URL('../../src/main/resources/regions/', import.meta.url))
mkdirSync(dir, { recursive: true })
writeFileSync(dir + 'province-city-china.json', JSON.stringify({ version, rows: [...rows.values()].sort((a,b)=>a[0].localeCompare(b[0])) }) + '\n')
console.log(JSON.stringify({version, counts: [1,2,3,4].map(level=>[...rows.values()].filter(r=>r[3]===level).length)}))
