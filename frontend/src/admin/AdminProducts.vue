<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { Plus, Search, RefreshCw } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import ProductImage from '../components/ProductImage.vue'
import {
  productsApi,
  type AdminProduct,
  type ProductInput,
  type ProductOptions,
  type ProductStatus,
} from '../api/products'
import { ApiError } from '../api/http'

const labels: Record<ProductStatus, string> = {
  draft: '草稿',
  on_sale: '已上架',
  off_sale: '已下架',
  sold_out: '已售罄',
  presale: '预售',
  scheduled: '待上架',
}
const rows = ref<AdminProduct[]>([]),
  total = ref(0),
  pages = ref(0)
const options = ref<ProductOptions>({ categories: [], brands: [] }),
  optionsReady = ref(false)
const route = useRoute()
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  categoryId: typeof route.query.categoryId === 'string' ? route.query.categoryId : '',
  status: '',
  sort: 'createTime',
  order: 'desc',
})
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  notice = ref('')
const mode = ref<'create' | 'edit' | 'detail' | null>(null),
  selected = ref<AdminProduct | null>(null)
const editor = ref<HTMLElement>(),
  images = ref('')
const categoryEditor = ref(false)
const categoryForm = reactive({
  name: '',
  parentId: '0',
  sort: 0,
  status: 'active' as 'active' | 'inactive',
})
const blank = (): ProductInput => ({
  categoryId: '',
  brandId: null,
  name: '',
  subtitle: '',
  coverUrl: '',
  price: 0.01,
  originalPrice: null,
  description: '',
  detailImages: [],
})
const form = reactive<ProductInput>(blank())
const readonly = computed(() => mode.value === 'detail')
let version = 0
function explain(e: unknown) {
  return e instanceof ApiError && e.traceId
    ? `${e.message}（请求编号：${e.traceId}）`
    : e instanceof Error
      ? e.message
      : '操作失败，请重试'
}
function money(value: number | null) {
  return value == null ? '—' : `¥${Number(value).toFixed(2)}`
}
function categoryLabel(id: string) {
  const path: string[] = [],
    seen = new Set<string>()
  let current = options.value.categories.find((c) => c.id === id)
  while (current && !seen.has(current.id)) {
    seen.add(current.id)
    path.unshift(current.name)
    current = options.value.categories.find((c) => c.id === current!.parentId)
  }
  return path.join(' / ')
}
async function load() {
  const requestVersion = ++version
  loading.value = true
  error.value = ''
  try {
    const result = await productsApi.list({ ...query, keyword: query.keyword.trim() })
    if (requestVersion !== version) return
    rows.value = result.list
    total.value = result.total
    pages.value = result.pages
    if (query.page > Math.max(1, result.pages)) query.page = Math.max(1, result.pages)
  } catch (e) {
    if (requestVersion === version) {
      error.value = explain(e)
      rows.value = []
      total.value = 0
      pages.value = 0
    }
  } finally {
    if (requestVersion === version) loading.value = false
  }
}
async function loadOptions() {
  optionsReady.value = false
  try {
    options.value = await productsApi.options()
    optionsReady.value = true
  } catch (e) {
    error.value = explain(e)
  }
}
async function refresh() {
  await Promise.all([load(), loadOptions()])
}
function search() {
  if (query.page !== 1) query.page = 1
  else void load()
}
watch(() => query.page, load)
watch(
  () => route.query.categoryId,
  (value) => {
    query.categoryId = typeof value === 'string' ? value : ''
    if (query.page === 1) void load()
    else query.page = 1
  },
)
onMounted(refresh)
onUnmounted(() => {
  version++
})
async function open(row?: AdminProduct, detail = false) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const product = row ? await productsApi.detail(row.id) : null
    selected.value = product
    Object.assign(
      form,
      product
        ? {
            categoryId: product.categoryId,
            brandId: product.brandId,
            name: product.name,
            subtitle: product.subtitle,
            coverUrl: product.coverUrl,
            price: product.price,
            originalPrice: product.originalPrice,
            description: product.description,
            detailImages: product.detailImages,
          }
        : blank(),
    )
    images.value = form.detailImages.join('\n')
    mode.value = product ? (detail ? 'detail' : 'edit') : 'create'
    requestAnimationFrame(() =>
      editor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
    )
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function save() {
  if (busy.value || readonly.value) return
  error.value = ''
  notice.value = ''
  const detailImages = images.value
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter(Boolean)
  const imageUrl = /^(https?:\/\/|\/(?!\/))[^\r\n]*$/
  if (!form.name.trim()) {
    error.value = '请输入商品名称'
    return
  }
  if (
    form.originalPrice != null &&
    String(form.originalPrice) !== '' &&
    Number(form.originalPrice) < Number(form.price)
  ) {
    error.value = '原价不能低于销售价'
    return
  }
  if (
    detailImages.length > 20 ||
    detailImages.some((url) => url.length > 512 || !imageUrl.test(url)) ||
    (form.coverUrl?.trim() && !imageUrl.test(form.coverUrl.trim()))
  ) {
    error.value = '图片须使用 http(s) 地址或站内绝对路径；详情图最多 20 张，每个地址不超过 512 字符'
    return
  }
  busy.value = true
  try {
    await productsApi.save(selected.value?.id || null, {
      categoryId: form.categoryId,
      brandId: form.brandId || null,
      name: form.name.trim(),
      subtitle: form.subtitle?.trim() || null,
      coverUrl: form.coverUrl?.trim() || null,
      price: Number(form.price),
      originalPrice:
        form.originalPrice == null || String(form.originalPrice) === ''
          ? null
          : Number(form.originalPrice),
      description: form.description?.trim() || null,
      detailImages,
    })
    const created = mode.value === 'create'
    mode.value = null
    if (created && query.page !== 1) query.page = 1
    else await load()
    notice.value = created ? '商品已创建为草稿，请完成库存配置后上架。' : '商品保存成功'
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function createCategory() {
  if (busy.value) return
  if (!categoryForm.name.trim()) {
    error.value = '请输入分类名称'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const category = await productsApi.createCategory({
      ...categoryForm,
      name: categoryForm.name.trim(),
    })
    await loadOptions()
    form.categoryId = category.id
    categoryEditor.value = false
    categoryForm.name = ''
    notice.value = '分类已创建并选中'
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function changeStatus(row: AdminProduct, status: ProductStatus) {
  if (
    busy.value ||
    !window.confirm(`确认将“${row.name}”${status === 'on_sale' ? '上架' : '下架'}？`)
  )
    return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await productsApi.status(row.id, status)
    mode.value = null
    await load()
    notice.value = `商品${labels[status]}`
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
function canDelete(row: AdminProduct) {
  return ['draft', 'off_sale'].includes(row.status) && Number(row.stock) === 0
}
async function remove(row: AdminProduct) {
  if (
    busy.value ||
    !window.confirm(`确认删除“${row.name}”？删除后将从商品管理和商城移除，历史订单保留。`)
  )
    return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await productsApi.remove(row.id)
    mode.value = null
    await load()
    notice.value = '商品已删除'
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="admin-page products-page">
    <header class="page-heading">
      <div>
        <span class="page-kicker">PRODUCTS</span>
        <h1>商品管理</h1>
        <p>维护商品资料、销售价格与上下架状态。库存由库存管理维护，新增商品默认保存为草稿。</p>
      </div>
      <button class="primary-button" :disabled="busy || !optionsReady" @click="open()">
        <Plus :size="16" />新增商品
      </button>
    </header>
    <p v-if="error" class="demo-banner" role="alert">
      {{ error }}<button class="text-action" :disabled="busy" @click="refresh">重新加载</button>
    </p>
    <p v-if="notice" class="demo-banner" role="status">{{ notice }}</p>
    <section
      v-if="mode"
      ref="editor"
      class="panel product-editor"
      aria-labelledby="product-editor-title"
    >
      <div class="panel-heading">
        <h2 id="product-editor-title">
          {{ mode === 'create' ? '新增商品' : readonly ? '商品详情' : '编辑商品' }}
        </h2>
        <button class="secondary-button" :disabled="busy" @click="mode = null">关闭</button>
      </div>
      <p v-if="selected" class="product-meta">
        编号 {{ selected.id }} · {{ labels[selected.status] }} · 可用库存 {{ selected.stock }} ·
        销量 {{ selected.sales }}<br />创建 {{ selected.createTime }} · 更新
        {{ selected.updateTime }}
      </p>
      <div v-if="!readonly" class="category-tools">
        <button
          class="text-action"
          :disabled="busy || !optionsReady"
          @click="categoryEditor = !categoryEditor"
        >
          {{ categoryEditor ? '收起分类表单' : '新增商品分类' }}</button
        ><span v-if="!options.categories.length">请先创建一个商品分类。</span>
      </div>
      <form
        v-if="categoryEditor && !readonly"
        class="category-form"
        @submit.prevent="createCategory"
      >
        <label
          >分类名称<input
            v-model="categoryForm.name"
            required
            maxlength="64"
            :disabled="busy" /></label
        ><label
          >父分类<select v-model="categoryForm.parentId" :disabled="busy">
            <option value="0">顶级分类</option>
            <option v-for="c in options.categories" :key="c.id" :value="c.id">
              {{ categoryLabel(c.id) }}
            </option>
          </select></label
        ><label
          >排序<input
            v-model.number="categoryForm.sort"
            required
            type="number"
            min="0"
            max="2147483647"
            step="1"
            :disabled="busy" /></label
        ><label
          >状态<select v-model="categoryForm.status" :disabled="busy">
            <option value="active">启用</option>
            <option value="inactive">停用</option>
          </select></label
        ><button class="secondary-button" :disabled="busy">保存分类</button>
      </form>
      <form @submit.prevent="save">
        <fieldset :disabled="busy || readonly" class="product-fields">
          <label>商品名称 *<input v-model="form.name" required maxlength="200" /></label>
          <label
            >商品分类 *<select v-model="form.categoryId" required>
              <option disabled value="">请选择分类</option>
              <option v-for="c in options.categories" :key="c.id" :value="c.id">
                {{ categoryLabel(c.id) }}{{ c.status !== 'active' ? '（已停用）' : '' }}
              </option>
              <option
                v-if="form.categoryId && !options.categories.some((c) => c.id === form.categoryId)"
                :value="form.categoryId"
              >
                原分类已不可用，请重新选择
              </option>
            </select></label
          >
          <label
            >品牌<select v-model="form.brandId">
              <option :value="null">不设置品牌</option>
              <option v-for="b in options.brands" :key="b.id" :value="b.id">
                {{ b.name }}{{ b.status !== 'active' ? '（已停用）' : '' }}
              </option>
              <option
                v-if="form.brandId && !options.brands.some((b) => b.id === form.brandId)"
                :value="form.brandId"
              >
                原品牌已不可用，请重新选择
              </option>
            </select></label
          >
          <label
            >销售价（元）*<input
              v-model.number="form.price"
              type="number"
              required
              min="0.01"
              max="99999999.99"
              step="0.01"
          /></label>
          <label
            >原价（元，可留空）<input
              v-model.number="form.originalPrice"
              type="number"
              min="0"
              max="99999999.99"
              step="0.01"
          /></label>
          <label>副标题<input v-model="form.subtitle" maxlength="500" /></label>
          <label class="wide"
            >封面图片地址<input
              v-model="form.coverUrl"
              maxlength="512"
              placeholder="https://… 或 /images/…"
          /></label>
          <div class="cover-preview">
            <ProductImage :src="form.coverUrl" :alt="form.name || '商品封面预览'" kind="stove" />
          </div>
          <label class="wide"
            >商品描述<textarea
              v-model="form.description"
              maxlength="16000"
              rows="5"
              placeholder="商品介绍、规格、使用说明；按纯文本展示"
            />
          </label>
          <label class="wide"
            >详情图片地址<textarea
              v-model="images"
              rows="4"
              placeholder="每行一个图片地址，最多 20 张"
            />
          </label>
        </fieldset>
        <div class="editor-actions">
          <button v-if="!readonly" class="primary-button" :disabled="busy || !optionsReady">
            {{ busy ? '保存中…' : '保存商品' }}</button
          ><button
            v-if="readonly"
            type="button"
            class="primary-button"
            :disabled="busy || !optionsReady"
            @click="mode = 'edit'"
          >
            编辑商品</button
          ><button type="button" class="secondary-button" :disabled="busy" @click="mode = null">
            {{ readonly ? '返回列表' : '取消' }}
          </button>
        </div>
      </form>
    </section>
    <section class="panel list-panel" :aria-busy="loading">
      <form class="list-toolbar product-filters" @submit.prevent="search">
        <label class="table-search"
          ><Search :size="16" /><input
            v-model="query.keyword"
            maxlength="200"
            type="search"
            aria-label="商品名称"
            placeholder="搜索商品名称"
        /></label>
        <select v-model="query.categoryId" aria-label="商品分类">
          <option value="">全部分类</option>
          <option v-for="c in options.categories" :key="c.id" :value="c.id">
            {{ categoryLabel(c.id) }}
          </option>
        </select>
        <select v-model="query.status" aria-label="商品状态">
          <option value="">全部状态</option>
          <option v-for="(label, state) in labels" :key="state" :value="state">{{ label }}</option>
        </select>
        <select v-model="query.sort" aria-label="排序字段">
          <option value="createTime">创建时间</option>
          <option value="price">销售价</option>
          <option value="sales">销量</option>
          <option value="id">商品编号</option>
        </select>
        <select v-model="query.order" aria-label="排序方向">
          <option value="desc">降序</option>
          <option value="asc">升序</option>
        </select>
        <button class="primary-button" :disabled="loading || busy">查询</button
        ><button
          type="button"
          class="secondary-button"
          :disabled="loading || busy"
          aria-label="刷新商品和选项"
          @click="refresh"
        >
          <RefreshCw :size="16" />
        </button>
      </form>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>商品</th>
              <th>分类 / 品牌</th>
              <th>销售价 / 原价</th>
              <th>可用库存 / 销量</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>
                <div class="product-cell">
                  <div class="product-thumb">
                    <ProductImage :src="row.coverUrl" :alt="row.name" kind="stove" />
                  </div>
                  <div>
                    <strong>{{ row.name }}</strong
                    ><small>{{ row.subtitle || '无副标题' }}</small
                    ><small>编号 {{ row.id }}</small>
                  </div>
                </div>
              </td>
              <td>
                {{ row.category || '分类不可用'
                }}<small>{{
                  options.brands.find((b) => b.id === row.brandId)?.name ||
                  (row.brandId ? '品牌不可用' : '无品牌')
                }}</small>
              </td>
              <td>
                {{ money(row.price) }}<small>{{ money(row.originalPrice) }}</small>
              </td>
              <td>{{ row.stock }} / {{ row.sales }}</td>
              <td>
                <span class="status-tag" :class="`status-${row.status}`">{{
                  labels[row.status] || row.status
                }}</span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-action" :disabled="busy || loading" @click="open(row, true)">
                    详情</button
                  ><button
                    class="text-action"
                    :disabled="busy || loading || !optionsReady"
                    @click="open(row)"
                  >
                    编辑
                  </button>
                  <button
                    v-if="row.status !== 'on_sale'"
                    class="text-action"
                    :disabled="busy || loading"
                    @click="changeStatus(row, 'on_sale')"
                  >
                    上架</button
                  ><button
                    v-if="!['draft', 'off_sale'].includes(row.status)"
                    class="text-action"
                    :disabled="busy || loading"
                    @click="changeStatus(row, 'off_sale')"
                  >
                    下架
                  </button>
                  <button
                    class="text-action danger"
                    :disabled="busy || loading || !canDelete(row)"
                    title="须先下架，且实际库存和订单预占均为零；最终以服务端检查为准"
                    @click="remove(row)"
                  >
                    删除
                  </button>
                  <RouterLink class="text-action" to="/admin/inventory">库存管理</RouterLink>
                </div>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6" class="empty-state">
                {{
                  loading
                    ? '正在加载商品…'
                    : error
                      ? '加载失败，请重试'
                      : '暂无匹配商品，可点击“新增商品”创建'
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="pagination">
        <span>共 {{ total }} 件商品</span>
        <div>
          <select v-model.number="query.size" aria-label="每页数量" @change="search">
            <option :value="10">10 条 / 页</option>
            <option :value="20">20 条 / 页</option>
            <option :value="50">50 条 / 页</option></select
          ><button :disabled="loading || busy || query.page <= 1" @click="query.page--">
            上一页</button
          ><span>{{ query.page }} / {{ Math.max(1, pages) }}</span
          ><button :disabled="loading || busy || query.page >= pages" @click="query.page++">
            下一页
          </button>
        </div>
      </footer>
    </section>
    <p class="product-help">
      删除采用逻辑删除并保留历史订单；有库存或订单预占时请先通过库存及订单流程处理。上架后无可用库存的商品在商城显示为售罄。
    </p>
  </div>
</template>

<style scoped>
.category-tools {
  display: flex;
  gap: 16px;
  margin-top: 14px;
  align-items: center;
}
.category-form {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
  padding: 16px;
  background: #f5f7f5;
  border-radius: 6px;
  margin-top: 12px;
}
.category-form label {
  display: grid;
  gap: 6px;
}
.category-form input,
.category-form select {
  padding: 8px;
  border: 1px solid #dce4df;
  border-radius: 6px;
  max-width: 100%;
}
.product-editor {
  padding: 24px;
  margin-bottom: 24px;
  scroll-margin-top: 24px;
}
.product-fields {
  border: 0;
  padding: 0;
  margin: 20px 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
.product-fields label {
  display: grid;
  gap: 8px;
}
.product-fields input,
.product-fields select,
.product-fields textarea,
.product-filters > select {
  border: 1px solid #dce4df;
  border-radius: 6px;
  padding: 10px;
  background: #fff;
  min-width: 0;
  max-width: 100%;
  font: inherit;
}
.product-fields textarea {
  resize: vertical;
}
.wide {
  grid-column: 1/-1;
}
.product-meta,
.product-help {
  color: #75837b;
  line-height: 1.8;
}
.editor-actions,
.row-actions {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  align-items: center;
}
.product-filters {
  flex-wrap: wrap;
}
.product-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 220px;
  max-width: 390px;
  white-space: normal;
  overflow-wrap: anywhere;
}
.product-thumb {
  width: 58px;
  height: 58px;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 6px;
  background: #f5f7f5;
}
.cover-preview {
  width: 140px;
  height: 140px;
  overflow: hidden;
  border-radius: 8px;
  background: #f5f7f5;
}
.product-thumb :deep(img),
.product-thumb :deep(svg),
.cover-preview :deep(img),
.cover-preview :deep(svg) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
td small {
  display: block;
  color: #75837b;
  margin-top: 5px;
}
.row-actions {
  min-width: 190px;
}
.danger {
  color: #b94343;
}
.row-actions button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.status-on_sale {
  background: #eaf4ed;
  color: #257251;
}
.status-off_sale,
.status-draft {
  background: #f0f2f0;
  color: #75837b;
}
@media (max-width: 700px) {
  .product-fields {
    grid-template-columns: 1fr;
  }
  .product-editor {
    padding: 16px;
  }
  .product-filters > * {
    max-width: 100%;
  }
}
</style>
