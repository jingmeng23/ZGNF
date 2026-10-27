<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Plus } from 'lucide-vue-next'
import { catalogApi, demoMode } from '../api/catalog'
import type { Product } from '../api/types'
import { useCartStore } from '../stores/cart'
import { session } from '../platform/session'
import ProductImage from './ProductImage.vue'
const props = defineProps<{ products?: Product[] }>()
const loaded = ref<Product[]>([]),
  loading = ref(false),
  error = ref(''),
  notice = ref(''),
  pending = ref('')
const visible = computed(() => props.products ?? loaded.value)
const cart = useCartStore(),
  router = useRouter(),
  route = useRoute()
async function load() {
  loading.value = true
  error.value = ''
  try {
    loaded.value = (await catalogApi.list({ size: 8 })).list
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
async function add(product: Product) {
  if (!demoMode) {
    await router.push(`/products/${product.id}`)
    return
  }
  if (pending.value) return
  if (!demoMode && !session.read()) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  pending.value = product.id
  try {
    await cart.add(product)
    notice.value = `已将「${product.name}」加入购物车`
  } catch (e) {
    notice.value = (e as Error).message
  } finally {
    pending.value = ''
  }
}
onMounted(() => {
  if (props.products === undefined) void load()
})
</script>
<template>
  <p v-if="loading" class="state" role="status">正在加载商品...</p>
  <div v-else-if="error" class="state" role="alert">
    {{ error }} <button @click="load">重新加载</button>
  </div>
  <p v-else-if="!visible.length" class="state">没有找到相关商品</p>
  <div v-else class="product-grid">
    <article v-for="product in visible" :key="product.id" class="product-card">
      <RouterLink :to="`/products/${product.id}`" class="product-image"
        ><ProductImage :src="product.coverUrl" :alt="product.name" :kind="product.id"
      /></RouterLink>
      <div class="product-info">
        <h3>
          <RouterLink :to="`/products/${product.id}`">{{ product.name }}</RouterLink>
        </h3>
        <p>{{ product.subtitle }}</p>
        <div class="product-price">
          <strong><small>¥</small>{{ product.price.toFixed(2) }}</strong
          ><button
            :disabled="!!pending || product.purchasable === false || product.availableStock === 0"
            :aria-label="demoMode ? `将${product.name}加入购物车` : `查看${product.name}详情与规格`"
            @click="add(product)"
          >
            <Plus :size="18" />
          </button>
        </div>
        <small class="sales"
          >已售 {{ product.sales
          }}{{
            product.statusMessage
              ? ' · ' + product.statusMessage
              : product.availableStock === 0
                ? ' · 暂时缺货'
                : ''
          }}</small
        >
      </div>
    </article>
  </div>
  <p v-if="notice" class="inline-notice" role="status">
    {{ notice }} <RouterLink to="/cart">查看购物车 →</RouterLink>
  </p>
</template>
