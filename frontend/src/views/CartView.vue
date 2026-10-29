<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ShoppingBag, Minus, Plus, Trash2 } from 'lucide-vue-next'
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
import ApplianceArt from '../components/ApplianceArt.vue'
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
onMounted(() => { if (!demoMode && session.read()) void cart.refresh(1) })
watch(() => cart.cart.selectedAmount, () => { if (!demoMode && session.read()) void loadCoupons() }, { immediate: true })
</script>
<template>
  <header class="page-heading"><span class="eyebrow">YOUR HOME ESSENTIALS</span><h1>购物车 <small>{{ cart.count }} 件商品</small></h1><p>商品价格和库存以提交订单时的服务端校验为准。</p></header>
  <p v-if="cart.busy" class="state" role="status">正在加载购物车...</p>
  <p v-else-if="!cart.items.length && !cart.cart.invalidItems.length" class="state">购物车还是空的，去看看精选商品吧。<RouterLink to="/catalog" class="button">逛逛商城</RouterLink></p>
  <template v-else>
    <label>订单优惠券 <select v-model="userCouponId" :disabled="busy || couponsLoading"><option value="">{{ couponsLoading ? '正在查询可用优惠券…' : '不使用优惠券' }}</option><option v-for="c in coupons" :key="c.id" :value="c.id">{{ c.name }}（满 {{ c.minimumAmount }} 减 {{ c.amount }}）</option></select></label><RouterLink to="/coupons">领取更多优惠券</RouterLink>
    <div class="cart-list"><div class="cart-toolbar"><label><input type="checkbox" :checked="selectedAll" @change="cart.selectAll(($event.target as HTMLInputElement).checked)" /> 全选</label><button class="button secondary" @click="cart.removeSelected"><Trash2 :size="16" /> 删除选中</button><button class="button secondary" @click="cart.clear"><Trash2 :size="16" /> 清空</button></div>
      <article v-for="item in cart.items" :key="item.id" class="cart-row"><input type="checkbox" :checked="item.selected" @change="changeSelection(item, ($event.target as HTMLInputElement).checked)" /><ApplianceArt :kind="item.productId" /><div class="cart-description"><h3>{{ item.productName }}</h3><strong>¥{{ item.unitPrice.toFixed(2) }}</strong><p v-if="item.priceChanged" class="error-message">价格已变更</p><p>小计 ¥{{ item.subtotal.toFixed(2) }}</p></div><div class="stepper"><button :aria-label="`减少${item.productName}数量`" @click="cart.change(item.id, -1)"><Minus :size="15" /></button><span>{{ item.quantity }}</span><button :aria-label="`增加${item.productName}数量`" :disabled="item.quantity >= item.availableStock" @click="cart.change(item.id, 1)"><Plus :size="15" /></button></div></article>
      <div v-if="cart.cart.total > cart.items.length" class="pagination"><button class="button secondary" :disabled="cart.cart.page <= 1" @click="cart.refresh(cart.cart.page - 1)">上一页</button><span>{{ cart.cart.page }} / {{ Math.max(1, Math.ceil(cart.cart.total / cart.cart.size)) }}</span><button class="button secondary" :disabled="cart.cart.page * cart.cart.size >= cart.cart.total" @click="cart.refresh(cart.cart.page + 1)">下一页</button></div>
    </div>
    <section v-if="cart.cart.invalidItems.length" class="cart-list invalid-items"><div class="cart-toolbar"><h2>失效商品（{{ cart.cart.invalidItems.length }}）</h2><button class="button secondary" @click="removeInvalid"><Trash2 :size="16" /> 删除失效商品</button></div><article v-for="item in cart.cart.invalidItems" :key="item.id" class="cart-row"><ApplianceArt :kind="item.productId" /><div class="cart-description"><h3>{{ item.productName || '商品已下架' }}</h3><p>{{ item.availableStock ? `库存不足，剩余 ${item.availableStock}` : '商品下架或库存不足' }}</p><strong>¥{{ item.unitPrice.toFixed(2) }}</strong></div><button class="button secondary" @click="cart.change(item.id, -item.quantity)"><Trash2 :size="16" /> 删除</button></article></section>
    <RouterLink to="/addresses" class="button secondary">管理或补全收货地址</RouterLink><div class="cart-summary"><div>已选 {{ cart.cart.selectedQuantity }} 件 <strong>¥{{ Number(cart.cart.selectedAmount).toFixed(2) }}</strong><small>结算前将再次校验库存和价格</small></div><button class="button" :disabled="busy || !cart.cart.selectedQuantity" @click="checkout">{{ busy ? '提交中...' : '提交订单' }}</button></div><p v-if="message" class="inline-notice" role="status">{{ message }}</p>
  </template>
</template>
