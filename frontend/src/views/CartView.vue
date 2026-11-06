<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Minus, Plus, Trash2, Heart, MessageCircle, Store, ShieldCheck } from 'lucide-vue-next'
import { useCartStore } from '../stores/cart'
import { addressApi, orderApi } from '../api/commerce'
import type { CartItem, UserAddress } from '../api/types'
import { demoMode } from '../api/catalog'
import { session } from '../platform/session'
import { couponsApi, type UserCoupon } from '../api/coupons'
const coupons = ref<UserCoupon[]>([]), userCouponId = ref('')
const quote = ref<Awaited<ReturnType<typeof orderApi.preview>> | null>(null)
let couponSequence = 0
const couponsLoading = ref(false)
async function loadCoupons() {
  const version = ++couponSequence
  couponsLoading.value = true
  try {
    const amount = Number(cart.cart.selectedAmount)
    const result: UserCoupon[] = []
    let page = 1, pages = 1
    do {
      const response = await couponsApi.mine(page, 'unused', amount)
      if (version !== couponSequence) return
      result.push(...response.list)
      pages = response.pages
      page++
    } while (page <= pages)
    coupons.value = result
    if (!result.some(c => c.id === userCouponId.value)) userCouponId.value = ''
  } catch (e) { if (version === couponSequence) { coupons.value = []; userCouponId.value = ''; message.value = (e as Error).message } }
  finally { if (version === couponSequence) couponsLoading.value = false }
}
import ProductImage from '../components/ProductImage.vue'
import { mallApi } from '../api/mall'
const cart = useCartStore()
const addresses = ref<UserAddress[]>([]), addressId = ref(''), busy = ref(false), message = ref('')
const selectedAll = computed(() => cart.items.length > 0 && cart.items.every(item => item.selected))
async function checkout() {
  if (!session.read()) { window.location.hash = '/login?redirect=/cart'; return }
  busy.value = true; message.value = ''
  try {
    addresses.value = await addressApi.list()
    if (!addressId.value) addressId.value = addresses.value.find(a => a.isDefault)?.id || addresses.value[0]?.id || ''
    const selected = addresses.value.find(a => a.id === addressId.value)
    if (!selected) { message.value = '请先添加收货地址'; return }
    if (selected.needsRegionCompletion) { message.value = selected.regionMessage || '请先补全收货地址的有效地区编码'; return }
    quote.value = await orderApi.preview(addressId.value, userCouponId.value)
    if (!window.confirm(`商品金额 ¥${quote.value.totalAmount}，优惠 ¥${quote.value.discountAmount}，运费 ¥${quote.value.freightAmount}，应付 ¥${quote.value.payAmount}。确认提交订单？`)) return
    await orderApi.create(addressId.value, userCouponId.value, quote.value); await cart.refresh(1); await loadCoupons(); userCouponId.value = ''; quote.value = null; message.value = '订单已创建'
  } catch (e) { message.value = (e as Error).message; await cart.refresh(1) } finally { busy.value = false }
}
async function changeSelection(item: CartItem, selected: boolean) { try { await cart.select(item.id, selected) } catch (e) { message.value = (e as Error).message } }
async function removeInvalid() { try { await cart.removeMany(cart.cart.invalidItems.map(item => item.id)) } catch (e) { message.value = (e as Error).message } }
async function favorite(item: CartItem) { try { await mallApi.favorite(item.productId, true); message.value = '已移入收藏' } catch (e) { message.value = (e as Error).message } }
async function favoriteSelected() {
  const selected = cart.items.filter(item => item.selected)
  if (!selected.length) return
  try { await Promise.all(selected.map(item => mallApi.favorite(item.productId, true))); message.value = `已将 ${selected.length} 件商品移入收藏` }
  catch (e) { message.value = (e as Error).message }
}
async function removeItem(item: CartItem) { try { await cart.change(item.id, -item.quantity) } catch (e) { message.value = (e as Error).message } }
async function removeSelected() { try { await cart.removeSelected() } catch (e) { message.value = (e as Error).message } }
async function clearCart() { if (!window.confirm('确认清理购物车中的全部商品？')) return; try { await cart.clear() } catch (e) { message.value = (e as Error).message } }
onMounted(() => { if (!demoMode && session.read()) void cart.refresh(1) })
watch(() => cart.cart.selectedAmount, () => { if (!demoMode && session.read()) void loadCoupons() }, { immediate: true })
</script>
<template>
  <div class="cart-page">
  <header class="page-heading cart-heading"><div><span class="eyebrow">YOUR HOME ESSENTIALS</span><h1>购物车 <small>{{ cart.count }} 件商品</small></h1></div><RouterLink to="/catalog" class="continue-shopping">继续购物 <span aria-hidden="true">›</span></RouterLink></header>
  <p v-if="cart.busy" class="state" role="status">正在加载购物车...</p>
  <p v-else-if="!cart.items.length && !cart.cart.invalidItems.length" class="state">购物车还是空的，去看看精选商品吧。<RouterLink to="/catalog" class="button">逛逛商城</RouterLink></p>
  <template v-else>
    <div class="cart-toolbar">
      <div class="toolbar-actions"><label class="select-all"><input type="checkbox" :checked="selectedAll" @change="cart.selectAll(($event.target as HTMLInputElement).checked)" /> 全选</label><button class="toolbar-button" :disabled="!cart.cart.selectedQuantity" @click="removeSelected"><Trash2 :size="15" />删除</button><button class="toolbar-button" :disabled="!cart.cart.selectedQuantity" @click="favoriteSelected"><Heart :size="15" />移入收藏</button><button class="toolbar-button" @click="clearCart"><Trash2 :size="15" />清理购物车</button></div>
      <nav class="cart-tabs" aria-label="商品快捷入口"><RouterLink to="/history">刚看过</RouterLink><RouterLink to="/favorites">刚收藏</RouterLink><RouterLink to="/orders">刚买过</RouterLink></nav>
    </div>
    <label class="coupon-select">订单优惠券 <select v-model="userCouponId" :disabled="busy || couponsLoading"><option value="">{{ couponsLoading ? '正在查询可用优惠券…' : '不使用优惠券' }}</option><option v-for="c in coupons" :key="c.id" :value="c.id">{{ c.name }}（满 {{ c.minimumAmount }} 减 {{ c.amount }}）</option></select></label><RouterLink to="/coupons" class="coupon-more">领取更多优惠券</RouterLink>
    <div class="cart-list"><article v-for="item in cart.items" :key="item.id" class="cart-item">
      <header class="store-heading"><input type="checkbox" :aria-label="`选择${item.productName}`" :checked="item.selected" @change="changeSelection(item, ($event.target as HTMLInputElement).checked)" /><span class="self-tag">自营</span><Store :size="16" /><strong>自贡能发自营店</strong><button class="service-button" aria-label="联系客服" title="联系客服"><MessageCircle :size="17" /></button></header>
      <div class="cart-row">
        <input type="checkbox" class="item-check" :aria-label="`选择${item.productName}`" :checked="item.selected" @change="changeSelection(item, ($event.target as HTMLInputElement).checked)" />
        <div class="product-photo"><ProductImage :src="item.coverUrl" :alt="item.productName" :kind="item.productId" /><span><ShieldCheck :size="12" />7天价保</span></div>
        <div class="cart-description"><h3>{{ item.productName }}</h3><select aria-label="商品规格" disabled><option>商品规格：默认</option></select><select aria-label="增值服务" disabled><option>服务：可选增值服务</option></select><select aria-label="赠品" disabled><option>赠品：暂无赠品</option></select></div>
        <div class="price-column"><strong>¥{{ item.unitPrice.toFixed(2) }}</strong><small>价格以结算为准</small><p v-if="item.priceChanged" class="error-message">价格已变更</p></div>
        <div class="stepper"><button :aria-label="`减少${item.productName}数量`" @click="cart.change(item.id, -1)"><Minus :size="14" /></button><span>{{ item.quantity }}</span><button :aria-label="`增加${item.productName}数量`" :disabled="item.quantity >= item.availableStock" @click="cart.change(item.id, 1)"><Plus :size="14" /></button></div>
        <div class="item-actions"><button @click="favorite(item)">移入收藏</button><button @click="removeItem(item)">删除</button></div>
      </div>
    </article>
      <div v-if="cart.cart.total > cart.items.length" class="pagination"><button class="button secondary" :disabled="cart.cart.page <= 1" @click="cart.refresh(cart.cart.page - 1)">上一页</button><span>{{ cart.cart.page }} / {{ Math.max(1, Math.ceil(cart.cart.total / cart.cart.size)) }}</span><button class="button secondary" :disabled="cart.cart.page * cart.cart.size >= cart.cart.total" @click="cart.refresh(cart.cart.page + 1)">下一页</button></div>
    </div>
    <section v-if="cart.cart.invalidItems.length" class="cart-list invalid-items"><div class="cart-toolbar"><h2>失效商品（{{ cart.cart.invalidItems.length }}）</h2><button class="toolbar-button" @click="removeInvalid"><Trash2 :size="16" /> 删除失效商品</button></div><article v-for="item in cart.cart.invalidItems" :key="item.id" class="cart-row invalid-row"><ProductImage :src="item.coverUrl" :alt="item.productName" :kind="item.productId" /><div class="cart-description"><h3>{{ item.productName || '商品已下架' }}</h3><p>{{ item.availableStock ? `库存不足，剩余 ${item.availableStock}` : '商品下架或库存不足' }}</p><strong>¥{{ item.unitPrice.toFixed(2) }}</strong></div><button class="toolbar-button" @click="cart.change(item.id, -item.quantity)"><Trash2 :size="16" /> 删除</button></article></section>
    <RouterLink to="/addresses" class="button secondary">管理或补全收货地址</RouterLink><div class="cart-summary"><div>已选 {{ cart.cart.selectedQuantity }} 件 <strong>¥{{ Number(cart.cart.selectedAmount).toFixed(2) }}</strong><small>结算前将再次校验库存和价格</small></div><button class="button" :disabled="busy || !cart.cart.selectedQuantity" @click="checkout">{{ busy ? '提交中...' : '提交订单' }}</button></div><p v-if="message" class="inline-notice" role="status">{{ message }}</p>
  </template>
  </div>
</template>

<style scoped>
.cart-page { margin: -34px -42px; padding: 34px 42px; background: #f5f5f5; }
.cart-heading { display: flex; align-items: end; justify-content: space-between; gap: 16px; }
.cart-heading h1 { margin-bottom: 0; }
.cart-heading h1 small { margin-left: 8px; color: #888; font-size: 13px; font-weight: 400; }
.continue-shopping { color: #666; font-size: 13px; }
.cart-toolbar { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 14px; padding: 8px 14px; border-bottom: 1px solid #ededed; background: #fff; }
.toolbar-actions, .cart-tabs { display: flex; align-items: center; gap: 18px; }
.select-all { display: inline-flex; align-items: center; gap: 7px; color: #444; font-size: 13px; }
.toolbar-button { display: inline-flex; align-items: center; gap: 5px; padding: 4px 0; border: 0; color: #555; background: transparent; font-size: 12px; }
.toolbar-button:disabled { cursor: default; opacity: .45; }
.cart-tabs { align-self: stretch; gap: 20px; }
.cart-tabs a { display: grid; align-items: center; border-bottom: 2px solid transparent; color: #777; font-size: 12px; }
.cart-tabs a.router-link-active { border-color: #c92a2a; color: #b82121; }
.coupon-select { display: inline-flex; align-items: center; gap: 10px; margin: 14px 0 8px; color: #555; font-size: 13px; }
.coupon-select select { max-width: 250px; padding: 7px 9px; border: 1px solid #ddd; background: #fff; }
.coupon-more { margin-left: 10px; color: #9b3333; font-size: 12px; }
.cart-list { margin: 10px 0 20px; border-top: 1px solid #ededed; background: transparent; }
.cart-item { margin-bottom: 10px; border-bottom: 1px solid #e9e9e9; background: #fff; }
.store-heading { display: flex; min-height: 43px; align-items: center; gap: 9px; padding: 0 16px; border-bottom: 1px solid #f0f0f0; color: #555; }
.store-heading strong { font-size: 13px; font-weight: 500; }
.self-tag { padding: 2px 5px; color: #fff; background: #c93434; font-size: 10px; }
.service-button { display: grid; width: 28px; height: 28px; margin-left: auto; place-items: center; border: 0; color: #777; background: transparent; }
.cart-row { display: grid; grid-template-columns: 28px 112px minmax(180px, 1fr) 112px 104px 82px; align-items: center; gap: 15px; min-width: 0; padding: 18px 16px; }
.product-photo { position: relative; width: 112px; aspect-ratio: 1; background: #f7f7f7; }
.product-photo :deep(img) { width: 100%; height: 100%; object-fit: contain; }
.product-photo > span { position: absolute; right: 0; bottom: 0; left: 0; display: flex; align-items: center; justify-content: center; gap: 3px; padding: 3px 0; color: #7c5b37; background: #f5e8d3; font-size: 10px; }
.cart-description { display: grid; align-content: center; gap: 7px; min-width: 0; }
.cart-description h3 { display: -webkit-box; overflow: hidden; margin: 0 0 3px; color: #333; font-size: 14px; font-weight: 500; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.cart-description select { width: min(100%, 235px); padding: 5px 8px; border: 1px solid #e4e4e4; border-radius: 0; color: #777; background: #fafafa; font-size: 11px; }
.price-column { display: grid; justify-items: start; gap: 4px; }
.price-column strong { color: #c62828; font-size: 17px; font-weight: 700; }
.price-column del, .price-column small { color: #999; font-size: 11px; }
.price-column p { margin: 0; }
.stepper { display: grid; grid-template-columns: 28px 34px 28px; height: 28px; align-items: stretch; border: 1px solid #ddd; }
.stepper button { display: grid; place-items: center; padding: 0; border: 0; color: #555; background: #fafafa; }
.stepper button:disabled { color: #bbb; }
.stepper span { display: grid; place-items: center; border-right: 1px solid #ddd; border-left: 1px solid #ddd; color: #444; font-size: 12px; }
.item-actions { display: grid; justify-items: start; gap: 9px; }
.item-actions button { padding: 0; border: 0; color: #666; background: none; font-size: 12px; }
.item-actions button:hover, .toolbar-button:hover { color: #b92c2c; }
.invalid-items { padding-top: 0; border: 1px solid #e9e9e9; background: #fff; }
.invalid-items .cart-toolbar { justify-content: flex-start; }
.invalid-items .cart-toolbar h2 { margin: 0; font-size: 14px; }
.invalid-row { grid-template-columns: 112px minmax(180px, 1fr) auto; }
.invalid-row > :deep(img) { width: 112px; aspect-ratio: 1; object-fit: contain; background: #f7f7f7; }
.cart-summary { display: flex; justify-content: flex-end; align-items: center; gap: 24px; padding: 16px 20px; border: 1px solid #e9e9e9; background: #fff; }
.cart-summary strong { margin-left: 12px; color: #bf2828; font-size: 20px; }
.cart-summary small { display: block; margin-top: 5px; color: #888; font-size: 11px; }
@media (max-width: 920px) {
  .cart-row { grid-template-columns: 22px 90px minmax(140px, 1fr) 92px 90px; gap: 10px; }
  .product-photo { width: 90px; }
  .cart-row .item-actions { grid-column: 3 / -1; display: flex; justify-content: flex-end; }
  .invalid-row { grid-template-columns: 90px minmax(140px, 1fr) auto; }
  .invalid-row > :deep(img) { width: 90px; }
}
@media (max-width: 620px) {
  .cart-page { margin: -20px -16px; padding: 20px 16px 86px; }
  .cart-heading { align-items: flex-start; }
  .cart-heading h1 { font-size: 23px; }
  .cart-toolbar { align-items: flex-start; flex-direction: column; }
  .toolbar-actions { flex-wrap: wrap; gap: 12px; }
  .cart-tabs { width: 100%; min-height: 35px; justify-content: space-around; }
  .cart-tabs a { min-height: 34px; }
  .cart-row { grid-template-columns: 20px 78px minmax(0, 1fr); align-items: start; gap: 10px; padding: 14px 10px; }
  .product-photo { width: 78px; }
  .cart-description { grid-column: 3; grid-row: 1; }
  .cart-description h3 { font-size: 13px; }
  .price-column { grid-column: 2 / 3; grid-row: 2; }
  .stepper { grid-column: 3; grid-row: 2; justify-self: end; }
  .item-actions { grid-column: 3; grid-row: 3; display: flex; justify-content: flex-end; }
  .store-heading { padding: 0 10px; }
  .invalid-row { grid-template-columns: 78px minmax(0, 1fr); }
  .invalid-row > :deep(img) { width: 78px; }
  .invalid-row > .toolbar-button { grid-column: 2; justify-self: end; }
  .coupon-select { flex-wrap: wrap; }
  .cart-summary { justify-content: space-between; gap: 10px; padding: 13px 10px; }
  .cart-summary strong { font-size: 17px; }
}
</style>
