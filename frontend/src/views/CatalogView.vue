<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductGrid from '../components/ProductGrid.vue'
import { catalogApi } from '../api/catalog'
import { demoMode } from '../api/catalog'
import { mallApi } from '../api/mall'
import type { Category, Product } from '../api/types'
const route = useRoute(),
  router = useRouter()
const search = ref(''),
  categories = ref<Category[]>([]),
  products = ref<Product[]>([])
const loading = ref(false),
  error = ref(''),
  categoryError = ref(''),
  total = ref(0),
  pages = ref(0)
const brands = ref<{ id: string; name: string }[]>([]),
  attributes = ref<Record<string, string[]>>({}),
  hotwords = ref<string[]>([]),
  suggestions = ref<string[]>([]),
  recommendations = ref<Product[]>([]),
  message = ref('')
let sequence = 0
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const category = computed(() => String(route.query.categoryId || ''))
const sorting = computed(
  () => `${route.query.sort || 'comprehensive'}:${route.query.order || 'desc'}`,
)
async function loadCategories() {
  categoryError.value = ''
  try {
    categories.value = await catalogApi.categories()
  } catch (e) {
    categoryError.value = (e as Error).message
  }
}
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
    categoryId: category.value,
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
async function loadExtras() {
  if (demoMode) return
  const results = await Promise.allSettled([
    mallApi.brands(),
    mallApi.attributes(),
    mallApi.hotwords(),
  ])
  if (results[0].status === 'fulfilled') brands.value = results[0].value
  if (results[1].status === 'fulfilled') attributes.value = results[1].value
  if (results[2].status === 'fulfilled') hotwords.value = results[2].value
  if (results.some((r) => r.status === 'rejected')) categoryError.value = '部分筛选项加载失败'
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
function sort(event: Event) {
  const [field, order] = (event.target as HTMLSelectElement).value.split(':')
  filter({ sort: field!, order: order! })
}
function categoryLabel(item: Category) {
  const names = [item.name],
    seen = new Set([item.id])
  let parent = categories.value.find((c) => c.id === item.parentId)
  while (parent && !seen.has(parent.id)) {
    names.unshift(parent.name)
    seen.add(parent.id)
    parent = categories.value.find((c) => c.id === parent!.parentId)
  }
  return names.join(' / ')
}
watch(() => route.fullPath, load, { immediate: true })
onMounted(loadCategories)
onMounted(loadExtras)
</script>
<template>
  <header class="page-heading">
    <span class="eyebrow">GOOD THINGS FOR HOME</span>
    <h1>好物商城</h1>
    <p>为家的每一份温暖，选一份可靠的品质。</p>
  </header>
  <form class="searchbar" @submit.prevent="filter({ q: search.trim() })">
    <input
      v-model="search"
      list="mall-suggestions"
      @input="suggest"
      maxlength="100"
      aria-label="搜索商城商品"
      placeholder="搜索你需要的安心好物"
    /><button class="button secondary" type="submit">搜索</button>
  </form>
  <div class="catalog-toolbar">
    <label
      >分类
      <select
        :value="category"
        @change="filter({ categoryId: ($event.target as HTMLSelectElement).value })"
      >
        <option value="">全部分类</option>
        <option v-for="item in categories" :key="item.id" :value="item.id">
          {{ categoryLabel(item) }}
        </option>
      </select></label
    ><label
      >排序
      <select :value="sorting" @change="sort">
        <option value="comprehensive:desc">综合排序</option>
        <option value="newest:desc">新品优先</option>
        <option value="sales:desc">销量优先</option>
        <option value="price:asc">价格从低到高</option>
        <option value="price:desc">价格从高到低</option>
      </select></label
    >
  </div>
  <datalist id="mall-suggestions">
    <option v-for="word in suggestions" :key="word" :value="word" />
  </datalist>
  <div class="mall-hotwords" v-if="hotwords.length">
    热搜
    <button v-for="word in hotwords" :key="word" @click="filter({ q: word })">{{ word }}</button>
  </div>
  <div class="catalog-toolbar mall-filters" v-if="!demoMode">
    <label
      >品牌
      <select
        :value="route.query.brandId || ''"
        @change="filter({ brandId: ($event.target as HTMLSelectElement).value })"
      >
        <option value="">全部品牌</option>
        <option v-for="brand in brands" :key="brand.id" :value="brand.id">{{ brand.name }}</option>
      </select></label
    >
    <label
      >最低价
      <input
        type="number"
        min="0"
        step="0.01"
        :value="route.query.minPrice || ''"
        @change="filter({ minPrice: ($event.target as HTMLInputElement).value })"
    /></label>
    <label
      >最高价
      <input
        type="number"
        min="0"
        step="0.01"
        :value="route.query.maxPrice || ''"
        @change="filter({ maxPrice: ($event.target as HTMLInputElement).value })"
    /></label>
    <label
      ><input
        type="checkbox"
        :checked="route.query.inStock === 'true'"
        @change="filter({ inStock: String(($event.target as HTMLInputElement).checked) })"
      />
      仅看有货</label
    >
    <label v-for="(values, name) in attributes" :key="name"
      >{{ name }}
      <select
        :value="route.query[`attr:${name}`] || ''"
        @change="filter({ [`attr:${name}`]: ($event.target as HTMLSelectElement).value })"
      >
        <option value="">全部</option>
        <option v-for="value in values" :key="value">{{ value }}</option>
      </select></label
    >
    <button @click="router.push('/catalog')">重置筛选</button>
  </div>
  <p v-if="message" role="status">{{ message }}</p>
  <p v-if="categoryError" role="alert">
    分类加载失败：{{ categoryError }} <button @click="loadCategories">重试</button>
  </p>
  <p v-if="loading" class="state" role="status">正在加载商品...</p>
  <div v-else-if="error" class="state" role="alert">
    {{ error }} <button @click="load">重试</button>
  </div>
  <template v-else
    ><p>共 {{ total }} 件商品</p>
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
</template>

<style scoped>
.mall-filters {
  flex-wrap: wrap;
  gap: 1rem;
}
.mall-filters input[type='number'] {
  width: 100px;
  padding: 0.5rem;
}
.mall-hotwords {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}
</style>
