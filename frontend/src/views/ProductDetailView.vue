<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { catalogApi, demoMode } from '../api/catalog'
import type { Product } from '../api/types'
import { useCartStore } from '../stores/cart'
import { session } from '../platform/session'
import ProductImage from '../components/ProductImage.vue'
const route = useRoute(), router = useRouter(), cart = useCartStore()
const product = ref<Product>(), loading = ref(false), error = ref(''), notice = ref(''), busy = ref(false), selected = ref(0)
let sequence = 0
const images = computed(() => [...new Set([product.value?.coverUrl, ...(product.value?.detailImages || [])].filter((x): x is string => !!x))])
async function load() { const token = ++sequence; loading.value = true; error.value = ''; notice.value = ''; selected.value = 0; try { const result = await catalogApi.detail(String(route.params.id)); if (token === sequence) product.value = result } catch(e) { if(token === sequence) error.value = (e as Error).message } finally { if(token === sequence) loading.value = false } }
async function add() { if (!product.value || busy.value) return; if (!demoMode && !session.read()) { await router.push({ path: '/login', query: { redirect: route.fullPath } }); return } busy.value = true; try { await cart.add(product.value); notice.value = '已加入购物车' } catch(e) { notice.value = (e as Error).message } finally { busy.value = false } }
watch(() => route.params.id, load, { immediate: true })
</script>
<template>
  <RouterLink to="/catalog">← 返回商城</RouterLink>
  <p v-if="loading" class="state" role="status">正在加载商品详情...</p><div v-else-if="error" class="state" role="alert">{{ error }} <button @click="load">重新加载</button></div>
  <article v-else-if="product"><div class="product-detail"><div><div class="detail-cover"><ProductImage :src="images[selected]" :alt="product.name" :kind="product.id" /></div><div class="detail-thumbnails"><button v-for="(src, index) in images" :key="src" :aria-label="`查看图片 ${index + 1}`" :aria-pressed="selected === index" @click="selected = index"><ProductImage :src="src" :alt="product.name" :kind="product.id" /></button></div></div>
  <div><h1>{{ product.name }}</h1><p>{{ product.subtitle }}</p><div class="product-price"><strong>¥{{ product.price.toFixed(2) }}</strong><del v-if="product.originalPrice != null && product.originalPrice > product.price">¥{{ product.originalPrice.toFixed(2) }}</del></div><p>已售 {{ product.sales }} · 可用库存 {{ product.availableStock ?? '待确认' }}</p><button class="button primary" :disabled="busy || product.availableStock === 0" @click="add">{{ product.availableStock === 0 ? '暂时缺货' : busy ? '正在加入...' : '加入购物车' }}</button><p v-if="notice" role="status">{{ notice }} <RouterLink to="/cart">查看购物车</RouterLink></p></div></div>
  <section class="detail-description"><h2>商品介绍</h2><p>{{ product.description || '暂无详细介绍，请以商品实际信息为准。' }}</p><div v-for="src in product.detailImages" :key="src" class="detail-long-image"><ProductImage :src="src" :alt="`${product.name}详情`" :kind="product.id" /></div></section></article>
</template>
