import { describe, expect, it } from 'vitest'
import { project, unproject } from './addressMap'
describe('address map coordinates', () => {
  it('round trips WGS84 points across zoom levels', () => {
    for (const zoom of [2, 4, 15, 18]) for (const [latitude, longitude] of [[29.34, 104.78], [-33.9, 151.2], [0, 0]]) {
      const pixel = project(latitude!, longitude!, zoom), point = unproject(pixel.x, pixel.y, zoom)
      expect(point.latitude).toBeCloseTo(latitude!, 6); expect(point.longitude).toBeCloseTo(longitude!, 6)
    }
  })
  it('wraps the date line and clamps polar tiles', () => {
    expect(unproject(256 * 2 ** 4 + 1, -10, 4).longitude).toBeGreaterThan(-180)
    expect(unproject(0, -10, 4).latitude).toBeLessThan(86)
  })
})
