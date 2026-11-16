<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { catalogApi, demoMode } from '../api/catalog'
import { mallApi } from '../api/mall'
import type { Product } from '../api/types'
import { useCartStore } from '../stores/cart'
import { session } from '../platform/session'
import ProductImage from './ProductImage.vue'
import { formatPrice } from './shop'
const props = defineProps<{ products?: Product[] }>()
const loaded = ref<Product[]>([]),
  loading = ref(false),
  error = ref(''),
  notice = ref(''),
  failed = ref(false),
  pending = ref('')
const visible = computed(() => props.products ?? loaded.value)
const cart = useCartStore(),
  router = useRouter(),
  route = useRoute()
const detailLink = (product: Product) => ({
  path: `/products/${product.id}`,
  query: route.path === '/catalog' ? { from: route.fullPath } : {},
})
const unavailable = (product: Product) =>
  product.purchasable === false ||
  product.availableStock === 0 ||
  ['off_sale', 'sold_out', 'presale', 'scheduled'].includes(product.status || '')
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
  if (pending.value || unavailable(product)) return
  if (product.skuCount) {
    await router.push(detailLink(product))
    return
  }
  if (!demoMode && !session.read()) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  pending.value = product.id
  notice.value = ''
  failed.value = false
  try {
    // Inspect old servers without skuCount before writing to the product-only cart.
    if (!demoMode) {
      const detail = await mallApi.detail(product.id)
      if (detail.matrix.skus.length) {
        await router.push(detailLink(product))
        return
      }
      if (unavailable(detail.product)) throw new Error('商品暂不可购买，请查看商品详情')
    }
    await cart.add(product)
    notice.value = demoMode
      ? `演示：已添加「${product.name}」`
      : `已将「${product.name}」加入购物车`
  } catch (e) {
    failed.value = true
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
  <p v-if="loading" class="state" role="status">正在加载商品…</p>
  <div v-else-if="error" class="state" role="alert">
    {{ error }} <button @click="load">重新加载</button>
  </div>
  <div v-else-if="!visible.length" class="shop-empty">
    <h3>没有找到相关商品</h3>
    <p>试试其他关键词，或放宽筛选条件。</p>
  </div>
  <div v-else class="product-grid shop-grid">
    <article v-for="(product, index) in visible" :key="product.id" class="product-card">
      <RouterLink :to="detailLink(product)" class="product-image"
        ><ProductImage
          :src="product.coverUrl"
          :alt="product.name"
          :kind="product.id"
          placeholder
          :priority="index < 4"
      /></RouterLink>
      <div class="product-info">
        <h3>
          <RouterLink :to="detailLink(product)">{{ product.name }}</RouterLink>
        </h3>
        <p>{{ product.subtitle }}</p>
        <strong class="card-price">{{ formatPrice(product.price) }}</strong>
        <div class="card-meta">
          <small v-if="product.sales > 0" class="sales">已售 {{ product.sales }}</small>
          <small v-if="unavailable(product)" class="card-state">{{
            product.statusMessage && product.statusMessage !== '已上架'
              ? product.statusMessage
              : product.availableStock === 0
                ? '暂时缺货'
                : '暂不可购买'
          }}</small>
        </div>
        <button
          class="card-buy"
          :disabled="!!pending || unavailable(product)"
          :aria-label="`${product.skuCount ? '选择规格：' : '加入购物车：'}${product.name}`"
          @click="add(product)"
        >
          {{
            pending === product.id
              ? '正在处理…'
              : unavailable(product)
                ? '暂不可购买'
                : product.skuCount
                  ? '选规格'
                  : '加入购物车'
          }}
        </button>
      </div>
    </article>
  </div>
  <p v-if="notice" class="inline-notice" :role="failed ? 'alert' : 'status'">
    {{ notice }} <RouterLink v-if="!failed" to="/cart">查看购物车 →</RouterLink>
  </p>
</template>

<style scoped>
.shop-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}
.product-card {
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
  box-shadow: 0 2px 8px #00000003;
  transition: box-shadow 0.2s;
}
.product-card:hover {
  box-shadow: 0 6px 22px #0000000c;
}
.product-image {
  aspect-ratio: 1;
  height: auto;
  background: #fafafa;
  padding: 12px;
}
.product-image :deep(img) {
  object-fit: contain;
}
.product-info {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 16px;
}
.product-info h3 {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 46px;
  font-size: 15px;
  line-height: 23px;
  color: #242424;
  overflow-wrap: anywhere;
}
.product-info p {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 36px;
  line-height: 18px;
  color: #666;
}
.card-price {
  margin-top: auto;
  font-size: 21px;
  color: #a53e2b;
}
.card-meta {
  min-height: 22px;
}
.sales,
.card-state {
  margin-top: 7px;
  color: #686868;
  font-size: 12px;
}
.card-state {
  color: #a53e2b;
}
.card-buy {
  width: 100%;
  min-height: 40px;
  margin-top: 14px;
  border: 1px solid #a53e2b;
  border-radius: 6px;
  color: #a53e2b;
  background: #fff;
  font-size: 13px;
}
.card-buy:hover:enabled {
  background: #f9efeb;
}
.card-buy:disabled {
  color: #777;
  border-color: #ddd;
  background: #f6f6f6;
  cursor: not-allowed;
}
.shop-empty {
  padding: 64px 16px;
  text-align: center;
  border: 1px dashed #ddd;
  border-radius: 8px;
  background: #fff;
}
.shop-empty h3 {
  color: #333;
}
.shop-empty p {
  color: #686868;
}
@media (max-width: 1200px) and (min-width: 761px) {
  .shop-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .shop-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
  .product-info {
    padding: 12px;
  }
  .product-info h3 {
    font-size: 14px;
  }
  .card-price {
    font-size: 19px;
  }
}
@media (max-width: 359px) {
  .shop-grid {
    grid-template-columns: 1fr;
  }
}
</style>
