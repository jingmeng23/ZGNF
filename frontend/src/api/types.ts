export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId: string
}
export interface PageResult<T> { list: T[]; total: number; page: number; size: number; pages: number }
export interface LoginResponse { accessToken: string; refreshToken: string; expiresIn: number }
export interface CurrentUser { userId: string }
// Java Long IDs remain strings to prevent precision loss.
export interface Product {
  id: string; name: string; subtitle: string; coverUrl: string | null
  price: number; originalPrice: number; sales: number
}
