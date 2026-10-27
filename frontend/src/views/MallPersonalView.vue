<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { mallApi } from '../api/mall'
import type { PersonalEntry } from '../api/mall'
import ProductImage from '../components/ProductImage.vue'

const route = useRoute()
const router = useRouter()
const type = computed(() => (route.path === '/favorites' ? ('favorites' as const) : ('history' as const)))
const title = computed(() => (type.value === 'favorites' ? '我的收藏' : '浏览足迹'))
const entries = ref<PersonalEntry[]>([])
const loading = ref(false)
const error = ref('')
const pages = ref(0)
const total = ref(0)
const busy = ref(false)
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
let sequence = 0

async function load() {
  const token = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await mallApi.personal(type.value, page.value)
    if (token !== sequence) return
    entries.value = result.list
    pages.value = result.pages
    total.value = result.total
    if (!entries.value.length && page.value > 1)
      void router.replace({ query: { page: String(page.value - 1) } })
  } catch (e) {
    if (token === sequence) error.value = (e as Error).message
  } finally {
    if (token === sequence) loading.value = false
  }
}

async function remove(id: string) {
  busy.value = true
  try {
    await mallApi.removePersonal(type.value, id)
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}

async function clear() {
  busy.value = true
  try {
    await mallApi.clearHistory()
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}

watch(() => route.fullPath, load, { immediate: true })
</script>

<template>
  <header class="page-heading personal-heading">
    <div>
      <h1>{{ title }}</h1>
      <p>{{ total }} 件商品 · 最近{{ type === 'favorites' ? '收藏' : '浏览' }}在前</p>
    </div>
    <button v-if="type === 'history' && total" class="button secondary" :disabled="busy" @click="clear">
      清空足迹
    </button>
  </header>

  <p v-if="loading" class="state" role="status">正在加载...</p>
  <p v-else-if="error" class="state" role="alert">{{ error }} <button @click="load">重试</button></p>
  <template v-else>
    <p v-if="!entries.length" class="state">
      {{ type === 'favorites' ? '还没有收藏商品' : '暂无浏览足迹' }}
      <RouterLink to="/catalog">去逛逛</RouterLink>
    </p>
    <div v-else class="personal-grid">
      <article v-for="entry in entries" :key="entry.productId">
        <RouterLink v-if="entry.product" class="product-link" :to="`/products/${entry.productId}`">
          <ProductImage
            :src="entry.product.coverUrl"
            :alt="entry.product.name"
            :kind="entry.productId"
          />
          <h2>{{ entry.product.name }}</h2>
          <p class="product-price">¥{{ entry.product.price.toFixed(2) }}</p>
          <p class="product-status">{{ entry.product.statusMessage }}</p>
        </RouterLink>
        <p v-else class="unavailable">商品已失效</p>
        <small>{{ entry.time }}</small>
        <button class="text-action" :disabled="busy" @click="remove(entry.productId)">
          {{ type === 'favorites' ? '取消收藏' : '删除足迹' }}
        </button>
      </article>
    </div>
    <nav v-if="pages > 1" class="catalog-pagination" aria-label="分页">
      <button :disabled="page <= 1" @click="router.push({ query: { page: String(page - 1) } })">
        上一页
      </button>
      <span>{{ page }} / {{ pages }}</span>
      <button :disabled="page >= pages" @click="router.push({ query: { page: String(page + 1) } })">
        下一页
      </button>
    </nav>
  </template>
</template>

<style scoped>
.personal-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 1rem;
}

.personal-heading p {
  margin-bottom: 0;
}

.personal-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  gap: 1rem;
}

article {
  min-width: 0;
  padding: 1rem;
  border: 1px solid #e6e5e1;
  border-radius: 6px;
}

.product-link {
  color: inherit;
  text-decoration: none;
}

article :deep(img) {
  width: 100%;
  aspect-ratio: 1;
  object-fit: contain;
  background: #f7f7f4;
}

article h2 {
  font-size: 1rem;
  line-height: 1.4;
}

.product-price {
  font-weight: 700;
  color: #a3482d;
}

.product-status,
.unavailable {
  color: #686b66;
}

article small {
  display: block;
  color: #686b66;
}

.text-action {
  margin-top: 0.75rem;
  padding: 0;
  border: 0;
  color: #a3482d;
  background: none;
  cursor: pointer;
}

.text-action:disabled {
  opacity: 0.55;
  cursor: wait;
}

@media (max-width: 520px) {
  .personal-heading {
    align-items: start;
    flex-direction: column;
  }
}
</style>
