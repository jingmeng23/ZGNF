import * as AddressParseModule from 'address-parse'
export interface ParsedAddress { name: string; mobile: string; province: string; city: string; area: string; details: string; code: string }
type Parser = { parse(text: string, all?: boolean): unknown[] }
// CommonJS exposes { default: parser }; Vite/Vitest can wrap that object again.
function parserInstance(module: unknown): Parser {
  let value = module as { parse?: unknown; default?: unknown } | undefined
  for (let depth = 0; depth < 4 && value; depth++) {
    if (typeof value.parse === 'function') return value as Parser
    value = value.default as typeof value
  }
  throw new Error('地址解析器加载失败，请刷新页面重试。')
}
export function parseAddress(text: string) {
  let normalized = text.normalize('NFKC').trim()
  const phonePattern = /(?<!\d)(?:\+?86[ -]*)?(1[3-9]\d[ -]*\d{4}[ -]*\d{4})(?!\d)/g
  const phones = [...normalized.matchAll(phonePattern)]
  if (phones.length > 1) return { result: undefined, confidence: 0 }
  normalized = normalized.replace(phonePattern, (_match, phone: string) => phone.replace(/[ -]/g, ''))
    .replace(/(?:收货人|收件人|联系人|姓名|联系电话|手机号码|手机号|手机|电话|所在地区|收货地址|详细地址|地址)\s*[:：]\s*/g, ' ')
    .replace(/[\r\n\t]+/g, ' ')
  const result = parserInstance(AddressParseModule).parse(normalized, true)[0] as ParsedAddress | undefined
  if (!result) return { result, confidence: 0 }
  const confidence = [result.name?.length >= 2 && result.name.length <= 20, /^1[3-9]\d{9}$/.test(result.mobile ?? ''), !!result.province, !!result.city, !!result.area && !!result.code, result.details?.length >= 5].filter(Boolean).length / 6
  return { result, confidence }
}
export const PARSE_THRESHOLD = 0.95
export async function uploadToOcr(_file: File): Promise<void> {}
export function municipalityCity(c: { province?: string; city?: string | string[]; district?: string }) {
  return (typeof c.city === 'string' ? c.city : c.city?.[0]) || c.district || c.province || ''
}
