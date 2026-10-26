import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { Cart, Product } from '../api/types'
import { cartApi } from '../api/commerce'
import { demoMode } from '../api/catalog'

export const useCartStore = defineStore('cart', () => {
  const cart = ref<Cart>({ items: [], invalidItems: [], total: 0, page: 1, size: 20, totalQuantity: 0, selectedQuantity: 0, selectedAmount: 0 })
  const busy = ref(false)
  const demoItems = ref<{ product: Product; quantity: number }[]>([])
  const items = computed(() => demoMode ? demoItems.value.map(item => ({ id: item.product.id, productId: item.product.id, productName: item.product.name, coverUrl: item.product.coverUrl, quantity: item.quantity, unitPrice: item.product.price, availableStock: 99, selected: true, available: true, subtotal: item.product.price * item.quantity, priceChanged: false })) : cart.value.items)
  const count = computed(() => demoMode ? demoItems.value.reduce((sum, item) => sum + item.quantity, 0) : cart.value.totalQuantity)
  const totalCents = computed(() => demoMode ? demoItems.value.reduce((sum, item) => sum + Math.round(item.product.price * 100) * item.quantity, 0) : Math.round(cart.value.selectedAmount * 100))
  async function refresh(page = cart.value.page || 1) { if (demoMode) return; busy.value = true; try { cart.value = await cartApi.get(page) } finally { busy.value = false } }
  async function add(product: Product) { if (demoMode) { const item = demoItems.value.find(row => row.product.id === product.id); if (item) item.quantity++; else demoItems.value.push({ product, quantity: 1 }); return }; await cartApi.add(product.id); await refresh() }
  async function change(id: string, delta: number) { if (demoMode) { const item = demoItems.value.find(row => row.product.id === id); if (!item) return; item.quantity += delta; demoItems.value = demoItems.value.filter(row => row.quantity > 0); return }; const item = cart.value.items.find(row => row.id === id); if (!item) return; if (item.quantity + delta <= 0) await cartApi.remove(id); else await cartApi.quantity(id, item.quantity + delta); await refresh() }
  async function select(id: string, selected: boolean) { cart.value = await cartApi.select(id, selected) }
  async function selectAll(selected: boolean) { cart.value = await cartApi.selectAll(selected) }
  async function removeSelected() { await cartApi.removeSelected(); await refresh(1) }
  async function removeMany(ids: string[]) { await cartApi.removeMany(ids); await refresh(1) }
  async function clear() { await cartApi.clear(); await refresh(1) }
  return { cart, items, count, totalCents, busy, refresh, add, change, select, selectAll, removeSelected, removeMany, clear }
})
