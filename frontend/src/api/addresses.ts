import { request } from './http'
import type { UserAddress } from './types'
export interface Region { code: string; parentCode: string | null; name: string; level: number; active: boolean; replacementCode: string | null; version: string }
export interface AddressInput { receiver: string; phone: string; province: string; city: string; district: string; detail: string; isDefault: boolean; label: string | null; provinceCode: string | null; cityCode: string | null; districtCode: string | null; latitude: number | null; longitude: number | null; allowDuplicate?: boolean }
export interface AddressDraft { receiver: string | null; phone: string | null; province: string | null; city: string | null; district: string | null; detail: string; provinceCode: string | null; cityCode: string | null; districtCode: string | null; needsConfirmation: boolean; warnings: string[] }
const root = '/users/me/addresses'
export const addressesApi = {
  list: () => request<UserAddress[]>(root),
  save: (id: string | null, input: AddressInput) => request<UserAddress>(id ? `${root}/${id}` : root, { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) }),
  remove: (id: string) => request<void>(`${root}/${id}`, { method: 'DELETE' }),
  setDefault: (id: string) => request<UserAddress>(`${root}/${id}/default`, { method: 'PUT' }),
  parse: (text: string) => request<AddressDraft>(`${root}/parse`, { method: 'POST', body: JSON.stringify({ text }) }),
  regions: (parent?: string) => request<Region[]>(`${root}/regions${parent ? `?parentCode=${encodeURIComponent(parent)}` : ''}`),
  reverse: (latitude: number, longitude: number) => request<AddressDraft>(`${root}/reverse-geocode`, { method: 'POST', body: JSON.stringify({ latitude, longitude, consent: true }) }),
}
