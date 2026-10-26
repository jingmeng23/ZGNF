<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ShoppingBag, Minus, Plus } from 'lucide-vue-next'
import { useCartStore } from '../stores/cart'
import { addressApi, orderApi } from '../api/commerce'
import type { UserAddress } from '../api/types'
import { demoMode } from '../api/catalog'
import { session } from '../platform/session'
import ApplianceArt from '../components/ApplianceArt.vue'
const cart = useCartStore()
const addresses = ref<UserAddress[]>([]), addressId = ref(''), busy = ref(false), message = ref('')
async function checkout() {
  if (demoMode) { message.value = '演示模式不创建真实订单'; return }
  if (!session.read()) { message.value = '请先登录后再提交订单'; return }
  busy.value = true; message.value = ''
  try { if (!addresses.value.length) addresses.value = await addressApi.list(); if (!addressId.value) addressId.value = addresses.value.find(a => a.isDefault)?.id || addresses.value[0]?.id || ''; if (!addressId.value) { message.value = '请先在个人中心添加收货地址'; return }; await orderApi.create(addressId.value); await cart.refresh(); message.value = '订单已创建'; }
  catch (e) { message.value = (e as Error).message } finally { busy.value = false }
}
onMounted(() => { if (!demoMode && session.read()) void cart.refresh() })
</script>
<template>
  <header class="page-heading"><span class="eyebrow">YOUR HOME ESSENTIALS</span><h1>购物车 <small>{{ cart.count }} 件商品</small></h1><p>确认商品和数量后即可提交订单。</p></header>
  <p v-if="cart.busy" class="state" role="status">正在加载购物车...</p>
  <p v-else-if="!cart.items.length" class="state">购物车还是空的，去看看精选商品吧。<RouterLink to="/catalog" class="button">逛逛商城</RouterLink></p>
  <template v-else><div class="cart-list"><article v-for="item in cart.items" :key="item.id" class="cart-row"><ApplianceArt :kind="item.productId" /><div class="cart-description"><h3>{{ item.productName }}</h3><strong>¥{{ item.unitPrice.toFixed(2) }}</strong></div><div class="stepper"><button :aria-label="`减少${item.productName}数量`" @click="cart.change(item.id, -1)"><Minus :size="15" /></button><span>{{ item.quantity }}</span><button :aria-label="`增加${item.productName}数量`" :disabled="item.quantity >= item.availableStock" @click="cart.change(item.id, 1)"><Plus :size="15" /></button></div></article></div><div class="cart-summary"><div>合计 <strong>¥{{ (cart.totalCents / 100).toFixed(2) }}</strong><small>金额以提交订单时后端计算为准</small></div><button class="button" :disabled="busy" @click="checkout">{{ busy ? '提交中...' : '提交订单' }}</button></div><p v-if="message" class="inline-notice" role="status">{{ message }}</p></template>
</template>
