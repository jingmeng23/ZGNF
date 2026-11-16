<script setup lang="ts">
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { ArrowRight, ChevronDown, Heart, MapPin, ShoppingBag, Trash2, X } from 'lucide-vue-next'
import ShopHeader from '../components/ShopHeader.vue'
import ProductImage from '../components/ProductImage.vue'
import CartQuantity from '../components/CartQuantity.vue'
import { useCartStore } from '../stores/cart'
import { useAuthStore } from '../stores/auth'
import { addressApi, orderApi, type CheckoutQuote } from '../api/commerce'
import { couponsApi, type UserCoupon } from '../api/coupons'
import { mallApi } from '../api/mall'
import type { CartItem, UserAddress } from '../api/types'
import { demoMode } from '../api/catalog'
import { formatPrice } from '../components/shop'
import {
  cartSignature,
  cents,
  changedProducts,
  couponReason,
  selectionState,
} from '../components/cartModel'
const cart = useCartStore(),
  auth = useAuthStore()
const initialLoading = ref(true),
  pageError = ref(''),
  message = ref(''),
  actionError = ref('')
const editing = reactive(new Set<string>()),
  favorites = reactive(new Set<string>())
const checkoutBusy = ref(false),
  submitting = ref(false),
  summaryExpanded = ref(false)
const addresses = ref<UserAddress[]>([]),
  addressId = ref(''),
  addressLoading = ref(false),
  addressError = ref('')
const coupons = ref<UserCoupon[]>([]),
  eligibleIds = ref(new Set<string>()),
  couponId = ref(''),
  couponError = ref('')
const calculating = ref(false),
  quoteError = ref(''),
  quote = ref<CheckoutQuote | null>(null)
const dialog = ref<HTMLDialogElement>(),
  dialogMode = ref<'delete' | 'checkout' | null>(null),
  dialogError = ref('')
const deleteItems = ref<CartItem[]>([]),
  confirmedQuote = ref<CheckoutQuote | null>(null)
const summaryElement = ref<HTMLElement>(),
  addressInput = ref<HTMLSelectElement>(),
  pageHeading = ref<HTMLHeadingElement>()
let lastFocus: HTMLElement | null = null,
  returnFocus: HTMLElement | null = null,
  calculation = 0,
  addressSequence = 0,
  loadSequence = 0,
  alive = true,
  calculationTimer: ReturnType<typeof setTimeout> | undefined
const availableItems = computed(() => cart.items.filter((i) => i.available))
const invalidItems = computed(() => [
  ...cart.cart.invalidItems,
  ...cart.items.filter((i) => !i.available),
])
const selected = computed(() => selectionState(cart.items))
const kinds = computed(
  () => new Set([...cart.items, ...cart.cart.invalidItems].map((i) => i.productId)).size,
)
const locked = computed(() => checkoutBusy.value || submitting.value || !!dialogMode.value)
const unsettled = computed(() => cart.busy || editing.size > 0 || calculating.value)
const canCheckout = computed(
  () =>
    selected.value.quantity > 0 &&
    !unsettled.value &&
    !locked.value &&
    !cart.loadError &&
    !couponError.value &&
    !quoteError.value &&
    !demoMode,
)
const amount = computed(() => quote.value?.payAmount ?? cart.cart.selectedAmount)
const eligibilityAmount = computed(() =>
  quote.value
    ? Math.max(0, cents(quote.value.totalAmount) - cents(quote.value.fullReductionAmount ?? 0))
    : cents(cart.cart.selectedAmount),
)
const allItems = computed(() => [...cart.items, ...cart.cart.invalidItems])
function pendingEdit(id: string, pending: boolean) {
  if (pending) editing.add(id)
  else editing.delete(id)
}
async function loadAddresses() {
  if (demoMode || !auth.authenticated) return
  const token = ++addressSequence
  addressLoading.value = true
  addressError.value = ''
  try {
    const result = await addressApi.list()
    if (!alive || token !== addressSequence || !auth.authenticated) return
    addresses.value = result
    if (addressId.value && !addresses.value.some((a) => a.id === addressId.value))
      addressId.value = ''
  } catch (e) {
    if (alive && token === addressSequence) addressError.value = (e as Error).message
  } finally {
    if (alive && token === addressSequence) addressLoading.value = false
  }
}
async function load() {
  const token = ++loadSequence
  if (!demoMode && !auth.authenticated) {
    initialLoading.value = false
    return
  }
  initialLoading.value = true
  pageError.value = ''
  actionError.value = ''
  try {
    await cart.refreshAll()
  } catch (e) {
    if (alive && token === loadSequence) pageError.value = (e as Error).message
  } finally {
    if (alive && token === loadSequence) initialLoading.value = false
  }
  if (alive && token === loadSequence) void loadAddresses()
}
async function allCoupons(orderAmount?: number) {
  const result: UserCoupon[] = []
  let page = 1,
    pages = 1
  do {
    const response = await couponsApi.mine(page, 'unused', orderAmount)
    result.push(...response.list)
    pages = response.pages
    page++
  } while (page <= pages)
  return result
}
function reason(coupon: UserCoupon) {
  return selected.value.quantity
    ? couponReason(coupon, eligibilityAmount.value, eligibleIds.value)
    : '请先选择有效商品'
}
function scheduleCalculation() {
  clearTimeout(calculationTimer)
  const token = ++calculation
  quote.value = null
  quoteError.value = ''
  couponError.value = ''
  if (locked.value || demoMode || initialLoading.value || !auth.authenticated) {
    calculating.value = false
    return
  }
  calculating.value = !!selected.value.quantity
  if (cart.busy || editing.size) return
  calculationTimer = setTimeout(() => {
    void calculate(token)
  }, 180)
}
async function calculate(token: number) {
  if (!selected.value.quantity) {
    calculating.value = false
    eligibleIds.value = new Set()
    return
  }
  const address = addresses.value.find((a) => a.id === addressId.value)
  try {
    const invalidSelections = invalidItems.value.filter((i) => i.selected)
    if (invalidSelections.length) {
      for (const item of invalidSelections) await cart.select(item.id, false)
      scheduleCalculation()
      return
    }
    const base =
      address && !address.needsRegionCompletion ? await orderApi.preview(address.id) : null
    if (token !== calculation || !alive) return
    const effectiveAmount = base
      ? Math.max(0, cents(base.totalAmount) - cents(base.fullReductionAmount ?? 0)) / 100
      : cart.cart.selectedAmount
    const [all, eligible] = await Promise.all([allCoupons(), allCoupons(effectiveAmount)])
    if (token !== calculation || !alive) return
    coupons.value = all
    eligibleIds.value = new Set(eligible.map((c) => c.id))
    if (couponId.value && !eligibleIds.value.has(couponId.value)) {
      couponId.value = ''
      message.value = '所选优惠券已不适用，已取消使用，请重新核对优惠。'
      return
    }
    const result =
      base && couponId.value ? await orderApi.preview(address!.id, couponId.value) : base
    if (token === calculation && alive) quote.value = result
  } catch (e) {
    if (token === calculation && alive) {
      quote.value = null
      quoteError.value = (e as Error).message
      couponError.value = '金额或优惠暂时无法确认，请重试'
      eligibleIds.value = new Set()
    }
  } finally {
    if (token === calculation && alive) calculating.value = false
  }
}
async function selectionAction(item: CartItem | null, checked: boolean) {
  if (locked.value || editing.size) return
  actionError.value = ''
  try {
    if (item) await cart.select(item.id, checked)
    else await cart.selectAll(checked)
  } catch (e) {
    actionError.value = (e as Error).message
  }
}
async function favorite(item: CartItem) {
  if (favorites.has(item.id)) return
  favorites.add(item.id)
  actionError.value = ''
  try {
    await mallApi.favorite(item.productId, true)
    message.value = `已收藏「${item.productName}」，商品仍保留在购物车。`
  } catch (e) {
    actionError.value = (e as Error).message
  } finally {
    favorites.delete(item.id)
  }
}
async function openDialog(mode: 'delete' | 'checkout', trigger?: HTMLElement) {
  lastFocus = trigger || (document.activeElement as HTMLElement)
  dialogError.value = ''
  dialogMode.value = mode
  await nextTick()
  dialog.value?.showModal()
}
function closeDialog() {
  if (!submitting.value) dialog.value?.close()
}
function dialogClosed() {
  dialogMode.value = null
  returnFocus = lastFocus
  lastFocus = null
  if (!submitting.value && !quote.value) scheduleCalculation()
  void nextTick(restoreDialogFocus)
}
function restoreDialogFocus() {
  if (!returnFocus || dialogMode.value) return
  if (returnFocus.isConnected && returnFocus.matches(':disabled')) return
  const target = returnFocus.isConnected ? returnFocus : pageHeading.value
  returnFocus = null
  target?.focus()
}
function confirmDelete(items: CartItem[]) {
  if (locked.value || unsettled.value || !items.length) return
  deleteItems.value = items.map((i) => ({ ...i }))
  void openDialog('delete')
}
async function deleteConfirmed() {
  if (submitting.value) return
  submitting.value = true
  dialogError.value = ''
  try {
    await cart.removeMany(deleteItems.value.map((i) => i.id))
    message.value = `已删除 ${deleteItems.value.length} 个购物车条目。`
    submitting.value = false
    dialog.value?.close()
  } catch (e) {
    dialogError.value = `${(e as Error).message}。请核对当前购物车后重试。`
  } finally {
    submitting.value = false
  }
}
async function checkout(event?: Event) {
  if (!canCheckout.value) return
  // Capture the trigger before loading disables it and the browser moves focus.
  const trigger =
    event?.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : (document.activeElement as HTMLElement)
  actionError.value = ''
  message.value = ''
  const address = addresses.value.find((a) => a.id === addressId.value)
  if (!address) {
    actionError.value = '请选择收货地址，以确认配送范围及运费。'
    summaryExpanded.value = true
    await nextTick()
    addressInput.value?.focus()
    summaryElement.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  if (address.needsRegionCompletion) {
    actionError.value = address.regionMessage || '收货地址信息不完整，请先补全地址。'
    return
  }
  checkoutBusy.value = true
  const before = allItems.value.map((i) => ({ ...i })),
    signature = cartSignature(cart.items)
  try {
    await cart.refreshAll()
    const changed = changedProducts(before, allItems.value)
    if (signature !== cartSignature(cart.items)) {
      actionError.value = changed.length
        ? `以下商品的价格、数量或购买资格已变化，请重新核对：${changed.join('、')}`
        : '商品库存或选中状态已变化，请重新核对后结算。'
      return
    }
    for (const invalid of cart.cart.invalidItems.filter((i) => i.selected))
      await cart.select(invalid.id, false)
    if (!selected.value.quantity) {
      actionError.value = '请先选择可购买商品。'
      return
    }
    const fresh = await orderApi.preview(address.id, couponId.value)
    const expected = cart.items.filter((row) => row.selected && row.available)
    const mismatch = expected.filter(
      (row) =>
        !fresh.items?.some(
          (i) =>
            row.productId === i.productId &&
            row.quantity === i.quantity &&
            cents(row.unitPrice) === cents(i.unitPrice),
        ),
    )
    if (mismatch.length || fresh.items?.length !== expected.length) {
      actionError.value = `结算报价已变化，请核对：${(mismatch.length ? mismatch : expected).map((i) => i.productName).join('、')}`
      await cart.refreshAll()
      return
    }
    quote.value = fresh
    confirmedQuote.value = fresh
    await openDialog('checkout', trigger)
  } catch (e) {
    actionError.value = (e as Error).message
    try {
      await cart.refreshAll()
    } catch {}
  } finally {
    checkoutBusy.value = false
    if (!dialogMode.value) scheduleCalculation()
  }
}
async function submitOrder() {
  if (!confirmedQuote.value || submitting.value) return
  submitting.value = true
  dialogError.value = ''
  try {
    const order = await orderApi.create(addressId.value, couponId.value, confirmedQuote.value)
    message.value = `订单 ${order.orderNo} 已创建，可在“我的订单”中查看。`
    submitting.value = false
    dialog.value?.close()
    confirmedQuote.value = null
    couponId.value = ''
    quote.value = null
    try {
      await cart.refreshAll()
    } catch {
      actionError.value = '订单已创建，购物车暂时无法刷新，请重新加载。'
    }
  } catch (e) {
    dialogError.value = `${(e as Error).message}。商品选择已保留；可重试或返回购物车核对。`
  } finally {
    submitting.value = false
  }
}
function showSummary() {
  summaryExpanded.value = !summaryExpanded.value
  if (summaryExpanded.value)
    nextTick(() => summaryElement.value?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
}
watch(unsettled, (value) => {
  if (!value && returnFocus)
    void nextTick(() => {
      if (document.activeElement === document.body) restoreDialogFocus()
      else returnFocus = null
    })
})
watch(
  () => [
    cartSignature(cart.items),
    JSON.stringify(cart.cart.invalidItems.map((i) => [i.id, i.selected])),
    cart.busy,
    editing.size,
    addressId.value,
    couponId.value,
    initialLoading.value,
    addresses.value.length,
  ],
  scheduleCalculation,
)
watch(
  () => auth.authenticated,
  () => {
    addressSequence++
    calculation++
    clearTimeout(calculationTimer)
    addresses.value = []
    addressId.value = ''
    addressLoading.value = false
    coupons.value = []
    couponId.value = ''
    eligibleIds.value = new Set()
    quote.value = null
    confirmedQuote.value = null
    message.value = ''
    calculating.value = false
    dialog.value?.close()
    void load()
  },
  { immediate: true },
)
onUnmounted(() => {
  alive = false
  calculation++
  clearTimeout(calculationTimer)
})
</script>

<template>
  <div class="cart-page">
    <ShopHeader compact />
    <header class="cart-heading">
      <div>
        <p class="eyebrow">SHOPPING BAG</p>
        <h1 ref="pageHeading" tabindex="-1">
          购物车
          <span v-if="!initialLoading && !pageError && (auth.authenticated || demoMode)"
            >{{ kinds }} 种商品，共 {{ cart.count }} 件</span
          >
        </h1>
      </div>
      <RouterLink to="/catalog" class="continue-shopping"
        >继续购物 <ArrowRight :size="16" aria-hidden="true"
      /></RouterLink>
    </header>
    <div v-if="!demoMode && !auth.authenticated" class="cart-state">
      <ShoppingBag :size="42" aria-hidden="true" />
      <h2>登录后查看购物车</h2>
      <p>登录你的账号，继续挑选心仪好物。</p>
      <RouterLink class="primary-button" to="/login?redirect=/cart">去登录</RouterLink>
    </div>
    <div v-else-if="initialLoading" class="cart-skeleton" role="status" aria-label="正在加载购物车">
      <div v-for="n in 3" :key="n"></div>
      <span>正在加载购物车…</span>
    </div>
    <div v-else-if="pageError" class="cart-state" role="alert">
      <ShoppingBag :size="42" aria-hidden="true" />
      <h2>暂时无法加载购物车</h2>
      <p>{{ pageError }}</p>
      <button class="primary-button" @click="load">重新加载</button>
    </div>
    <template v-else>
      <p v-if="demoMode" class="cart-notice">
        当前为演示模式，购物车仅保留在本次会话中，无法创建真实订单。
      </p>
      <p v-if="message" class="cart-notice success" role="status">
        {{ message }}
        <RouterLink v-if="message.includes('订单')" to="/orders">查看我的订单</RouterLink>
      </p>
      <div v-if="actionError || cart.loadError" class="cart-notice error" role="alert">
        {{ actionError || cart.loadError }}
        <button v-if="cart.loadError" class="text-button" @click="load">重新加载</button>
      </div>
      <div v-if="!allItems.length" class="cart-state">
        <ShoppingBag :size="42" aria-hidden="true" />
        <h2>购物车还是空的</h2>
        <p>去看看商城里的商品，把喜欢的带回来。</p>
        <RouterLink class="primary-button" to="/catalog">去逛逛</RouterLink>
      </div>
      <div v-else class="cart-layout">
        <section class="cart-main" aria-label="购物车商品">
          <section class="cart-list" aria-label="可购买商品">
            <div class="cart-toolbar">
              <label class="check-label"
                ><input
                  type="checkbox"
                  :checked="selected.all"
                  :indeterminate="selected.mixed"
                  :aria-checked="selected.mixed ? 'mixed' : selected.all"
                  :disabled="locked || cart.busy || !!editing.size || !availableItems.length"
                  @change="selectionAction(null, ($event.target as HTMLInputElement).checked)"
                />全选有效商品</label
              >
              <span class="selection-count"
                >已选 {{ selected.kinds }} 种 / {{ selected.quantity }} 件</span
              >
              <button
                class="text-button delete-selected"
                :disabled="locked || unsettled || !selected.kinds"
                @click="confirmDelete(availableItems.filter((i) => i.selected))"
              >
                <Trash2 :size="15" aria-hidden="true" />删除所选
              </button>
            </div>
            <div v-if="availableItems.length" class="cart-columns" aria-hidden="true">
              <span class="column-product">商品信息</span><span>单价</span><span>数量</span
              ><span>小计</span><span>操作</span>
            </div>
            <p v-else class="no-valid">暂无可购买商品，下方商品已失效。</p>
            <article
              v-for="item in availableItems"
              :key="item.id"
              class="cart-product-row"
              :data-item-id="item.id"
              :aria-label="item.productName"
            >
              <label class="row-check"
                ><input
                  type="checkbox"
                  :aria-label="`选择${item.productName}`"
                  :checked="item.selected"
                  :disabled="locked || cart.busy || !!editing.size"
                  @change="selectionAction(item, ($event.target as HTMLInputElement).checked)"
              /></label>
              <RouterLink
                :to="`/products/${item.productId}`"
                class="product-photo"
                :aria-label="`查看${item.productName}`"
                ><ProductImage :src="item.coverUrl" :alt="item.productName" kind="gas" placeholder
              /></RouterLink>
              <div class="product-info">
                <RouterLink :to="`/products/${item.productId}`" class="product-name">{{
                  item.productName
                }}</RouterLink>
                <p class="product-spec">规格：默认</p>
                <p v-if="item.priceChanged" class="price-change">价格有变动，请核对当前单价</p>
              </div>
              <div class="row-price">
                <span class="mobile-label">单价 </span>{{ formatPrice(item.unitPrice) }}
              </div>
              <div class="row-purchase">
                <CartQuantity
                  class="row-quantity"
                  :item="item"
                  :disabled="locked"
                  @pending="pendingEdit"
                />
                <div class="row-subtotal">
                  <span class="mobile-label">小计 </span>{{ formatPrice(item.subtotal) }}
                </div>
              </div>
              <div class="row-actions">
                <button
                  v-if="!demoMode"
                  class="icon-button favorite-button"
                  :aria-label="`收藏${item.productName}`"
                  :disabled="locked || favorites.has(item.id)"
                  @click="favorite(item)"
                >
                  <Heart :size="17" aria-hidden="true" /></button
                ><button
                  class="icon-button"
                  :aria-label="`删除${item.productName}`"
                  :disabled="locked || unsettled"
                  @click="confirmDelete([item])"
                >
                  <Trash2 :size="17" aria-hidden="true" />
                </button>
              </div>
            </article>
          </section>
          <section v-if="invalidItems.length" class="invalid-list" aria-labelledby="invalid-title">
            <header>
              <h2 id="invalid-title">
                失效商品 <span>{{ invalidItems.length }} 种</span>
              </h2>
              <button
                class="text-button"
                :disabled="locked || unsettled"
                @click="confirmDelete(invalidItems)"
              >
                清理失效商品
              </button>
            </header>
            <article
              v-for="item in invalidItems"
              :key="item.id"
              class="invalid-row"
              :data-invalid-id="item.id"
            >
              <RouterLink
                :to="`/products/${item.productId}`"
                class="product-photo"
                :aria-label="`查看${item.productName}`"
                ><ProductImage :src="item.coverUrl" :alt="item.productName" kind="gas" placeholder
              /></RouterLink>
              <div class="product-info">
                <RouterLink :to="`/products/${item.productId}`" class="product-name">{{
                  item.productName
                }}</RouterLink>
                <p class="product-spec">规格：默认 · {{ item.quantity }} 件</p>
                <span class="invalid-badge">{{
                  item.degraded
                    ? '商品信息暂不可用'
                    : item.availableStock < item.quantity
                      ? '库存不足'
                      : '商品已下架或不可购买'
                }}</span>
              </div>
              <button
                class="icon-button"
                :aria-label="`删除失效商品${item.productName}`"
                :disabled="locked || unsettled"
                @click="confirmDelete([item])"
              >
                <Trash2 :size="17" aria-hidden="true" />
              </button>
            </article>
          </section>
          <p class="cart-footnote">购物车不锁定库存，商品价格、库存和优惠以结算时确认为准。</p>
        </section>
        <aside
          ref="summaryElement"
          class="cart-summary"
          :class="{ expanded: summaryExpanded }"
          aria-label="订单汇总"
          :aria-busy="unsettled"
        >
          <div class="summary-heading">
            <h2>订单汇总</h2>
            <span>{{ selected.kinds }} 种 / {{ selected.quantity }} 件</span
            ><button
              class="icon-button summary-toggle"
              :aria-expanded="summaryExpanded"
              aria-label="展开或收起金额明细"
              @click="summaryExpanded = !summaryExpanded"
            >
              <ChevronDown :size="18" aria-hidden="true" />
            </button>
          </div>
          <div class="summary-content">
            <div class="address-section">
              <label for="cart-address"><MapPin :size="16" aria-hidden="true" />配送至</label
              ><select
                id="cart-address"
                ref="addressInput"
                v-model="addressId"
                :disabled="locked || addressLoading || demoMode"
              >
                <option value="">{{ addressLoading ? '正在加载地址…' : '请选择收货地址' }}</option>
                <option v-for="address in addresses" :key="address.id" :value="address.id">
                  {{ address.receiver }} ·
                  {{
                    address.fullAddress ||
                    `${address.province}${address.city}${address.district}${address.detail}`
                  }}
                </option>
              </select>
              <p v-if="addressError" class="inline-error">
                {{ addressError }} <button class="text-button" @click="loadAddresses">重试</button>
              </p>
              <RouterLink v-if="!demoMode" to="/addresses" class="address-link"
                >{{ addresses.length ? '管理收货地址' : '添加收货地址' }}
                <ArrowRight :size="13" aria-hidden="true"
              /></RouterLink>
            </div>
            <details class="coupon-section">
              <summary>
                优惠券
                <span
                  >{{ couponId ? '已选择 1 张' : '选择优惠' }}
                  <ChevronDown :size="14" aria-hidden="true"
                /></span>
              </summary>
              <div class="coupon-options">
                <p>订单通用券，每笔订单最多使用一张。适用优惠由系统重新确认。</p>
                <label class="coupon-option"
                  ><input
                    v-model="couponId"
                    type="radio"
                    name="cart-coupon"
                    value=""
                    :disabled="locked || unsettled"
                  />不使用优惠券</label
                ><label
                  v-for="coupon in coupons"
                  :key="coupon.id"
                  class="coupon-option"
                  :class="{ unavailable: !!reason(coupon) }"
                  ><input
                    v-model="couponId"
                    type="radio"
                    name="cart-coupon"
                    :value="coupon.id"
                    :disabled="locked || unsettled || !!reason(coupon)"
                  /><span
                    ><strong>{{ coupon.name }} · 减 {{ formatPrice(coupon.amount) }}</strong
                    ><small
                      >满 {{ formatPrice(coupon.minimumAmount) }} 可用 ·
                      {{ coupon.expireTime.slice(0, 10) }} 到期</small
                    ><small v-if="reason(coupon)" class="inline-error">{{
                      reason(coupon)
                    }}</small></span
                  ></label
                >
                <p v-if="!coupons.length">
                  {{ selected.quantity ? '暂无可用优惠券' : '选择商品后查看优惠券' }}
                </p>
                <RouterLink v-if="!demoMode" to="/coupons" class="address-link"
                  >前往领券中心 <ArrowRight :size="13" aria-hidden="true"
                /></RouterLink>
              </div>
            </details>
            <dl class="amount-details">
              <div>
                <dt>商品金额</dt>
                <dd>{{ formatPrice(quote?.totalAmount ?? cart.cart.selectedAmount) }}</dd>
              </div>
              <div>
                <dt>优惠金额</dt>
                <dd>
                  {{
                    quote
                      ? `−${formatPrice(quote.discountAmount)}`
                      : selected.quantity
                        ? '结算时确认'
                        : '—'
                  }}
                </dd>
              </div>
              <div v-if="quote?.fullReductionAmount" class="discount-detail">
                <dt>满减优惠</dt>
                <dd>−{{ formatPrice(quote.fullReductionAmount) }}</dd>
              </div>
              <div v-if="quote?.couponDiscountAmount" class="discount-detail">
                <dt>优惠券抵扣</dt>
                <dd>−{{ formatPrice(quote.couponDiscountAmount) }}</dd>
              </div>
              <div class="freight-detail">
                <dt>运费</dt>
                <dd>{{ quote ? formatPrice(quote.freightAmount) : '选择收货地址后计算' }}</dd>
              </div>
            </dl>
          </div>
          <div class="summary-total">
            <span>预计合计</span
            ><strong data-testid="cart-total">{{
              unsettled ? '更新中…' : formatPrice(amount)
            }}</strong>
          </div>
          <p class="summary-hint" role="status">
            {{
              unsettled
                ? '正在同步购物车与优惠，请稍候'
                : !selected.quantity
                  ? '请选择有效商品后结算'
                  : !quote
                    ? '待确认运费及优惠，最终金额以结算报价为准'
                    : '已按当前地址、商品与优惠重新计算'
            }}
          </p>
          <div v-if="quoteError || couponError" class="inline-error quote-error" role="alert">
            {{ quoteError || couponError }}
            <button class="text-button" :disabled="unsettled" @click="scheduleCalculation">
              重新计算
            </button>
            <button class="text-button refresh-cart" :disabled="unsettled" @click="load">
              刷新购物车
            </button>
          </div>
          <button class="primary-button checkout-button" :disabled="!canCheckout" @click="checkout">
            {{ checkoutBusy ? '正在核对…' : '去结算' }} <ArrowRight :size="17" aria-hidden="true" />
          </button>
          <p v-if="demoMode" class="summary-hint">演示模式暂不支持结算</p>
        </aside>
      </div>
      <div v-if="allItems.length" class="cart-mobile-bar" aria-label="快捷结算">
        <label class="check-label"
          ><input
            type="checkbox"
            :checked="selected.all"
            :indeterminate="selected.mixed"
            :aria-checked="selected.mixed ? 'mixed' : selected.all"
            :disabled="locked || cart.busy || !!editing.size || !availableItems.length"
            @change="selectionAction(null, ($event.target as HTMLInputElement).checked)"
          />全选</label
        ><button class="mobile-total" :aria-expanded="summaryExpanded" @click="showSummary">
          <span
            >合计 <strong>{{ unsettled ? '更新中…' : formatPrice(amount) }}</strong></span
          ><small
            >{{ selected.quantity }} 件 · 明细 <ChevronDown :size="12" aria-hidden="true"
          /></small></button
        ><button class="primary-button" :disabled="!canCheckout" @click="checkout">
          {{ checkoutBusy ? '核对中…' : '去结算' }}
        </button>
      </div>
    </template>
    <dialog
      ref="dialog"
      class="cart-dialog"
      aria-labelledby="cart-dialog-title"
      @close="dialogClosed"
      @cancel="submitting ? $event.preventDefault() : undefined"
      @click="$event.target === dialog && !submitting && closeDialog()"
    >
      <div class="dialog-heading">
        <h2 id="cart-dialog-title">
          {{ dialogMode === 'delete' ? '确认删除商品' : '确认结算信息' }}
        </h2>
        <button
          class="icon-button"
          aria-label="关闭弹窗"
          :disabled="submitting"
          @click="closeDialog"
        >
          <X :size="20" aria-hidden="true" />
        </button>
      </div>
      <template v-if="dialogMode === 'delete'"
        ><p>
          将从购物车中移除 {{ deleteItems.length }} 种商品，共
          {{ deleteItems.reduce((sum, i) => sum + i.quantity, 0) }} 件。
        </p>
        <ul class="delete-preview">
          <li v-for="item in deleteItems.slice(0, 4)" :key="item.id">
            {{ item.productName }} × {{ item.quantity }}
          </li>
          <li v-if="deleteItems.length > 4">另有 {{ deleteItems.length - 4 }} 种商品</li>
        </ul>
        <p class="muted">删除后如需购买，请重新加入购物车。</p></template
      >
      <template v-else-if="confirmedQuote"
        ><p>已重新核对 {{ selected.kinds }} 种商品，共 {{ selected.quantity }} 件。</p>
        <p class="confirm-address">
          {{ addresses.find((a) => a.id === addressId)?.receiver }} ·
          {{
            addresses.find((a) => a.id === addressId)?.fullAddress ||
            [
              addresses.find((a) => a.id === addressId)?.province,
              addresses.find((a) => a.id === addressId)?.city,
              addresses.find((a) => a.id === addressId)?.district,
              addresses.find((a) => a.id === addressId)?.detail,
            ].join('')
          }}
        </p>
        <dl class="amount-details">
          <div>
            <dt>商品金额</dt>
            <dd>{{ formatPrice(confirmedQuote.totalAmount) }}</dd>
          </div>
          <div>
            <dt>优惠</dt>
            <dd>−{{ formatPrice(confirmedQuote.discountAmount) }}</dd>
          </div>
          <div>
            <dt>运费</dt>
            <dd>{{ formatPrice(confirmedQuote.freightAmount) }}</dd>
          </div>
          <div class="confirm-pay">
            <dt>应付合计</dt>
            <dd>{{ formatPrice(confirmedQuote.payAmount) }}</dd>
          </div>
        </dl>
        <p class="muted">确认后将创建真实订单，支付请前往“我的订单”。</p></template
      >
      <p v-if="dialogError" class="inline-error" role="alert">{{ dialogError }}</p>
      <div class="dialog-actions">
        <button class="secondary-button" autofocus :disabled="submitting" @click="closeDialog">
          返回购物车</button
        ><button
          class="primary-button"
          :disabled="submitting"
          @click="dialogMode === 'delete' ? deleteConfirmed() : submitOrder()"
        >
          {{ submitting ? '正在提交…' : dialogMode === 'delete' ? '确认删除' : '确认并创建订单' }}
        </button>
      </div>
    </dialog>
  </div>
</template>

<style scoped>
.cart-page {
  max-width: 1280px;
  margin: 0 auto;
  color: #292929;
}
.cart-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 28px 0 24px;
}
.eyebrow {
  font-size: 10px;
  letter-spacing: 2px;
  color: #8a776f;
  margin: 0 0 8px;
}
.cart-heading h1 {
  font-size: 28px;
  font-weight: 650;
  margin: 0;
  letter-spacing: -0.5px;
}
.cart-heading h1 span {
  margin-left: 14px;
  font-size: 13px;
  font-weight: 400;
  color: #737373;
  letter-spacing: 0;
}
.continue-shopping,
.text-button,
.address-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.continue-shopping {
  color: #666;
  min-height: 44px;
  white-space: nowrap;
}
.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 312px;
  align-items: start;
  gap: 24px;
}
.cart-main {
  min-width: 0;
}
.cart-list,
.invalid-list,
.cart-summary {
  background: white;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
}
.cart-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid #eee;
}
.check-label {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  min-height: 32px;
  cursor: pointer;
}
input[type='checkbox'],
input[type='radio'] {
  width: 17px;
  height: 17px;
  margin: 0;
  accent-color: #a53e2b;
  flex-shrink: 0;
  cursor: pointer;
}
input:disabled {
  cursor: not-allowed;
}
.selection-count {
  color: #777;
  font-size: 12px;
}
.delete-selected {
  margin-left: auto;
}
.text-button {
  border: 0;
  padding: 7px 0;
  background: none;
  color: #8a3e30;
}
.text-button:disabled {
  color: #aaa;
}
.cart-columns,
.cart-product-row {
  display: grid;
  grid-template-columns: 20px 96px minmax(0, 1fr) 80px 116px 96px 36px;
  gap: 12px;
  align-items: start;
  padding: 22px 18px;
}
.cart-columns {
  padding-top: 12px;
  padding-bottom: 12px;
  font-size: 11px;
  color: #888;
  background: #fcfcfc;
}
.column-product {
  grid-column: 1 / 4;
  padding-left: 32px;
}
.cart-product-row + .cart-product-row {
  border-top: 1px solid #eee;
}
.row-check {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
}
.product-photo {
  width: 96px;
  height: 96px;
  display: block;
  border: 1px solid #f0f0f0;
  border-radius: 7px;
  overflow: hidden;
  background: #fafafa;
}
.product-photo :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.product-photo :deep(.image-placeholder) {
  min-height: 0;
  padding: 6px;
  gap: 4px;
  text-align: center;
  font-size: 10px;
  overflow-wrap: anywhere;
}
.product-photo :deep(.image-placeholder svg) {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
}
.product-info {
  min-width: 0;
  padding: 5px 0 0;
}
.product-info p {
  min-height: 0;
}
.row-purchase {
  display: contents;
}
.product-name {
  font-size: 13px;
  line-height: 1.7;
  font-weight: 550;
  display: block;
  overflow-wrap: anywhere;
}
.product-name:hover {
  color: #a53e2b;
}
.product-spec {
  color: #888;
  font-size: 11px;
  margin: 10px 0 0;
  line-height: 1.6;
}
.price-change {
  color: #9c3f2d;
  font-size: 11px;
  line-height: 1.6;
  margin: 7px 0 0;
}
.row-price,
.row-subtotal {
  padding-top: 12px;
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.row-subtotal {
  font-weight: 650;
  color: #a53e2b;
}
.row-quantity {
  padding-top: 3px;
}
.row-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: 0;
  color: #777;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
}
.icon-button:hover {
  color: #a53e2b;
  background: #fbf5f2;
  border-radius: 5px;
}
.icon-button:disabled {
  color: #bbb;
  background: none;
}
.mobile-label {
  display: none;
}
.invalid-list {
  margin-top: 20px;
}
.invalid-list > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #eee;
  padding: 14px 18px;
}
.invalid-list h2 {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
}
.invalid-list h2 span {
  color: #888;
  font-size: 11px;
  font-weight: 400;
  margin-left: 5px;
}
.invalid-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 36px;
  gap: 16px;
  padding: 18px;
  align-items: center;
}
.invalid-row + .invalid-row {
  border-top: 1px solid #eee;
}
.invalid-row .product-photo {
  width: 72px;
  height: 72px;
  filter: grayscale(1);
  opacity: 0.65;
}
.invalid-row .product-name {
  color: #777;
}
.invalid-badge {
  display: inline-block;
  margin-top: 7px;
  background: #f3f3f3;
  border: 1px solid #e8e8e8;
  padding: 3px 7px;
  border-radius: 3px;
  font-size: 11px;
  color: #777;
}
.no-valid {
  padding: 28px 18px;
  color: #888;
  font-size: 13px;
}
.cart-footnote {
  font-size: 11px;
  line-height: 1.7;
  color: #888;
  padding: 0 2px;
  margin-top: 18px;
}
.cart-summary {
  position: sticky;
  top: 24px;
  padding: 22px;
}
.refresh-cart {
  margin-left: 10px;
}
@media (max-height: 780px) {
  .cart-summary {
    position: static;
  }
}
.summary-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 20px;
  border-bottom: 1px solid #eee;
}
.summary-heading h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
}
.summary-heading > span {
  font-size: 11px;
  color: #888;
}
.summary-toggle {
  display: none;
}
.address-section {
  padding: 18px 0;
  border-bottom: 1px solid #eee;
}
.address-section > label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #555;
  margin-bottom: 10px;
}
.address-section select {
  display: block;
  width: 100%;
  min-height: 42px;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 5px;
  font-size: 12px;
  background: #fff;
  color: #555;
  text-overflow: ellipsis;
}
.address-link {
  font-size: 11px;
  color: #94513f;
  margin-top: 8px;
  min-height: 26px;
}
.coupon-section {
  border-bottom: 1px solid #eee;
}
.coupon-section summary {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
  font-size: 12px;
  min-height: 52px;
  align-items: center;
  list-style: none;
}
.coupon-section summary::-webkit-details-marker {
  display: none;
}
.coupon-section summary > span {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #a53e2b;
}
.coupon-section[open] summary svg {
  transform: rotate(180deg);
}
.coupon-options {
  max-height: 320px;
  overflow-y: auto;
  padding: 0 2px 12px;
}
.coupon-options > p {
  font-size: 11px;
  color: #777;
  line-height: 1.7;
  margin: 0 0 12px;
}
.coupon-option {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 12px 0;
  font-size: 12px;
  cursor: pointer;
  border-bottom: 1px dashed #e8e8e8;
  overflow-wrap: anywhere;
}
.coupon-option input {
  margin-top: 2px;
}
.coupon-option strong {
  font-size: 12px;
  font-weight: 550;
  line-height: 1.6;
}
.coupon-option small {
  display: block;
  margin-top: 5px;
  color: #888;
  font-size: 10px;
  line-height: 1.7;
}
.coupon-option.unavailable {
  color: #888;
}
.amount-details {
  font-size: 12px;
  margin: 20px 0;
}
.amount-details > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin: 13px 0;
}
.amount-details dt {
  color: #777;
  flex-shrink: 0;
}
.amount-details dd {
  margin: 0;
  text-align: right;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.discount-detail {
  font-size: 11px;
  padding-left: 9px;
}
.discount-detail dd {
  color: #a53e2b;
}
.freight-detail dd {
  font-size: 11px;
}
.summary-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  border-top: 1px solid #eee;
  padding-top: 20px;
  font-size: 13px;
}
.summary-total strong {
  font-size: 26px;
  letter-spacing: -0.5px;
  font-weight: 650;
  color: #a53e2b;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
  text-align: right;
}
.summary-hint {
  color: #888;
  font-size: 10px;
  line-height: 1.7;
  margin: 10px 0 18px;
}
.primary-button,
.secondary-button {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  border-radius: 6px;
  min-height: 46px;
  padding: 10px 20px;
  font-weight: 550;
  font-size: 13px;
  line-height: 1.5;
  border: 1px solid transparent;
}
.primary-button {
  background: #a53e2b;
  color: white;
}
.primary-button:hover:not(:disabled) {
  background: #883323;
}
.primary-button:disabled {
  background: #e8e0de;
  color: #8e8582;
}
.secondary-button {
  background: #fff;
  color: #666;
  border-color: #ddd;
}
.checkout-button {
  width: 100%;
}
.cart-notice {
  display: block;
  padding: 13px 16px;
  border: 1px solid #e5d4ca;
  background: #fff9f4;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
  margin: 0 0 18px;
  overflow-wrap: anywhere;
}
.cart-notice.success {
  background: #f2f8f4;
  border-color: #d7e5dc;
  color: #3e654c;
}
.cart-notice a {
  text-decoration: underline;
  margin-left: 8px;
}
.cart-notice.error,
.inline-error {
  color: #a13223;
}
.inline-error {
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.cart-state {
  min-height: 370px;
  padding: 60px 20px;
  background: white;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.cart-state > svg {
  color: #bba9a1;
}
.cart-state h2 {
  font-size: 21px;
  margin: 20px 0 0;
  font-weight: 550;
}
.cart-state p {
  margin: 12px 0 26px;
  color: #888;
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.cart-skeleton > div {
  height: 140px;
  margin: 12px 0;
  border-radius: 8px;
  background: linear-gradient(100deg, #eee 30%, #fff 50%, #eee 70%);
  background-size: 200% 100%;
  animation: cart-loading 1.5s infinite;
}
.cart-skeleton > span {
  font-size: 13px;
  color: #888;
}
@keyframes cart-loading {
  to {
    background-position: -200% 0;
  }
}
.cart-mobile-bar {
  display: none;
}
.cart-dialog {
  border: 1px solid #e5e5e5;
  border-radius: 12px;
  padding: 24px;
  width: min(480px, calc(100% - 32px));
  max-height: 90dvh;
  overflow-y: auto;
  color: #333;
  box-shadow: 0 20px 80px #0002;
}
.cart-dialog::backdrop {
  background: #211a1766;
}
.dialog-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.dialog-heading h2 {
  margin: 0;
  font-size: 20px;
}
.cart-dialog p {
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.delete-preview {
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 14px 14px 14px 30px;
  font-size: 12px;
  line-height: 1.9;
  overflow-wrap: anywhere;
}
.muted {
  color: #888;
  font-size: 12px;
}
.confirm-address {
  color: #777;
  padding: 12px;
  background: #fafafa;
  border-radius: 5px;
}
.confirm-pay {
  border-top: 1px solid #eee;
  padding-top: 15px;
  font-size: 18px;
  color: #a53e2b;
  font-weight: 600;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}
@media (max-width: 1250px) and (min-width: 1021px) {
  .cart-columns,
  .cart-product-row {
    grid-template-columns: 20px 80px minmax(0, 1fr) 72px 116px 85px;
    gap: 10px;
    padding-left: 14px;
    padding-right: 14px;
  }
  .cart-columns > span:last-child {
    display: none;
  }
  .cart-product-row .product-photo {
    width: 80px;
    height: 80px;
  }
  .row-actions {
    grid-column: 3 / 7;
    justify-self: end;
    flex-direction: row;
  }
}
@media (max-width: 1020px) {
  .cart-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .cart-summary {
    position: static;
  }
  .cart-product-row,
  .cart-columns {
    grid-template-columns: 20px 88px minmax(0, 1fr) 80px 116px 96px 36px;
    gap: 10px;
  }
  .cart-product-row .product-photo {
    width: 88px;
    height: 88px;
  }
  .summary-heading {
    justify-content: flex-start;
    gap: 16px;
  }
  .summary-total strong {
    font-size: 28px;
  }
}
@media (max-width: 800px) and (min-width: 621px) {
  .cart-product-row,
  .cart-columns {
    grid-template-columns: 20px 80px minmax(0, 1fr) 70px 116px 85px;
    gap: 10px;
    padding-left: 14px;
    padding-right: 14px;
  }
  .cart-product-row .product-photo {
    width: 80px;
    height: 80px;
  }
  .cart-columns > span:last-child {
    display: none;
  }
  .row-actions {
    grid-column: 3 / 7;
    flex-direction: row;
    justify-self: end;
  }
}
@media (max-width: 620px) {
  .cart-page {
    padding-bottom: 40px;
  }
  .cart-heading {
    margin: 22px 0 18px;
    align-items: flex-end;
    gap: 8px;
  }
  .eyebrow {
    font-size: 9px;
  }
  .cart-heading h1 {
    font-size: 24px;
  }
  .cart-heading h1 span {
    display: block;
    margin: 8px 0 0;
    font-size: 11px;
  }
  .continue-shopping {
    font-size: 11px;
  }
  .cart-toolbar {
    padding: 10px 14px;
    gap: 6px;
  }
  .cart-toolbar .selection-count {
    width: 100%;
    order: 3;
    padding-left: 26px;
    font-size: 11px;
  }
  .check-label {
    min-height: 44px;
    font-size: 12px;
  }
  .cart-toolbar .text-button {
    min-height: 44px;
    font-size: 12px;
  }
  .cart-columns {
    display: none;
  }
  .cart-product-row {
    grid-template-columns: 20px 88px minmax(0, 1fr);
    gap: 10px;
    padding: 18px 14px;
  }
  .row-check {
    grid-row: 1 / 3;
    align-self: start;
  }
  .product-photo {
    grid-column: 2;
    grid-row: 1 / 3;
  }
  .product-info {
    grid-column: 3;
    padding-top: 0;
  }
  .product-name {
    font-size: 13px;
    line-height: 1.7;
  }
  .product-spec {
    margin-top: 5px;
  }
  .row-price {
    grid-column: 3;
    grid-row: 2;
    padding-top: 0;
    font-size: 13px;
  }
  .row-quantity {
    padding-top: 4px;
    text-align: left;
  }
  .row-purchase {
    display: flex;
    grid-column: 2 / 4;
    grid-row: 3;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px 12px;
    min-width: 0;
  }
  .row-subtotal {
    flex: 1;
    min-width: 90px;
    text-align: right;
    padding-top: 0;
    font-size: 15px;
  }
  .mobile-label {
    display: inline;
    color: #888;
    font-size: 11px;
    font-weight: 400;
    margin-right: 5px;
  }
  .row-price .mobile-label {
    display: none;
  }
  .row-actions {
    grid-column: 2 / 4;
    grid-row: 4;
    flex-direction: row;
    justify-self: end;
  }
  .icon-button {
    width: 44px;
    height: 44px;
  }
  .row-actions .icon-button {
    height: 44px;
  }
  .cart-product-row {
    row-gap: 8px;
  }
  .invalid-list > header {
    padding: 10px 14px;
  }
  .invalid-list .text-button {
    min-height: 44px;
    font-size: 11px;
  }
  .invalid-row {
    grid-template-columns: 64px minmax(0, 1fr) 36px;
    gap: 10px;
    padding: 14px;
  }
  .invalid-row .product-photo {
    grid-column: 1;
    grid-row: 1;
    width: 64px;
    height: 64px;
  }
  .invalid-row .product-info {
    grid-column: 2;
  }
  .invalid-row .icon-button {
    width: 36px;
  }
  .cart-summary {
    padding: 18px;
  }
  .summary-heading {
    padding-bottom: 12px;
    gap: 10px;
  }
  .summary-heading h2 {
    font-size: 16px;
  }
  .summary-heading > span {
    margin-left: auto;
  }
  .summary-toggle {
    display: inline-flex;
    width: 30px;
    height: 30px;
  }
  .summary-content {
    display: none;
  }
  .cart-summary.expanded .summary-content {
    display: block;
  }
  .cart-summary.expanded .summary-toggle {
    transform: rotate(180deg);
  }
  .summary-total {
    border: 0;
    padding-top: 16px;
  }
  .summary-total strong {
    font-size: 24px;
  }
  .summary-hint {
    margin-bottom: 4px;
  }
  .cart-summary .checkout-button {
    display: none;
  }
  .address-section select {
    font-size: 14px;
    min-height: 44px;
  }
  .address-link {
    min-height: 36px;
  }
  .coupon-option {
    min-height: 44px;
  }
  .cart-mobile-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    position: fixed;
    z-index: 30;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid #e5e5e5;
    box-shadow: 0 -4px 20px #00000004;
  }
  .cart-mobile-bar .check-label {
    flex-shrink: 0;
    font-size: 11px;
    gap: 6px;
  }
  .cart-mobile-bar .primary-button {
    flex-shrink: 0;
    min-width: 92px;
    padding: 12px;
  }
  .mobile-total {
    border: 0;
    padding: 0;
    background: none;
    text-align: right;
    flex: 1;
    min-width: 0;
    min-height: 44px;
  }
  .mobile-total > span {
    font-size: 11px;
    color: #777;
    display: block;
  }
  .mobile-total strong {
    font-size: 19px;
    color: #a53e2b;
    font-weight: 650;
    overflow-wrap: anywhere;
  }
  .mobile-total small {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 3px;
    color: #888;
    font-size: 10px;
    margin-top: 4px;
  }
  .cart-state {
    min-height: 330px;
    padding: 40px 18px;
  }
  .cart-dialog {
    padding: 18px;
  }
  .dialog-heading h2 {
    font-size: 18px;
  }
  .dialog-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }
  .dialog-actions button {
    padding: 10px 8px;
    font-size: 12px;
  }
}
@media (max-width: 359px) {
  .cart-product-row {
    grid-template-columns: 18px 72px minmax(0, 1fr);
    gap: 8px;
    padding-left: 12px;
    padding-right: 12px;
  }
  .cart-product-row .product-photo {
    width: 72px;
    height: 72px;
  }
  .cart-mobile-bar {
    gap: 8px;
    padding-left: 12px;
    padding-right: 12px;
  }
  .mobile-total strong {
    font-size: 16px;
  }
  .cart-mobile-bar .primary-button {
    min-width: 78px;
    font-size: 12px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .cart-skeleton > div {
    animation: none;
  }
}
</style>
<style>
body:has(.cart-dialog[open]) {
  overflow: hidden;
}
</style>
