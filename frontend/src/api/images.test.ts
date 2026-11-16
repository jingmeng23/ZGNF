import { afterEach, expect, it, vi } from 'vitest'
import { productImageUrl } from './images'

afterEach(() => vi.unstubAllEnvs())

it('loads uploaded images from the API host when frontend and backend have different origins', () => {
  vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.com/api/v1/')
  expect(productImageUrl('/api/v1/media/products/one.png')).toBe(
    'https://api.example.com/api/v1/media/products/one.png',
  )
  expect(productImageUrl('/images/products/legacy.png')).toBe('/images/products/legacy.png')
  expect(productImageUrl('https://cdn.example.com/legacy.png')).toBe(
    'https://cdn.example.com/legacy.png',
  )
})

it('uses the API proxy for same-origin deployments and handles missing images', () => {
  vi.stubEnv('VITE_API_BASE_URL', '')
  expect(productImageUrl('/api/v1/media/products/one.png')).toBe('/api/v1/media/products/one.png')
  expect(productImageUrl(null)).toBe('')
})
