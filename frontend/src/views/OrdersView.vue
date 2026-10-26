<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Package } from 'lucide-vue-next'
import { orderApi } from '../api/commerce'
import type { Order } from '../api/types'
const orders = ref<Order[]>([]), loading = ref(true), error = ref('')
async function load() { loading.value = true; error.value = ''; try { orders.value = (await orderApi.list()).list } catch (e) { error.value = (e as Error).message } finally { loading.value = false } }
onMounted(load)
</script>
<template><header class="page-heading"><span class="eyebrow">MY ORDERS</span><h1>我的订单</h1></header><p v-if="loading" class="state">正在加载订单...</p><div v-else-if="error" class="state" role="alert"><p>{{ error }}</p><button class="button secondary" @click="load">重试</button></div><div v-else-if="!orders.length" class="empty-state"><Package :size="48" :stroke-width="1.2" /><h2>还没有订单</h2><RouterLink to="/catalog" class="button">继续逛逛</RouterLink></div><div v-else class="cart-list"><article v-for="order in orders" :key="order.id" class="cart-row"><Package :size="25" /><div class="cart-description"><h3>{{ order.orderNo }}</h3><p>{{ order.createTime }} · {{ order.items.length }} 件商品</p><strong>¥{{ Number(order.payAmount).toFixed(2) }}</strong></div><span>{{ order.status }}</span></article></div></template>
