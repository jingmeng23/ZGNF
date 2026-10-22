import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { Product } from '../api/types'
import { demoMode } from '../api/catalog'

export const useCartStore = defineStore('cart', () => {
  const items = ref<{ product: Product; quantity: number }[]>([])
  const count = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))
  const totalCents = computed(() => items.value.reduce((sum, item) => sum + Math.round(item.product.price * 100) * item.quantity, 0))
  function add(product: Product) {
    if (!demoMode) return
    const item = items.value.find(item => item.product.id === product.id)
    if (item) item.quantity = Math.min(99, item.quantity + 1)
    else items.value.push({ product, quantity: 1 })
  }
  function change(id: string, delta: number) {
    const item = items.value.find(item => item.product.id === id)
    if (!item) return
    item.quantity = Math.min(99, item.quantity + delta)
    items.value = items.value.filter(item => item.quantity > 0)
  }
  return { items, count, totalCents, add, change }
})
