import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { Cart, CartItem, Product } from '../api/types'
import { cartApi } from '../api/commerce'
import { demoMode } from '../api/catalog'
import { cents } from '../components/cartModel'

const emptyCart = (): Cart => ({
  items: [],
  invalidItems: [],
  total: 0,
  page: 1,
  size: 100,
  totalQuantity: 0,
  selectedQuantity: 0,
  selectedAmount: 0,
})
export const useCartStore = defineStore('cart', () => {
  const cart = ref<Cart>(emptyCart()),
    loading = ref(false),
    pending = ref(0),
    loadError = ref('')
  const busy = computed(() => loading.value || pending.value > 0)
  const items = computed(() => cart.value.items)
  const count = computed(() => cart.value.totalQuantity)
  const totalCents = computed(() => cents(cart.value.selectedAmount))
  let generation = 0,
    revision = 0,
    tail: Promise<unknown> = Promise.resolve()

  function adopt(result: Cart) {
    cart.value = result
    loadError.value = ''
  }
  function demoTotals() {
    const selected = items.value.filter((i) => i.available && i.selected)
    cart.value = {
      ...cart.value,
      total: items.value.length,
      totalQuantity: [...items.value, ...cart.value.invalidItems].reduce(
        (sum, i) => sum + i.quantity,
        0,
      ),
      selectedQuantity: selected.reduce((sum, i) => sum + i.quantity, 0),
      selectedAmount: selected.reduce((sum, i) => sum + cents(i.unitPrice) * i.quantity, 0) / 100,
    }
  }
  async function readAll(): Promise<Cart> {
    const first = await cartApi.get(1, 100)
    const rows = [...first.items]
    for (let page = 2; rows.length < first.total; page++) {
      const next = await cartApi.get(page, 100)
      if (
        next.total !== first.total ||
        next.totalQuantity !== first.totalQuantity ||
        next.selectedAmount !== first.selectedAmount ||
        !next.items.length
      )
        throw new Error('购物车内容已变化，请重新加载')
      rows.push(...next.items)
    }
    if (new Set(rows.map((i) => i.id)).size !== rows.length)
      throw new Error('购物车内容已变化，请重新加载')
    return { ...first, items: rows }
  }
  async function refresh(_page = 1) {
    if (demoMode) {
      demoTotals()
      return
    }
    const epoch = generation,
      token = ++revision
    loading.value = true
    loadError.value = ''
    try {
      await tail
      if (epoch !== generation) return
      const result = await readAll()
      if (epoch === generation && token === revision) adopt(result)
    } catch (e) {
      if (epoch === generation && token === revision) loadError.value = (e as Error).message
      throw e
    } finally {
      if (epoch === generation && token === revision) loading.value = false
    }
  }
  // Serialize writes for the whole cart. Never apply a response from a previous login session.
  function mutate(work: () => Promise<Cart | void>) {
    const epoch = generation
    revision++
    loading.value = false
    pending.value++
    const job = tail.then(async () => {
      if (epoch !== generation) throw new Error('登录状态已变化，请重试')
      try {
        const result = await work()
        if (epoch === generation) {
          if (demoMode) demoTotals()
          else {
            const current = result || (await readAll())
            if (epoch === generation) adopt(current)
          }
        }
      } catch (e) {
        if (epoch === generation && !demoMode) {
          try {
            const current = await readAll()
            if (epoch === generation) adopt(current)
          } catch {
            if (epoch === generation) loadError.value = '购物车状态尚未确认，请重新加载后继续'
          }
        }
        throw e
      } finally {
        if (epoch === generation) pending.value--
      }
    })
    tail = job.catch(() => {})
    return job
  }
  function reset() {
    generation++
    revision++
    loading.value = false
    pending.value = 0
    loadError.value = ''
    cart.value = emptyCart()
  }
  async function add(product: Product, quantity = 1) {
    if (product.skuCount) throw new Error('该商品暂不支持在线购买')
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 999)
      throw new Error('购买数量须为 1 至 999 的整数')
    return mutate(async () => {
      if (!demoMode) return cartApi.add(product.id, quantity)
      const item = items.value.find((i) => i.productId === product.id)
      if ((item?.quantity || 0) + quantity > Math.min(product.availableStock ?? 99, 999))
        throw new Error('数量超过可用库存或购物车上限')
      if (item) {
        item.quantity += quantity
        item.subtotal = (cents(item.unitPrice) * item.quantity) / 100
        item.selected = true
      } else
        items.value.push({
          id: product.id,
          productId: product.id,
          productName: product.name,
          coverUrl: product.coverUrl,
          quantity,
          unitPrice: product.price,
          availableStock: product.availableStock ?? 99,
          selected: true,
          available: true,
          subtotal: (cents(product.price) * quantity) / 100,
          priceChanged: false,
        })
    })
  }
  async function setQuantity(id: string, quantity: number) {
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 999)
      throw new Error('数量须为 1 至 999 的整数')
    return mutate(async () => {
      const item = items.value.find((i) => i.id === id)
      if (!item || !item.available) throw new Error('商品已失效，请刷新购物车')
      if (quantity > item.availableStock) throw new Error('数量超过可用库存')
      if (!demoMode) return cartApi.quantity(id, quantity)
      item.quantity = quantity
      item.subtotal = (cents(item.unitPrice) * quantity) / 100
    })
  }
  async function select(id: string, selected: boolean) {
    return mutate(async () => {
      const item = [...items.value, ...cart.value.invalidItems].find((i) => i.id === id)
      if (!item || (selected && !item.available)) throw new Error('商品不可选择')
      if (!demoMode) return cartApi.select(id, selected)
      item.selected = selected
    })
  }
  async function selectAll(selected: boolean) {
    return mutate(async () => {
      if (!demoMode) return cartApi.selectAll(selected)
      for (const item of [...items.value, ...cart.value.invalidItems])
        item.selected = selected && item.available
    })
  }
  async function removeMany(ids: string[]) {
    if (!ids.length) return
    return mutate(async () => {
      if (demoMode) {
        const removed = new Set(ids)
        cart.value.items = items.value.filter((i) => !removed.has(i.id))
        cart.value.invalidItems = cart.value.invalidItems.filter((i) => !removed.has(i.id))
        return
      }
      for (let start = 0; start < ids.length; start += 100)
        await cartApi.removeMany(ids.slice(start, start + 100))
    })
  }
  async function change(id: string, delta: number) {
    const item = [...items.value, ...cart.value.invalidItems].find((i) => i.id === id)
    if (!item) return
    if (item.quantity + delta <= 0) return removeMany([id])
    return setQuantity(id, item.quantity + delta)
  }
  const removeSelected = () =>
    removeMany(items.value.filter((i) => i.available && i.selected).map((i) => i.id))
  const clear = () => removeMany([...items.value, ...cart.value.invalidItems].map((i) => i.id))
  return {
    cart,
    items,
    count,
    totalCents,
    busy,
    loadError,
    refresh,
    refreshAll: refresh,
    add,
    reset,
    setQuantity,
    change,
    select,
    selectAll,
    removeSelected,
    removeMany,
    clear,
  }
})
