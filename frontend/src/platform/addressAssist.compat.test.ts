import { expect, it, vi } from 'vitest'
// Reproduce the nested default export shape seen in the browser bundle.
vi.mock('address-parse', () => ({ parse: undefined, default: { default: {
  parse: () => [{ name: '张三', mobile: '13800138000', province: '四川省', city: '自贡市', area: '贡井区', code: '510303', details: '贡井街道广场路12号' }],
} } }))
import { parseAddress, PARSE_THRESHOLD } from './addressAssist'
it('unwraps CommonJS default exports instead of calling parse on the wrapper', () => {
  expect(parseAddress('张三 13800138000 四川省自贡市贡井区贡井街道广场路12号').confidence).toBeGreaterThanOrEqual(PARSE_THRESHOLD)
})
