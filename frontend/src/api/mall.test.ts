import { afterEach, describe, expect, it, vi } from 'vitest'
import { mallApi, mallParams } from './mall'
afterEach(() => vi.unstubAllGlobals())
describe('mall API', () => {
  it('encodes all filters including attribute names and zero price', () => {
    expect(
      Object.fromEntries(
        mallParams({
          minPrice: '0',
          maxPrice: '50.00',
          inStock: true,
          brandId: '42',
          attributes: { 气源: '天然气', empty: '' },
        }),
      ),
    ).toEqual({
      minPrice: '0',
      maxPrice: '50.00',
      inStock: 'true',
      brandId: '42',
      'attributes[气源]': '天然气',
    })
  })
  it('encodes matrix selections and preserves SKU IDs as strings', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 200,
            data: { dimensions: [], skus: [], selectedSkuId: '9007199254740993' },
          }),
          { status: 200 },
        ),
      )
    vi.stubGlobal('fetch', fetchMock)
    expect((await mallApi.matrix('42', { 颜色: '红' })).selectedSkuId).toBe('9007199254740993')
    const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost')
    expect(JSON.parse(url.searchParams.get('selection')!)).toEqual({ 颜色: '红' })
  })
  it('uses idempotent PUT and DELETE for favorite toggles', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ code: 200, data: null }), { status: 200 })),
      )
    vi.stubGlobal('fetch', fetchMock)
    await mallApi.favorite('42', true)
    await mallApi.favorite('42', false)
    expect(fetchMock.mock.calls.map((c) => c[1].method)).toEqual(['PUT', 'DELETE'])
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/mall/me/favorites/42')
  })
})
