<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductGrid from '../components/ProductGrid.vue'
import { catalogApi } from '../api/catalog'
import type { Category, Product } from '../api/types'
const route = useRoute(), router = useRouter()
const search = ref(''), categories = ref<Category[]>([]), products = ref<Product[]>([])
const loading = ref(false), error = ref(''), categoryError = ref(''), total = ref(0), pages = ref(0)
let sequence = 0
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const category = computed(() => String(route.query.categoryId || ''))
const sorting = computed(() => `${route.query.sort || 'createTime'}:${route.query.order || 'desc'}`)
async function loadCategories() { categoryError.value = ''; try { categories.value = await catalogApi.categories() } catch(e) { categoryError.value = (e as Error).message } }
async function load() { const token = ++sequence; loading.value = true; error.value = ''; search.value = String(route.query.q || ''); try { const result = await catalogApi.list({ page: page.value, size: 12, keyword: search.value.trim(), categoryId: category.value, sort: String(route.query.sort || 'createTime'), order: String(route.query.order || 'desc') }); if (token === sequence) { products.value = result.list; total.value = result.total; pages.value = result.pages } } catch(e) { if (token === sequence) error.value = (e as Error).message } finally { if (token === sequence) loading.value = false } }
function filter(values: Record<string, string>) { void router.push({ query: { ...route.query, ...values, page: '1' } }) }
function sort(event: Event) { const [field, order] = (event.target as HTMLSelectElement).value.split(':'); filter({ sort: field!, order: order! }) }
function categoryLabel(item: Category) { const names = [item.name], seen = new Set([item.id]); let parent = categories.value.find(c => c.id === item.parentId); while (parent && !seen.has(parent.id)) { names.unshift(parent.name); seen.add(parent.id); parent = categories.value.find(c => c.id === parent!.parentId) } return names.join(' / ') }
watch(() => route.fullPath, load, { immediate: true })
onMounted(loadCategories)
</script>
<template>
  <header class="page-heading"><span class="eyebrow">GOOD THINGS FOR HOME</span><h1>好物商城</h1><p>为家的每一份温暖，选一份可靠的品质。</p></header>
  <form class="searchbar" @submit.prevent="filter({ q: search.trim() })"><input v-model="search" maxlength="100" aria-label="搜索商城商品" placeholder="搜索你需要的安心好物" /><button class="button secondary" type="submit">搜索</button></form>
  <div class="catalog-toolbar"><label>分类 <select :value="category" @change="filter({ categoryId: ($event.target as HTMLSelectElement).value })"><option value="">全部分类</option><option v-for="item in categories" :key="item.id" :value="item.id">{{ categoryLabel(item) }}</option></select></label><label>排序 <select :value="sorting" @change="sort"><option value="createTime:desc">最新上架</option><option value="sales:desc">销量优先</option><option value="price:asc">价格从低到高</option><option value="price:desc">价格从高到低</option></select></label></div>
  <p v-if="categoryError" role="alert">分类加载失败：{{ categoryError }} <button @click="loadCategories">重试</button></p>
  <p v-if="loading" class="state" role="status">正在加载商品...</p><div v-else-if="error" class="state" role="alert">{{ error }} <button @click="load">重试</button></div>
  <template v-else><p>共 {{ total }} 件商品</p><ProductGrid :products="products" /><nav class="catalog-pagination" aria-label="商品分页"><button :disabled="page <= 1" @click="router.push({ query: { ...route.query, page: String(page - 1) } })">上一页</button><span>第 {{ page }} / {{ Math.max(1, pages) }} 页</span><button :disabled="page >= pages" @click="router.push({ query: { ...route.query, page: String(page + 1) } })">下一页</button></nav></template>
</template>
