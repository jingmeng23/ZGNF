<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductGrid from '../components/ProductGrid.vue'
import { ArrowDown, ArrowDownUp, ArrowUp, ChevronDown, Search, SlidersHorizontal } from 'lucide-vue-next'
import { catalogApi } from '../api/catalog'
import { demoMode } from '../api/catalog'
import { mallApi } from '../api/mall'
import type { Product } from '../api/types'
const route = useRoute(),
  router = useRouter()
const search = ref(''), products = ref<Product[]>([])
const priceRangeOpen = ref(false), minPriceDraft = ref(''), maxPriceDraft = ref('')
const loading = ref(false),
  error = ref(''),
  total = ref(0),
  pages = ref(0)
const suggestions = ref<string[]>([]),
  recommendations = ref<Product[]>([]),
  message = ref('')
let sequence = 0
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
async function load() {
  const token = ++sequence
  loading.value = true
  error.value = ''
  search.value = String(route.query.q || '')
  const attrs: Record<string, string> = {}
  for (const [key, value] of Object.entries(route.query))
    if (key.startsWith('attr:') && value) attrs[key.slice(5)] = String(value)
  const query = {
    page: page.value,
    size: 12,
    keyword: search.value.trim(),
    categoryId: String(route.query.categoryId || ''),
    brandId: String(route.query.brandId || ''),
    minPrice: String(route.query.minPrice || ''),
    maxPrice: String(route.query.maxPrice || ''),
    inStock: route.query.inStock === 'true',
    attributes: attrs,
    sort: String(route.query.sort || 'comprehensive'),
    order: String(route.query.order || 'desc'),
  }
  try {
    const response = demoMode
      ? { results: await catalogApi.list(query), recommendations: [], message: '' }
      : await mallApi.search(query)
    const result = response.results
    if (token === sequence) {
      products.value = result.list
      total.value = result.total
      pages.value = result.pages
      recommendations.value = response.recommendations
      message.value = response.message
    }
  } catch (e) {
    if (token === sequence) error.value = (e as Error).message
  } finally {
    if (token === sequence) loading.value = false
  }
}
let suggestSequence = 0
async function suggest() {
  if (demoMode) return
  const token = ++suggestSequence
  try {
    const result = await mallApi.suggest(search.value)
    if (token === suggestSequence) suggestions.value = result
  } catch {
    if (token === suggestSequence) suggestions.value = []
  }
}
function filter(values: Record<string, string>) {
  void router.push({ query: { ...route.query, ...values, page: '1' } })
}
function sortBy(field: 'comprehensive' | 'sales' | 'price') {
  const order = String(route.query.sort || '') === field && String(route.query.order || 'desc') === 'desc' ? 'asc' : 'desc'
  filter({ sort: field, order })
}
function applyPriceRange() {
  filter({ minPrice: minPriceDraft.value.trim(), maxPrice: maxPriceDraft.value.trim() })
  priceRangeOpen.value = false
}
function togglePriceRange() {
  priceRangeOpen.value = !priceRangeOpen.value
  if (priceRangeOpen.value) {
    minPriceDraft.value = String(route.query.minPrice || '')
    maxPriceDraft.value = String(route.query.maxPrice || '')
  }
}
watch(() => route.fullPath, load, { immediate: true })
</script>
<template>
  <div class="mall-page">
  <header class="page-heading">
    <span class="eyebrow">GOOD THINGS FOR HOME</span>
    <h1>好物商城</h1>
    <p>为家的每一份温暖，选一份可靠的品质。</p>
  </header>
  <form class="mall-search" @submit.prevent="filter({ q: search.trim() })">
    <Search :size="19" aria-hidden="true" />
    <input
      v-model="search"
      list="mall-suggestions"
      @input="suggest"
      maxlength="100"
      aria-label="搜索商城商品"
      placeholder="搜索你需要的安心好物"
    /><button type="submit">搜索</button>
  </form>
  <div class="catalog-controls mall-panel">
    <div class="filter-label"><SlidersHorizontal :size="16" />商品筛选与排序</div>
    <nav class="sort-menu" aria-label="商品排序">
      <button class="sort-option" :class="{ active: route.query.sort === 'comprehensive' || !route.query.sort }" @click="filter({ sort: 'comprehensive', order: 'desc' })">综合<ChevronDown :size="14" /></button>
      <button class="sort-option" :class="{ active: route.query.sort === 'sales' }" @click="sortBy('sales')">销量<component :is="route.query.sort === 'sales' ? (route.query.order === 'asc' ? ArrowUp : ArrowDown) : ArrowDownUp" :size="15" /></button>
      <button class="sort-option" :class="{ active: route.query.sort === 'price' }" @click="sortBy('price')">价格<component :is="route.query.sort === 'price' ? (route.query.order === 'asc' ? ArrowUp : ArrowDown) : ArrowDownUp" :size="15" /></button>
      <div class="price-range-wrap">
        <button class="sort-option" :class="{ active: priceRangeOpen || route.query.minPrice || route.query.maxPrice }" :aria-expanded="priceRangeOpen" @click="togglePriceRange">价格区间<ChevronDown :size="14" /></button>
        <form v-if="priceRangeOpen" class="price-range-popover" @submit.prevent="applyPriceRange">
          <label>最低价<input v-model="minPriceDraft" type="number" min="0" step="0.01" placeholder="¥" /></label>
          <span class="range-separator">—</span>
          <label>最高价<input v-model="maxPriceDraft" type="number" min="0" step="0.01" placeholder="¥" /></label>
          <button class="range-submit" type="submit">确定</button>
        </form>
      </div>
    </nav>
  </div>
  <datalist id="mall-suggestions">
    <option v-for="word in suggestions" :key="word" :value="word" />
  </datalist>
  <p v-if="message" class="mall-message" role="status">{{ message }}</p>
  <p v-if="loading" class="state" role="status">正在加载商品...</p>
  <div v-else-if="error" class="state" role="alert">
    {{ error }} <button @click="load">重试</button>
  </div>
  <template v-else
    ><div class="result-heading"><h2>精选好物</h2><span>共 {{ total }} 件商品</span></div>
    <ProductGrid :products="products" />
    <nav class="catalog-pagination" aria-label="商品分页">
      <button
        :disabled="page <= 1"
        @click="router.push({ query: { ...route.query, page: String(page - 1) } })"
      >
        上一页</button
      ><span>第 {{ page }} / {{ Math.max(1, pages) }} 页</span
      ><button
        :disabled="page >= pages"
        @click="router.push({ query: { ...route.query, page: String(page + 1) } })"
      >
        下一页
      </button>
    </nav>
    <section v-if="!loading && !error && !total && recommendations.length">
      <h2>看看这些热门好物</h2>
      <ProductGrid :products="recommendations" />
    </section>
  </template>
  </div>
</template>

<style scoped>
.mall-page { max-width: 1240px; margin: 0 auto; padding-bottom: 36px; }
.mall-page > .page-heading { padding: 8px 2px 20px; }
.mall-page > .page-heading h1 { margin-top: 8px; letter-spacing: .01em; }
.mall-page > .page-heading p { margin-top: 8px; color: #78877e; }
.mall-search { display: flex; height: 52px; align-items: center; gap: 11px; margin-bottom: 18px; padding: 0 8px 0 16px; border: 1px solid #e2e8e2; border-radius: 7px; background: #fff; color: #91a099; box-shadow: 0 2px 10px #1c45310a; }
.mall-search input { flex: 1; min-width: 0; height: 100%; border: 0; outline: 0; color: #26362d; background: transparent; }
.mall-search button { height: 38px; padding: 0 22px; border: 0; border-radius: 5px; color: #fff; background: #b84832; font-size: 13px; }
.mall-search button:hover { background: #9d3d2b; }
.mall-panel { min-height: 68px; padding: 14px 16px; border: 1px solid #e7ece8; border-radius: 7px; background: #fff; box-shadow: 0 2px 9px #1c453108; }
.filter-label { display: inline-flex; align-items: center; gap: 7px; margin-right: 10px; color: #6c7d73; font-size: 12px; white-space: nowrap; }
.mall-message { padding: 10px 14px; border: 1px solid #ead8d0; border-radius: 5px; color: #93452f; background: #fff9f6; font-size: 13px; }
.result-heading { display: flex; align-items: baseline; justify-content: space-between; margin: 28px 2px 13px; }
.result-heading h2 { margin: 0; color: #24362c; font-size: 20px; }
.result-heading span { color: #829087; font-size: 12px; }
.mall-page :deep(.product-grid) { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.mall-page :deep(.product-card) { border-color: #e7ece8; border-radius: 7px; box-shadow: 0 2px 8px #173e3508; transition: transform .18s ease, box-shadow .18s ease; }
.mall-page :deep(.product-card:hover) { transform: translateY(-3px); box-shadow: 0 8px 20px #173e3514; }
.mall-page :deep(.product-image) { height: 210px; background: #f4f7f4; }
.mall-page :deep(.product-info) { padding: 15px 16px 16px; }
.mall-page :deep(.product-info h3) { font-size: 15px; }
.mall-page :deep(.product-price) { color: #b84832; }
.mall-page :deep(.product-price button) { background: #b84832; }
.mall-page :deep(.product-price button:hover) { background: #963b29; }
.mall-page :deep(.catalog-pagination button) { border-color: #e0e7e1; background: #fff; }
.catalog-controls { display: flex; align-items: center; flex-wrap: wrap; justify-content: space-between; gap: 12px 16px; margin-bottom: 18px; }
.sort-menu { display: flex; align-items: center; gap: 12px; }
.sort-option { display: inline-flex; min-width: 76px; min-height: 38px; align-items: center; justify-content: center; gap: 8px; padding: 0 14px; border: 1px solid #e2e2e2; border-radius: 5px; color: #333; background: #fff; font-size: 13px; }
.sort-option.active { border-color: #e4393c; color: #e4393c; }
.sort-option svg { color: currentColor; }
.price-range-wrap { position: relative; }
.price-range-popover { position: absolute; z-index: 4; top: calc(100% + 8px); left: 0; display: flex; align-items: end; gap: 8px; width: max-content; padding: 14px; border: 1px solid #e5e5e5; background: #fff; box-shadow: 0 5px 18px #00000012; }
.price-range-popover label { display: grid; gap: 5px; color: #777; font-size: 11px; }
.price-range-popover input { width: 86px; min-width: 0; padding: 7px; border: 1px solid #ddd; color: #333; background: #fff; font-size: 12px; }
.range-separator { padding-bottom: 7px; color: #999; }
.range-submit { height: 31px; padding: 0 12px; border: 0; color: #fff; background: #e4393c; font-size: 12px; }
@media (max-width: 620px) {
  .mall-page { padding-bottom: 20px; }
  .mall-search { height: 48px; }
  .mall-search button { padding: 0 15px; }
  .filter-label { width: 100%; margin-bottom: 2px; }
  .catalog-controls { align-items: flex-start; flex-direction: column; }
  .sort-menu { width: 100%; justify-content: space-between; gap: 6px; }
  .sort-option { min-width: 0; min-height: 36px; gap: 5px; padding: 0 9px; font-size: 12px; }
  .price-range-popover { left: auto; right: 0; }
  .mall-page :deep(.product-grid) { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .mall-page :deep(.product-image) { height: 150px; }
  .mall-page :deep(.product-info) { padding: 11px; }
}
</style>
