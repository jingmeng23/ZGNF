<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { catalogApi, demoMode } from '../api/catalog'
import type { Product } from '../api/types'
import { useCartStore } from '../stores/cart'
import { session } from '../platform/session'
import ProductImage from '../components/ProductImage.vue'
import { mallApi } from '../api/mall'
import type { Detail, Matrix } from '../api/mall'
const detail = ref<Detail>(),
  matrix = ref<Matrix>(),
  selection = ref<Record<string, string>>({}),
  favorite = ref(false),
  favoriteBusy = ref(false),
  skuLoading = ref(false),
  skuError = ref('')
let skuSequence = 0
const route = useRoute(),
  router = useRouter(),
  cart = useCartStore()
const product = ref<Product>(),
  loading = ref(false),
  error = ref(''),
  notice = ref(''),
  busy = ref(false),
  selected = ref(0)
let sequence = 0
const images = computed(() =>
  detail.value?.mainImages.length
    ? detail.value.mainImages
    : [
        ...new Set(
          [product.value?.coverUrl, ...(product.value?.detailImages || [])].filter(
            (x): x is string => !!x,
          ),
        ),
      ],
)
const selectedSku = computed(() =>
  matrix.value?.skus.find((s) => s.id === matrix.value?.selectedSkuId),
)
const displayPrice = computed(() => selectedSku.value?.price ?? product.value?.price ?? 0)
async function load() {
  const token = ++sequence
  skuSequence++
  loading.value = true
  error.value = ''
  notice.value = ''
  selected.value = 0
  detail.value = undefined
  matrix.value = undefined
  selection.value = {}
  favorite.value = false
  skuError.value = ''
  skuLoading.value = false
  const id = String(route.params.id)
  try {
    if (demoMode) {
      const result = await catalogApi.detail(id)
      if (token === sequence) product.value = result
    } else {
      const result = await mallApi.detail(id)
      if (token === sequence) {
        detail.value = result
        matrix.value = result.matrix
        product.value = {
          ...result.product,
          description: result.description,
          detailImages: result.detailImages,
        }
      }
      if (session.read()) {
        void mallApi.visit(id).catch(() => {})
        try {
          const saved = await mallApi.isFavorite(id)
          if (token === sequence) favorite.value = saved
        } catch {
          if (token === sequence) notice.value = '收藏状态加载失败'
        }
      }
    }
  } catch (e) {
    if (token === sequence) error.value = (e as Error).message
  } finally {
    if (token === sequence) loading.value = false
  }
}
async function refreshMatrix() {
  if (demoMode || !matrix.value?.skus.length || loading.value) return
  const token = ++skuSequence
  skuLoading.value = true
  skuError.value = ''
  try {
    const result = await mallApi.matrix(String(route.params.id), selection.value)
    if (token === skuSequence) matrix.value = result
  } catch (e) {
    if (token === skuSequence) {
      skuError.value = (e as Error).message
      if (matrix.value) matrix.value.selectedSkuId = null
    }
  } finally {
    if (token === skuSequence) skuLoading.value = false
  }
}
function choose(name: string, value: string) {
  selection.value = { ...selection.value, [name]: value }
  void refreshMatrix()
}
function resetSelection() {
  selection.value = {}
  void refreshMatrix()
}
async function toggleFavorite() {
  if (!session.read()) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (favoriteBusy.value) return
  const token = sequence
  favoriteBusy.value = true
  try {
    await mallApi.favorite(String(route.params.id), !favorite.value)
    if (token === sequence) {
      favorite.value = !favorite.value
      notice.value = favorite.value ? '已收藏' : '已取消收藏'
    }
  } catch (e) {
    if (token === sequence) notice.value = (e as Error).message
  } finally {
    favoriteBusy.value = false
  }
}
const refreshTimer = setInterval(() => {
  void refreshMatrix()
}, 20000)
onUnmounted(() => {
  clearInterval(refreshTimer)
  sequence++
  skuSequence++
})
async function add() {
  if (!product.value || busy.value) return
  if (!demoMode && !session.read()) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  busy.value = true
  try {
    await cart.add(product.value)
    notice.value = '已加入购物车'
  } catch (e) {
    notice.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
watch(() => route.params.id, load, { immediate: true })
</script>
<template>
  <RouterLink to="/catalog">← 返回商城</RouterLink>
  <p v-if="loading" class="state" role="status">正在加载商品详情...</p>
  <div v-else-if="error" class="state" role="alert">
    {{ error }} <button @click="load">重新加载</button>
  </div>
  <article v-else-if="product">
    <div class="product-detail">
      <div>
        <div class="detail-cover">
          <ProductImage :src="images[selected]" :alt="product.name" :kind="product.id" />
        </div>
        <div class="detail-thumbnails">
          <button
            v-for="(src, index) in images"
            :key="src"
            :aria-label="`查看图片 ${index + 1}`"
            :aria-pressed="selected === index"
            @click="selected = index"
          >
            <ProductImage :src="src" :alt="product.name" :kind="product.id" />
          </button>
        </div>
      </div>
      <div>
        <h1>{{ product.name }}</h1>
        <p v-if="product.brandName" class="product-brand">品牌：{{ product.brandName }}</p>
        <p>{{ product.subtitle }}</p>
        <div class="product-price">
          <strong>¥{{ displayPrice.toFixed(2) }}</strong
          >
        </div>
        <p v-if="product.statusMessage" class="mall-status" role="status">
          {{ product.statusMessage }}
        </p>
        <small class="price-note">页面价格为当前展示价，最终价格以订单结算页为准。</small>
        <section v-if="matrix?.skus.length" class="sku-picker" aria-label="选择商品规格">
          <fieldset v-for="dimension in matrix.dimensions" :key="dimension.name">
            <legend>{{ dimension.name }}</legend>
            <button
              v-for="option in dimension.options"
              :key="option.value"
              :disabled="option.disabled || skuLoading"
              :aria-pressed="selection[dimension.name] === option.value"
              @click="choose(dimension.name, option.value)"
            >
              {{ option.value }}{{ option.disabled ? '（不可选）' : '' }}
            </button>
          </fieldset>
          <button @click="resetSelection">
            重选规格</button
          ><button :disabled="skuLoading" @click="refreshMatrix">刷新价格与库存</button>
          <p v-if="skuLoading" role="status">正在确认价格与库存…</p>
          <p v-if="skuError" role="alert">{{ skuError }}</p>
          <p v-if="selectedSku">
            已选 {{ selectedSku.name }} · ¥{{ selectedSku.price.toFixed(2) }} · 库存
            {{ selectedSku.availableStock }}
          </p>
          <p v-else>请选择完整规格组合</p>
          <details>
            <summary>全部规格的实时价格与库存</summary>
            <ul>
              <li v-for="sku in matrix.skus" :key="sku.id">
                {{ sku.name }} · ¥{{ sku.price.toFixed(2) }} · 库存 {{ sku.availableStock }}
                {{ sku.reason }}
              </li>
            </ul>
          </details>
        </section>
        <button
          v-if="!demoMode"
          class="button secondary"
          :disabled="favoriteBusy"
          :aria-pressed="favorite"
          @click="toggleFavorite"
        >
          {{ favorite ? '取消收藏' : '收藏商品' }}
        </button>
        <p>已售 {{ product.sales }} · 可用库存 {{ product.availableStock ?? '待确认' }}</p>
        <button
          class="button primary"
          v-if="!matrix?.skus.length"
          :disabled="busy || product.availableStock === 0 || product.purchasable === false"
          @click="add"
        >
          {{ product.availableStock === 0 ? '暂时缺货' : busy ? '正在加入...' : '加入购物车' }}
        </button>
        <p v-if="notice" role="status">
          {{ notice }} <RouterLink to="/cart">查看购物车</RouterLink>
        </p>
      </div>
    </div>
    <section v-if="detail" class="detail-description">
      <h2>服务承诺</h2>
      <ul v-if="detail.servicePromises.length">
        <li v-for="promise in detail.servicePromises" :key="promise">{{ promise }}</li>
      </ul>
      <p v-else>暂无服务承诺说明</p>
      <h2>评价概览</h2>
      <p>
        共 {{ detail.review.count }} 条评价 · 好评 {{ detail.review.positiveCount }} 条 · 平均
        {{ detail.review.averageRating }} 分
      </p>
      <h2>商品参数</h2>
      <dl>
        <template v-for="(values, name) in detail.attributes" :key="name"
          ><dt>{{ name }}</dt>
          <dd>{{ values.join(' / ') }}</dd></template
        >
      </dl>
    </section>
    <section class="detail-description">
      <h2>商品介绍</h2>
      <p>{{ product.description || '暂无详细介绍，请以商品实际信息为准。' }}</p>
      <div v-for="src in product.detailImages" :key="src" class="detail-long-image">
        <ProductImage :src="src" :alt="`${product.name}详情`" :kind="product.id" />
      </div>
    </section>
  </article>
</template>

<style scoped>
.sku-picker fieldset {
  border: 0;
  padding: 0.6rem 0;
}
.sku-picker button {
  margin: 0.25rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid #ccc;
  border-radius: 6px;
}
.sku-picker button[aria-pressed='true'] {
  border-color: #b45b37;
  background: #f9e9df;
}
.sku-picker button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.mall-status {
  padding: 1rem;
  background: #fff0df;
}
</style>
