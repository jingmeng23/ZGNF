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
  brandName?: string | null
  brandId?: string; status?: string; statusMessage?: string; purchasable?: boolean
  categoryId?: string; description?: string | null; detailImages?: string[]
  id: string; name: string; subtitle: string; coverUrl: string | null
  price: number; originalPrice: number | null; sales: number; availableStock?: number
}
export interface Category { id: string; parentId: string; name: string; sort: number; status: string }
export interface CartItem { id: string; productId: string; productName: string; coverUrl: string | null; quantity: number; unitPrice: number; availableStock: number; selected: boolean; available: boolean; subtotal: number; priceChanged: boolean; degraded?: boolean }
export interface Cart { items: CartItem[]; invalidItems: CartItem[]; total: number; page: number; size: number; totalQuantity: number; selectedQuantity: number; selectedAmount: number }
export interface UserAddress { id: string; receiver: string; phone: string; province: string; city: string; district: string; detail: string; isDefault: boolean; label?: string | null; provinceCode?: string | null; cityCode?: string | null; districtCode?: string | null; latitude?: number | null; longitude?: number | null; coordinateSystem?: string; needsRegionCompletion?: boolean; regionMessage?: string | null; suggestedDistrictCode?: string | null }
export interface Order { id: string; orderNo: string; status: string; totalAmount: number; payAmount: number; createTime: string; items: { productName: string; quantity: number; unitPrice: number }[] }
