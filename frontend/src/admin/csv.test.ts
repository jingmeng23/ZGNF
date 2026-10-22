import { describe, expect, it } from 'vitest'
import { toCsv } from './csv'

describe('CSV export', () => {
  it('preserves Chinese text, quotes and newlines in quoted cells', () => {
    expect(toCsv([{ key: 'name', label: '名称' }], [{ id: '1', name: '燃气,"安装"\n服务' }])).toBe(
      '"名称"\r\n"燃气,""安装""\n服务"',
    )
  })
  it('prevents formula evaluation while preserving numeric values', () => {
    const csv = toCsv(
      [
        { key: 'name', label: '客户' },
        { key: 'amount', label: '金额' },
      ],
      [{ id: '1', name: '=1+1', amount: -12 }],
    )
    expect(csv).toContain('"\'=1+1","-12"')
  })
  it('exports headers for an empty list', () => {
    expect(toCsv([{ key: 'id', label: '编号' }], [])).toBe('"编号"')
  })
})
