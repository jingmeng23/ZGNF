import { afterEach, describe, expect, it, vi } from 'vitest'
import { addressesApi } from './addresses'
afterEach(() => vi.unstubAllGlobals())
describe('address assistance API', () => {
  it('sends only the agreed fields and keeps duplicate consent in the URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 200, data: {} }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const input = { receiver: '张三', phone: '13800138000', provinceCode: '440000', cityCode: '440300', districtCode: '440305', province: '广东省', city: '深圳市', district: '南山区', detail: '科技园科苑路15号', fullAddress: '广东省深圳市南山区科技园科苑路15号', longitude: null, latitude: null, adcode: '440305', isDefault: true }
    await addressesApi.save(null, input, true)
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/v1/users/me/addresses?allowDuplicate=true')
    expect(JSON.parse(fetchMock.mock.calls[0]![1].body)).toEqual(input)
  })
  it('only returns a draft during parsing; saving and duplicate consent are explicit', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ code: 200, data: {} }), { status: 200 })))
    vi.stubGlobal('fetch', fetchMock)
    await addressesApi.parse('张三 13800138000')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/v1/users/me/addresses/parse')
    await addressesApi.reverse(29.34, 104.78)
    expect(JSON.parse(fetchMock.mock.calls[1]![1].body)).toEqual({ latitude: 29.34, longitude: 104.78, consent: true })
  })
})
