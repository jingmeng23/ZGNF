<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Plus, ShoppingBag } from 'lucide-vue-next'
import { catalogApi } from '../api/catalog'
import type { Product } from '../api/types'
import { useCartStore } from '../stores/cart'
import ApplianceArt from './ApplianceArt.vue'
const props = withDefaults(defineProps<{ search?: string; category?: string }>(), { search: '', category: '全部' })
const products = ref<Product[]>([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const cart = useCartStore()
const visible = computed(() => products.value.filter(p => (!props.search || `${p.name}${p.subtitle}`.includes(props.search)) && (props.category === '全部' || (props.category === '安全配件' ? ['demo-3', 'demo-4'].includes(p.id) : p.name.includes(props.category)))))
async function load() {
  loading.value = true; error.value = ''
  try { products.value = await catalogApi.list() } catch (e) { error.value = (e as Error).message } finally { loading.value = false }
}
function add(product: Product) { cart.add(product); notice.value = `已将「${product.name}」加入演示购物车` }
onMounted(load)
</script>

<template>
  <p v-if="loading" class="state" role="status">正在为你寻找安心好物…</p>
  <div v-else-if="error" class="state" role="alert"><p>{{ error }}</p><button class="button secondary" @click="load">重新加载</button></div>
  <p v-else-if="!visible.length" class="state">没有找到相关商品，试试其他关键词。</p>
  <div v-else class="product-grid"><article v-for="(product, index) in visible" :key="product.id" class="product-card"><div class="product-image"><span class="product-tag">{{ ['人气精选', '舒适之选', '安心守护', '品质优选'][index % 4] }}</span><ApplianceArt :kind="product.id" /></div><div class="product-info"><h3>{{ product.name }}</h3><p>{{ product.subtitle }}</p><div class="product-price"><strong><small>¥</small>{{ product.price.toFixed(2) }}</strong><del>¥{{ product.originalPrice }}</del><button :aria-label="`将${product.name}加入购物车`" @click="add(product)"><Plus :size="18" /></button></div><small class="sales">演示商品 · 已售 {{ product.sales }} 件</small></div></article></div>
  <p v-if="notice" class="inline-notice" role="status"><ShoppingBag :size="16" />{{ notice }} <RouterLink to="/cart">查看购物车 →</RouterLink></p>
</template>
