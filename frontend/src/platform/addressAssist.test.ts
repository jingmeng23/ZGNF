import { describe, expect, it } from 'vitest'
import { municipalityCity, parseAddress, PARSE_THRESHOLD } from './addressAssist'

describe('address recognition safeguards', () => {
  it.each([
    '张三 13800138000 四川省自贡市贡井区贡井街道广场路12号',
    '收件人：张三\n联系电话：138 0013 8000\n所在地区：四川省 自贡市 贡井区\n详细地址：贡井街道广场路12号',
    '张三 +86 13800138000 四川省自贡市贡井区贡井街道广场路12号',
    '张三 １３８００１３８０００ 四川省自贡市贡井区贡井街道广场路１２号',
    '收件人：张三，手机号码：138-0013-8000，四川省自贡市贡井区贡井街道广场路12号',
  ])('recognizes pasted Zigong information: %s', text => {
    const { result, confidence } = parseAddress(text)
    expect(confidence).toBeGreaterThanOrEqual(PARSE_THRESHOLD)
    expect(result).toMatchObject({ name: '张三', mobile: '13800138000', code: '510303', details: '贡井街道广场路12号' })
  })
  it('recognizes a complete delivery address', () => {
    const { result, confidence } = parseAddress('张三 13800138000 广东省深圳市南山区科技园科苑路15号')
    expect(confidence).toBeGreaterThanOrEqual(PARSE_THRESHOLD)
    expect(result).toMatchObject({ name: '张三', mobile: '13800138000', province: '广东省', city: '深圳市', area: '南山区', code: '440305' })
  })
  it('does not automatically apply ambiguous or incomplete input', () => {
    for (const text of ['随便一段文字', '张三 13800138000', '张三 13800138000 13900139000 北京市朝阳区望京街道1号', '张三 138 0013 8000 139-0013-9000 四川省自贡市贡井区贡井街道广场路12号']) {
      expect(parseAddress(text).confidence).toBeLessThan(PARSE_THRESHOLD)
    }
  })
  it('falls back to district when municipality city is an empty array', () => {
    expect(municipalityCity({ province: '北京市', city: [], district: '朝阳区' })).toBe('朝阳区')
    expect(municipalityCity({ province: '广东省', city: '深圳市', district: '南山区' })).toBe('深圳市')
  })
})
