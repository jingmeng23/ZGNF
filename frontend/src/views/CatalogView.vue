<script setup lang="ts">
import { ref, watch, computed, nextTick, onUnmounted } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import { Search, SlidersHorizontal, X } from 'lucide-vue-next'
import ProductGrid from '../components/ProductGrid.vue'
import ShopHeader from '../components/ShopHeader.vue'
import { priceRange, catalogScroll } from '../components/shop'
import { catalogApi, demoMode } from '../api/catalog'
import { mallApi } from '../api/mall'
import type { Product, Category } from '../api/types'
const route = useRoute(),
  router = useRouter()
const search = ref(''),
  products = ref<Product[]>([]),
  categories = ref<Category[]>([])
const minPriceDraft = ref(''),
  maxPriceDraft = ref(''),
  priceError = ref('')
const loading = ref(false),
  error = ref(''),
  total = ref(0),
  pages = ref(0),
  message = ref('')
const categoryError = ref(''),
  filtersOpen = ref(false)
const suggestions = ref<string[]>([])
let suggestTimer: ReturnType<typeof setTimeout> | undefined,
  suggestSequence = 0
function suggest() {
  clearTimeout(suggestTimer)
  const token = ++suggestSequence
  suggestions.value = []
  if (demoMode || !search.value.trim()) return
  suggestTimer = setTimeout(async () => {
    try {
      const result = await mallApi.suggest(search.value.trim())
      if (token === suggestSequence) suggestions.value = result || []
    } catch {
      /* Search remains available when suggestions fail. */
    }
  }, 200)
}
let sequence = 0,
  firstLoad = true
const textQuery = (key: string) =>
  typeof route.query[key] === 'string' ? (route.query[key] as string) : ''
const page = computed(() =>
  /^\d+$/.test(textQuery('page')) ? Math.max(1, Math.min(100000, Number(textQuery('page')))) : 1,
)
const sort = computed(() =>
  ['sales', 'price', 'newest'].includes(textQuery('sort')) ? textQuery('sort') : 'comprehensive',
)
const order = computed(() => (textQuery('order') === 'asc' ? 'asc' : 'desc'))
const chips = computed(() => {
  const result: { key: string; label: string }[] = []
  if (textQuery('q')) result.push({ key: 'q', label: `搜索：${textQuery('q')}` })
  if (textQuery('categoryId'))
    result.push({
      key: 'categoryId',
      label: `分类：${categories.value.find((c) => c.id === textQuery('categoryId'))?.name || textQuery('categoryId')}`,
    })
  if (textQuery('minPrice') || textQuery('maxPrice'))
    result.push({
      key: 'price',
      label: `价格：¥${textQuery('minPrice') || '0'} ～ ${textQuery('maxPrice') ? '¥' + textQuery('maxPrice') : '不限'}`,
    })
  if (textQuery('brandId')) result.push({ key: 'brandId', label: '已选品牌' })
  if (textQuery('inStock') === 'true') result.push({ key: 'inStock', label: '仅看有货' })
  for (const key of Object.keys(route.query))
    if (key.startsWith('attr:')) result.push({ key, label: `${key.slice(5)}：${textQuery(key)}` })
  if (sort.value !== 'comprehensive')
    result.push({
      key: 'sort',
      label:
        sort.value === 'price'
          ? `价格${order.value === 'asc' ? '从低到高' : '从高到低'}`
          : sort.value === 'sales'
            ? '销量优先'
            : '最新上架',
    })
  return result
})
async function loadCategories() {
  categoryError.value = ''
  try {
    categories.value = (await catalogApi.categories()).sort((a, b) => a.sort - b.sort)
  } catch {
    categoryError.value = '分类暂时无法加载'
  }
}
async function load() {
  const token = ++sequence
  loading.value = true
  error.value = ''
  message.value = ''
  search.value = textQuery('q')
  minPriceDraft.value = textQuery('minPrice')
  maxPriceDraft.value = textQuery('maxPrice')
  priceError.value = ''
  const range = priceRange(minPriceDraft.value, maxPriceDraft.value)
  if (range.error) {
    loading.value = false
    priceError.value = range.error
    error.value = '链接中的价格条件无效，请修改价格或清除筛选。'
    return
  }
  const attributes: Record<string, string> = {}
  for (const key of Object.keys(route.query))
    if (key.startsWith('attr:') && textQuery(key)) attributes[key.slice(5)] = textQuery(key)
  try {
    const query = {
      page: page.value,
      size: 12,
      keyword: search.value.trim(),
      categoryId: textQuery('categoryId'),
      brandId: textQuery('brandId'),
      minPrice: range.min,
      maxPrice: range.max,
      inStock: textQuery('inStock') === 'true',
      attributes,
      sort: sort.value,
      order: order.value,
    }
    const response = demoMode
      ? { results: await catalogApi.list(query), message: '' }
      : await mallApi.search(query)
    if (token !== sequence) return
    const result = response.results
    if (page.value > Math.max(1, result.pages)) {
      await router.replace({ query: { ...route.query, page: String(Math.max(1, result.pages)) } })
      return
    }
    products.value = result.list
    total.value = result.total
    pages.value = result.pages
    message.value = response.message
  } catch (e) {
    if (token === sequence) error.value = (e as Error).message
  } finally {
    if (token === sequence) {
      loading.value = false
      await nextTick()
      if (firstLoad) {
        firstLoad = false
        const y = catalogScroll.get(route.fullPath)
        if (y !== undefined) requestAnimationFrame(() => window.scrollTo(0, y))
      }
    }
  }
}
function filter(values: Record<string, string | undefined>) {
  const query: LocationQueryRaw = { ...route.query, ...values, page: undefined }
  for (const key of Object.keys(query)) if (query[key] === '') delete query[key]
  void router.push({ query })
}
function applyPriceRange() {
  const range = priceRange(minPriceDraft.value, maxPriceDraft.value)
  priceError.value = range.error
  if (!range.error) filter({ minPrice: range.min, maxPrice: range.max })
}
function remove(key: string) {
  if (key === 'price') filter({ minPrice: undefined, maxPrice: undefined })
  else if (key === 'sort') filter({ sort: undefined, order: undefined })
  else filter({ [key]: undefined })
}
function clear() {
  priceError.value = ''
  void router.push({ path: '/catalog' })
}
onBeforeRouteLeave(() => {
  catalogScroll.set(route.fullPath, window.scrollY)
})
onUnmounted(() => {
  sequence++
  suggestSequence++
  clearTimeout(suggestTimer)
})
void loadCategories()
watch(() => route.fullPath, load, { immediate: true })
</script>

<template>
  <div class="mall-page">
    <ShopHeader compact />
    <div class="catalog-title">
      <div>
        <h1>好物商城</h1>
        <p>按需挑选，轻松找到心仪好物</p>
      </div>
    </div>
    <form class="mall-search" role="search" @submit.prevent="filter({ q: search.trim() })">
      <Search :size="20" aria-hidden="true" /><input
        v-model="search"
        maxlength="100"
        aria-label="搜索商城商品"
        list="mall-suggestions"
        @input="suggest"
        placeholder="搜索商品名称、型号或关键词"
      /><button type="submit">搜索</button>
    </form>
    <datalist id="mall-suggestions">
      <option v-for="word in suggestions" :key="word" :value="word" />
    </datalist>
    <section class="catalog-filters" aria-label="商品筛选">
      <nav class="category-menu" aria-label="商品分类">
        <span class="filter-name">分类</span
        ><button
          :aria-pressed="!textQuery('categoryId')"
          @click="filter({ categoryId: undefined })"
        >
          全部商品</button
        ><button
          v-for="category in categories"
          :key="category.id"
          :aria-pressed="textQuery('categoryId') === category.id"
          @click="filter({ categoryId: category.id })"
        >
          {{ category.name }}
        </button>
      </nav>
      <p v-if="categoryError" role="alert">
        {{ categoryError }} <button class="text-action" @click="loadCategories">重试</button>
      </p>
      <div class="sort-row">
        <nav class="sort-menu" aria-label="商品排序">
          <button
            :aria-pressed="sort === 'comprehensive'"
            @click="filter({ sort: undefined, order: undefined })"
          >
            综合排序</button
          ><button
            :aria-pressed="sort === 'sales'"
            @click="filter({ sort: 'sales', order: 'desc' })"
          >
            销量优先 ↓</button
          ><button
            :aria-pressed="sort === 'price'"
            @click="
              filter({ sort: 'price', order: sort === 'price' && order === 'asc' ? 'desc' : 'asc' })
            "
          >
            价格 {{ sort === 'price' ? (order === 'asc' ? '↑ 从低到高' : '↓ 从高到低') : '↕' }}
          </button>
        </nav>
        <button
          class="mobile-filter"
          :aria-expanded="filtersOpen"
          aria-controls="price-filters"
          @click="filtersOpen = !filtersOpen"
        >
          <SlidersHorizontal :size="16" aria-hidden="true" />价格筛选
        </button>
      </div>
      <div id="price-filters" class="price-filters" :class="{ open: filtersOpen }">
        <form class="price-range" novalidate @submit.prevent="applyPriceRange">
          <span class="filter-name">价格</span
          ><label
            ><span class="sr-only">最低价</span
            ><input
              v-model="minPriceDraft"
              inputmode="decimal"
              placeholder="最低价 ¥"
              :aria-invalid="!!priceError"
              :aria-describedby="priceError ? 'price-error' : undefined"
              maxlength="15" /></label
          ><span aria-hidden="true">—</span
          ><label
            ><span class="sr-only">最高价</span
            ><input
              v-model="maxPriceDraft"
              inputmode="decimal"
              placeholder="最高价 ¥"
              :aria-invalid="!!priceError"
              :aria-describedby="priceError ? 'price-error' : undefined"
              maxlength="15" /></label
          ><button type="submit">确定</button>
        </form>
        <p v-if="priceError" id="price-error" class="filter-error" role="alert">{{ priceError }}</p>
      </div>
    </section>
    <div v-if="chips.length" class="selected-filters" aria-label="已选条件">
      <span>已选</span
      ><button
        v-for="chip in chips"
        :key="chip.key"
        :aria-label="`清除${chip.label}`"
        @click="remove(chip.key)"
      >
        {{ chip.label }}<X :size="13" aria-hidden="true" /></button
      ><button class="clear-filters" @click="clear">清除筛选</button>
    </div>
    <p v-if="message" class="mall-message" role="status">{{ message }}</p>
    <div class="result-heading">
      <h2>商品列表</h2>
      <span role="status">{{
        loading ? '正在查询…' : error ? '暂时无法获取商品' : `共 ${total} 件商品`
      }}</span>
    </div>
    <p v-if="loading" class="state" role="status">正在加载商品…</p>
    <div v-else-if="error" class="state" role="alert">
      {{ error }} <button class="text-action" @click="load">重新加载</button
      ><button v-if="chips.length" class="text-action" @click="clear">清除筛选</button>
    </div>
    <template v-else
      ><ProductGrid :products="products" />
      <div v-if="!total && chips.length" class="empty-reset">
        <button class="text-action" @click="clear">清除筛选，查看全部商品</button>
      </div>
      <nav v-if="pages > 1" class="catalog-pagination" aria-label="商品分页">
        <button
          :disabled="page <= 1"
          @click="router.push({ query: { ...route.query, page: String(page - 1) } })"
        >
          上一页</button
        ><span aria-current="page">{{ page }} / {{ pages }}</span
        ><button
          :disabled="page >= pages"
          @click="router.push({ query: { ...route.query, page: String(page + 1) } })"
        >
          下一页
        </button>
      </nav></template
    >
  </div>
</template>

<style scoped>
.mall-page {
  max-width: 1240px;
  margin: 0 auto;
  padding-bottom: 32px;
  color: #292929;
}
.catalog-title h1 {
  margin: 0;
  font-size: 26px;
  letter-spacing: 0.02em;
}
.catalog-title p {
  margin: 8px 0 20px;
  color: #666;
  font-size: 13px;
}
.mall-search {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 48px;
  padding: 5px 6px 5px 16px;
  background: #fff;
  border: 1px solid #ccc;
  border-radius: 7px;
  margin-bottom: 20px;
  color: #777;
}
.mall-search input {
  min-width: 0;
  flex: 1;
  height: 100%;
  background: transparent;
  border: 0;
  color: #292929;
}
.mall-search button {
  height: 36px;
  padding: 0 26px;
  border: 0;
  border-radius: 5px;
  background: #a53e2b;
  color: #fff;
}
.catalog-filters {
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  padding: 16px 20px;
}
.catalog-filters > p {
  font-size: 13px;
}
.category-menu {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee;
}
.filter-name {
  color: #666;
  font-size: 13px;
  margin-right: 12px;
}
.category-menu button,
.sort-menu button {
  min-height: 36px;
  padding: 7px 13px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: #444;
  font-size: 13px;
}
button[aria-pressed='true'] {
  color: #9b3524;
  background: #fbefea;
  border-color: #e3bfb3;
  font-weight: 600;
}
.sort-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding-top: 12px;
}
.sort-menu {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.mobile-filter {
  display: none;
}
.price-filters {
  margin-top: 12px;
}
.price-range {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #777;
}
.price-range label {
  min-width: 0;
}
.price-range input {
  width: 126px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid #d7d7d7;
  border-radius: 5px;
  font-size: 13px;
}
.price-range button {
  min-height: 36px;
  padding: 0 17px;
  border: 1px solid #a53e2b;
  border-radius: 5px;
  background: #fff;
  color: #a53e2b;
  font-size: 13px;
}
.filter-error {
  color: #a13223;
  font-size: 13px;
  margin-bottom: 0;
}
.selected-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  font-size: 12px;
  color: #666;
}
.selected-filters button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  max-width: 100%;
  overflow-wrap: anywhere;
  border: 1px solid #e4d2cb;
  border-radius: 4px;
  color: #8b3a2a;
  background: #fff;
}
.selected-filters .clear-filters {
  color: #666;
  border-color: transparent;
  background: transparent;
}
.result-heading {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin: 25px 0 15px;
}
.result-heading h2 {
  font-size: 18px;
  margin: 0;
}
.result-heading span {
  color: #666;
  font-size: 13px;
}
.text-action {
  border: 0;
  background: transparent;
  color: #a53e2b;
  text-decoration: underline;
  padding: 8px;
}
.mall-message {
  font-size: 13px;
  color: #82412d;
}
.empty-reset {
  text-align: center;
  margin: 12px;
}
@media (max-width: 620px) {
  .catalog-title h1 {
    font-size: 22px;
  }
  .catalog-filters {
    padding: 12px;
  }
  .category-menu {
    flex-wrap: nowrap;
    overflow-x: auto;
  }
  .category-menu > * {
    flex-shrink: 0;
  }
  .category-menu .filter-name {
    display: none;
  }
  .sort-row {
    flex-wrap: wrap;
  }
  .sort-menu {
    gap: 2px;
  }
  .sort-menu button {
    padding: 7px 9px;
    font-size: 12px;
  }
  .mobile-filter {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px;
    border: 1px solid #ddd;
    border-radius: 5px;
    color: #555;
    background: #fff;
    font-size: 12px;
  }
  .price-filters {
    display: none;
  }
  .price-filters.open,
  .price-filters:has(.filter-error) {
    display: block;
  }
  .price-range {
    gap: 7px;
  }
  .price-range .filter-name {
    display: none;
  }
  .price-range label {
    flex: 1;
  }
  .price-range input {
    width: 100%;
  }
  .price-range button {
    padding: 0 12px;
  }
  .mall-search button {
    padding: 0 20px;
  }
}
</style>
