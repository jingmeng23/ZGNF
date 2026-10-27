import { afterEach, describe, expect, it, vi } from 'vitest'
import { addressesApi } from './addresses'
afterEach(() => vi.unstubAllGlobals())
describe('address assistance API', () => {
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
