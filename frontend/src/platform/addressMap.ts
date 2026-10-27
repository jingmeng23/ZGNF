const limit = 85.05112878
export function project(latitude: number, longitude: number, zoom: number) {
  const size = 256 * 2 ** zoom, sin = Math.sin(Math.max(-limit, Math.min(limit, latitude)) * Math.PI / 180)
  return { x: (longitude + 180) / 360 * size, y: (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * size }
}
export function unproject(x: number, y: number, zoom: number) {
  const size = 256 * 2 ** zoom
  const longitude = ((x / size * 360) % 360 + 360) % 360 - 180
  const latitude = Math.atan(Math.sinh(Math.PI * (1 - 2 * Math.max(0, Math.min(size, y)) / size))) * 180 / Math.PI
  return { latitude: Number(latitude.toFixed(7)), longitude: Number(longitude.toFixed(7)) }
}
