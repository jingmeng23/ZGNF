export function productImageUrl(value: string | null | undefined): string {
  if (!value) return ''
  if (value.startsWith('/api/v1/media/products/')) {
    const base = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
    return base + value.slice('/api/v1'.length)
  }
  return value
}
