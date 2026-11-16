<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  Plus,
  Search,
  RefreshCw,
  Package,
  ShoppingBag,
  FilePenLine,
  Boxes,
  CheckCircle2,
  Info,
  ArrowLeft,
} from 'lucide-vue-next'
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
import { productImageUrl } from '../api/images'

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
const editor = ref<HTMLElement>()
const uploadStatus = ref('')
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
  brandName: '',
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
const summary = computed(() => ({
  onSale: rows.value.filter((row) => row.status === 'on_sale').length,
  draft: rows.value.filter((row) => row.status === 'draft').length,
  stock: rows.value.reduce((sum, row) => sum + Number(row.stock || 0), 0),
}))
function resetFilters() {
  Object.assign(query, {
    keyword: '',
    categoryId: '',
    status: '',
    sort: 'createTime',
    order: 'desc',
  })
  search()
}
function filterStatus(status: string) {
  query.status = status
  search()
}
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
            brandName:
              product.brandName ??
              options.value.brands.find((b) => b.id === product.brandId)?.name ??
              '',
            name: product.name,
            subtitle: product.subtitle,
            coverUrl: product.coverUrl,
            price: product.price,
            originalPrice: product.originalPrice,
            description: product.description,
            detailImages: [...product.detailImages],
          }
        : blank(),
    )
    mode.value = product ? (detail ? 'detail' : 'edit') : 'create'
    categoryEditor.value = false
    requestAnimationFrame(() =>
      editor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
    )
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function uploadImages(event: Event, kind: 'cover' | 'detail') {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length || busy.value || readonly.value) return
  error.value = ''
  notice.value = ''
  if (kind === 'detail' && form.detailImages.length + files.length > 20) {
    error.value = '详情图片最多 20 张，请移除部分图片后再上传'
    return
  }
  if (files.some((file) => file.size === 0 || file.size > 10 * 1024 * 1024)) {
    error.value = '请选择非空图片，单张图片不能超过 10MB'
    return
  }
  if (
    files.some((file) =>
      file.type
        ? !['image/jpeg', 'image/png', 'image/gif'].includes(file.type)
        : !/\.(jpe?g|png|gif)$/i.test(file.name),
    )
  ) {
    error.value = '请选择 JPG、PNG 或 GIF 图片'
    return
  }
  busy.value = true
  try {
    for (const [index, file] of files.entries()) {
      uploadStatus.value = `正在上传图片 ${index + 1}/${files.length}…`
      const image = await productsApi.uploadImage(file)
      if (kind === 'cover') form.coverUrl = image.url
      else form.detailImages.push(image.url)
    }
  } catch (e) {
    error.value = `图片上传失败：${explain(e)}。已上传的图片已保留，可重新选择未完成的图片。`
  } finally {
    uploadStatus.value = ''
    busy.value = false
  }
}
function removeDetailImage(index: number) {
  if (!busy.value && !readonly.value) form.detailImages.splice(index, 1)
}
function moveDetailImage(index: number, offset: number) {
  if (busy.value || readonly.value) return
  const target = index + offset
  if (target < 0 || target >= form.detailImages.length) return
  const image = form.detailImages.splice(index, 1)[0]!
  form.detailImages.splice(target, 0, image)
}
async function save() {
  if (busy.value || readonly.value) return
  error.value = ''
  notice.value = ''
  const detailImages = [...form.detailImages]
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
    error.value = '图片信息无效，请重新上传；详情图片最多 20 张'
    return
  }
  busy.value = true
  try {
    const saved = await productsApi.save(selected.value?.id || null, {
      categoryId: form.categoryId,
      brandId: null,
      brandName: form.brandName?.trim() || null,
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
    if ((saved.brandName?.trim() || '') !== (form.brandName?.trim() || '')) {
      throw new Error(
        '商品接口返回的品牌与填写内容不一致，请确认后端已更新至支持品牌名称的版本，再重新保存。',
      )
    }
    const created = mode.value === 'create'
    mode.value = null
    if (created) {
      // 回到完整列表，避免只展示新商品的分类或草稿状态而隐藏已有商品。
      query.keyword = ''
      query.categoryId = ''
      query.status = ''
      query.sort = 'createTime'
      query.order = 'desc'
    }
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
    <p v-if="error && !mode" class="product-alert" role="alert">
      {{ error }}<button class="text-action" :disabled="busy" @click="refresh">重新加载</button>
    </p>
    <p v-if="notice" class="product-notice" role="status">
      <CheckCircle2 :size="17" />{{ notice }}
    </p>
    <section v-if="!mode" class="product-overview" aria-label="商品概览">
      <article>
        <div>
          <span>商品总数</span><strong>{{ total }}</strong
          ><small>当前筛选结果</small>
        </div>
        <span class="overview-icon"><Package :size="22" /></span>
      </article>
      <article>
        <div>
          <span>本页在售商品</span><strong>{{ summary.onSale }}</strong
          ><small>已上架，可在商城展示</small>
        </div>
        <span class="overview-icon mint"><ShoppingBag :size="22" /></span>
      </article>
      <article>
        <div>
          <span>本页草稿</span><strong>{{ summary.draft }}</strong
          ><small>完善资料后即可上架</small>
        </div>
        <span class="overview-icon amber"><FilePenLine :size="22" /></span>
      </article>
      <article>
        <div>
          <span>本页可用库存</span><strong>{{ summary.stock }}</strong
          ><small>库存数量由库存管理维护</small>
        </div>
        <span class="overview-icon blue"><Boxes :size="22" /></span>
      </article>
    </section>
    <section
      v-if="mode"
      ref="editor"
      class="panel product-editor"
      aria-labelledby="product-editor-title"
    >
      <div class="panel-heading">
        <div>
          <span class="editor-kicker">PRODUCT INFORMATION</span>
          <h2 id="product-editor-title">
            {{ mode === 'create' ? '新增商品' : readonly ? '商品详情' : '编辑商品' }}
          </h2>
          <p class="editor-subtitle">
            {{
              mode === 'create'
                ? '先完善商品资料，再配置库存并上架。'
                : '清晰准确的商品信息，让顾客更放心地选购。'
            }}
          </p>
        </div>
        <button class="secondary-button" :disabled="busy" @click="mode = null">
          <ArrowLeft :size="15" />返回列表
        </button>
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
      <p v-if="error" class="product-alert" role="alert">{{ error }}</p>
      <form
        @submit.prevent="save"
        @invalid.capture="error = '请检查并填写必填项，价格最多保留两位小数。'"
      >
        <fieldset :disabled="busy || readonly" class="product-fields">
          <div class="form-section-heading wide">
            <span>01</span>
            <div>
              <h3>基本信息</h3>
              <p>填写商品名称、分类和价格，带 * 为必填项。</p>
            </div>
          </div>
          <label
            >商品名称 *<input
              v-model="form.name"
              required
              maxlength="200"
              placeholder="请输入清晰、完整的商品名称"
          /></label>
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
            >品牌<input
              v-model="form.brandName"
              maxlength="100"
              placeholder="请输入品牌名称，可留空"
          /></label>
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
          <label
            >副标题<input
              v-model="form.subtitle"
              maxlength="500"
              placeholder="一句话介绍商品亮点或规格"
          /></label>
          <div class="form-section-heading wide">
            <span>02</span>
            <div>
              <h3>图片与商品介绍</h3>
              <p>使用清晰的图片与准确的描述，帮助顾客了解商品。</p>
            </div>
          </div>
          <label v-if="!readonly" class="wide image-upload"
            >商品封面图片<input
              type="file"
              accept="image/jpeg,image/png,image/gif"
              aria-label="上传商品封面图片"
              @change="uploadImages($event, 'cover')"
            /><small>选择一张 JPG、PNG 或 GIF 图片，单张不超过 10MB；重新选择可替换封面。</small>
          </label>
          <div class="cover-preview">
            <ProductImage :src="form.coverUrl" :alt="form.name || '商品封面预览'" kind="stove" />
          </div>
          <div class="cover-caption">
            <strong>封面预览</strong>
            <p>
              {{
                form.coverUrl
                  ? '建议使用主体清晰、背景简洁的商品图片。'
                  : '选择商品图片后可在此预览。'
              }}
            </p>
            <button
              v-if="form.coverUrl && !readonly"
              type="button"
              class="text-action"
              @click="form.coverUrl = null"
            >
              移除封面
            </button>
          </div>
          <label class="wide"
            >商品描述<textarea
              v-model="form.description"
              maxlength="16000"
              rows="5"
              placeholder="商品介绍、规格、使用说明；按纯文本展示"
            />
          </label>
          <div class="wide detail-images">
            <label v-if="!readonly" class="image-upload"
              >商品详情图片（{{ form.detailImages.length }}/20）<input
                type="file"
                accept="image/jpeg,image/png,image/gif"
                multiple
                aria-label="上传商品详情图片"
                :disabled="form.detailImages.length >= 20"
                @change="uploadImages($event, 'detail')"
              /><small
                >可一次选择多张，最多 20 张，单张不超过 10MB；按下方顺序展示，可调整或移除。</small
              >
            </label>
            <p v-if="!form.detailImages.length" class="image-empty">暂无详情图片</p>
            <div v-else class="detail-image-grid">
              <div
                v-for="(src, index) in form.detailImages"
                :key="`${src}-${index}`"
                class="detail-image-card"
              >
                <a
                  :href="productImageUrl(src)"
                  target="_blank"
                  rel="noopener noreferrer"
                  :aria-label="`查看详情图片 ${index + 1}`"
                >
                  <img
                    :src="productImageUrl(src)"
                    :alt="`商品详情图片 ${index + 1}`"
                    loading="lazy"
                  />
                </a>
                <span>详情图 {{ index + 1 }}</span>
                <div v-if="!readonly" class="image-actions">
                  <button
                    type="button"
                    class="text-action"
                    :disabled="index === 0"
                    @click="moveDetailImage(index, -1)"
                  >
                    前移
                  </button>
                  <button
                    type="button"
                    class="text-action"
                    :disabled="index === form.detailImages.length - 1"
                    @click="moveDetailImage(index, 1)"
                  >
                    后移
                  </button>
                  <button
                    type="button"
                    class="text-action"
                    :aria-label="`移除详情图片 ${index + 1}`"
                    @click="removeDetailImage(index)"
                  >
                    移除
                  </button>
                </div>
              </div>
            </div>
          </div>
        </fieldset>
        <p v-if="uploadStatus" class="upload-status" role="status">{{ uploadStatus }}</p>
        <div class="editor-actions">
          <button v-if="!readonly" class="primary-button" :disabled="busy || !optionsReady">
            {{ uploadStatus ? '图片上传中…' : busy ? '保存中…' : '保存商品' }}</button
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
    <section v-if="!mode" class="panel list-panel" :aria-busy="loading">
      <div class="product-status-tabs" aria-label="按商品状态筛选">
        <button
          type="button"
          :class="{ active: !query.status }"
          :aria-pressed="!query.status"
          :disabled="busy || loading"
          @click="filterStatus('')"
        >
          全部商品</button
        ><button
          v-for="(label, state) in labels"
          :key="state"
          type="button"
          :class="{ active: query.status === state }"
          :aria-pressed="query.status === state"
          :disabled="busy || loading"
          @click="filterStatus(state)"
        >
          {{ label }}
        </button>
      </div>
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
          class="reset-button"
          :disabled="loading || busy"
          @click="resetFilters"
        >
          重置</button
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
                    <button
                      class="product-name"
                      :disabled="busy || loading"
                      @click="open(row, true)"
                    >
                      {{ row.name }}</button
                    ><small>{{ row.subtitle || '无副标题' }}</small
                    ><small>编号 {{ row.id }}</small>
                  </div>
                </div>
              </td>
              <td>
                {{ row.category || '分类不可用'
                }}<small>{{
                  row.brandName ||
                  options.brands.find((b) => b.id === row.brandId)?.name ||
                  (row.brandId ? '品牌不可用' : '无品牌')
                }}</small>
              </td>
              <td>
                <strong class="product-price">{{ money(row.price) }}</strong
                ><small class="original-price">{{ money(row.originalPrice) }}</small>
              </td>
              <td>
                <span class="stock-pill" :class="{ low: Number(row.stock) <= 10 }">{{
                  row.stock
                }}</span
                ><small>销量 {{ row.sales }}</small>
              </td>
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
      <Info :size="16" />
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

.products-page {
  --product-green: #5c886a;
  --product-line: #e4ebdf;
}
.products-page .page-heading h1 {
  color: #304b38;
}
.products-page .page-heading p {
  color: #8b9889;
  line-height: 1.8;
  max-width: 650px;
}
.products-page .primary-button,
.products-page .secondary-button {
  border-radius: 9px;
  min-height: 39px;
  gap: 7px;
}
.products-page .primary-button {
  background: #5c886a;
  box-shadow: 0 3px 9px #5c886a18;
}
.products-page .primary-button:hover {
  background: #497656;
}
.products-page button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.product-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 24px 0;
}
.product-overview article {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 23px;
  border: 1px solid #e4ebdf;
  border-radius: 16px;
  background: linear-gradient(140deg, #fff, #f8fbf5);
  box-shadow: 0 4px 18px #294b3407;
}
.product-overview article > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.product-overview article span {
  font-size: 12px;
  color: #899780;
}
.product-overview strong {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.8px;
  color: #4d6942;
  margin: 13px 0 8px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.product-overview small {
  color: #a1ab94;
  font-size: 11px;
  line-height: 1.7;
}
.overview-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  background: #edf5e8;
  border-radius: 13px;
  color: #829e6b !important;
}
.overview-icon.mint {
  background: #eaf4ee;
  color: #7da18c !important;
}
.overview-icon.amber {
  background: #fbf1df;
  color: #bda06a !important;
}
.overview-icon.blue {
  background: #edf2f8;
  color: #91a2bb !important;
}
.products-page .list-panel {
  border: 1px solid var(--product-line);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 5px 24px #294b3407;
}
.product-status-tabs {
  display: flex;
  gap: 7px;
  overflow: auto;
  padding: 17px 22px;
  border-bottom: 1px solid #edf1e6;
}
.product-status-tabs button {
  background: none;
  border: 0;
  padding: 9px 15px;
  border-radius: 8px;
  color: #909d83;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.product-status-tabs button.active {
  color: #6a8954;
  background: #edf5e7;
  font-weight: 600;
}
.product-status-tabs button:hover {
  background: #f5f8f0;
}
.products-page .product-filters {
  padding: 21px 24px;
  gap: 12px;
  background: #fcfdfb;
  border-bottom: 1px solid #edf1e6;
}
.product-filters .table-search {
  background: #fff;
  border: 1px solid #e2e9da;
  border-radius: 9px;
  min-height: 40px;
  flex: 1;
  max-width: 340px;
  min-width: 180px;
}
.product-filters .table-search input {
  background: transparent;
}
.products-page .product-filters > select {
  border-color: #e2e9da;
  border-radius: 9px;
  background: #fff;
  color: #899879;
  height: 40px;
  padding: 8px 11px;
  font-size: 12px;
}
.reset-button {
  border: 0;
  background: none;
  padding: 10px 3px;
  color: #95a183;
  font: inherit;
  cursor: pointer;
}
.products-page input:focus-visible,
.products-page textarea:focus-visible,
.products-page select:focus-visible {
  outline: 2px solid #95b27f;
  outline-offset: 2px;
}
.products-page button:focus-visible,
.products-page a:focus-visible {
  outline: 2px solid #95b27f;
  outline-offset: 3px;
}
.products-page .admin-table {
  min-width: 1050px;
}
.products-page .admin-table th {
  background: #f5f8f0;
  color: #909d81;
  font-size: 11px;
  font-weight: 500;
  padding-top: 15px;
  padding-bottom: 15px;
}
.products-page .admin-table td {
  padding-top: 20px;
  padding-bottom: 20px;
  border-color: #eff3e8;
  color: #7a896b;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.products-page .admin-table tbody tr:hover {
  background: #fbfdf7;
}
.products-page td small {
  font-size: 10px;
  color: #a3ad96;
  margin-top: 7px;
}
.product-thumb {
  width: 66px;
  height: 66px;
  border-radius: 12px;
  background: #f5f8ef;
  border: 1px solid #eaf0e2;
  padding: 5px;
  box-sizing: border-box;
}
.product-name {
  border: 0;
  background: none;
  padding: 0;
  color: #5f794f;
  font-size: 13px;
  font-weight: 500;
  text-align: left;
  line-height: 1.7;
  cursor: pointer;
}
.product-name:hover {
  color: #477239;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.product-price {
  font-weight: 600;
  color: #5b7948;
  font-size: 14px;
}
.original-price {
  text-decoration: line-through;
}
.stock-pill {
  display: inline-block;
  min-width: 32px;
  padding: 4px 8px;
  border-radius: 7px;
  background: #edf5e7;
  color: #73925c;
  text-align: center;
}
.stock-pill.low {
  background: #fcf2e1;
  color: #b59a65;
}
.products-page .status-tag {
  border-radius: 20px;
  padding: 5px 10px;
  font-size: 11px;
  font-weight: 400;
  white-space: nowrap;
}
.status-on_sale {
  background: #edf5e8;
  color: #76965e;
}
.status-draft,
.status-off_sale {
  background: #f1f3ed;
  color: #9ba68b;
}
.status-sold_out {
  background: #fbf0e4;
  color: #b69b73;
}
.status-presale {
  background: #f2edf8;
  color: #a096b7;
}
.status-scheduled {
  background: #edf3f8;
  color: #91a4b8;
}
.products-page .row-actions {
  gap: 4px;
  min-width: 205px;
  max-width: 270px;
}
.products-page .row-actions .text-action {
  padding: 5px 7px;
  border-radius: 6px;
  color: #819a6d;
  font-size: 11px;
}
.products-page .row-actions .text-action:hover {
  background: #eff5e9;
}
.products-page .row-actions .danger {
  color: #b59a87;
}
.products-page .pagination {
  padding: 18px 24px;
  color: #99a48d;
  border-top: 1px solid #edf1e6;
}
.products-page .pagination select,
.products-page .pagination button {
  border-color: #e3ead9;
  border-radius: 7px;
  min-height: 32px;
}
.product-editor {
  border: 1px solid #e1e9da;
  border-radius: 18px;
  background: linear-gradient(160deg, #fff, #fdfefb);
  box-shadow: 0 7px 28px #294b3408;
  padding: 30px;
  max-width: 1080px;
  margin: 0 auto 24px;
}
.product-editor > .panel-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  border-bottom: 1px solid #edf1e5;
  padding: 0 0 23px;
  margin-bottom: 20px;
}
.editor-kicker {
  color: #a3b092;
  font-size: 10px;
  letter-spacing: 2px;
}
.product-editor h2 {
  color: #4e6942;
  font-size: 23px;
  margin: 9px 0;
}
.editor-subtitle {
  font-size: 12px;
  color: #99a68a;
  line-height: 1.8;
  margin: 0;
}
.product-meta {
  padding: 14px 18px;
  background: #f4f8ef;
  border: 1px solid #e7eddc;
  border-radius: 10px;
  font-size: 11px;
  color: #8b9b79;
  line-height: 1.9;
}
.category-tools {
  font-size: 12px;
  color: #a0ac8f;
}
.category-tools .text-action {
  color: #859e6e;
}
.category-form {
  background: #f7faf2;
  border: 1px solid #e5edd9;
  border-radius: 12px;
  padding: 20px;
  gap: 16px;
}
.category-form label {
  font-size: 12px;
  color: #8a9b77;
}
.category-form input,
.category-form select {
  border-color: #e1e9d6;
  border-radius: 8px;
  background: #fff;
  box-sizing: border-box;
}
.product-fields {
  gap: 23px;
  margin: 26px 0;
}
.product-fields label {
  color: #81966d;
  font-size: 12px;
  gap: 10px;
}
.products-page .product-fields input,
.products-page .product-fields select,
.products-page .product-fields textarea {
  box-sizing: border-box;
  width: 100%;
  border-color: #e0e8d7;
  border-radius: 9px;
  padding: 12px 13px;
  background: #fbfdf8;
  color: #687e54;
  font-size: 13px;
  line-height: 1.6;
}
.product-fields input::placeholder,
.product-fields textarea::placeholder {
  color: #b0bba0;
}
.product-fields small {
  font-size: 11px;
  color: #a6b095;
  line-height: 1.8;
}
.form-section-heading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding-bottom: 16px;
  border-bottom: 1px solid #eef2e6;
  margin-top: 6px;
}
.form-section-heading > span {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  background: #edf5e5;
  color: #8ca775;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.form-section-heading h3 {
  font-size: 15px;
  color: #728b5d;
  margin: 1px 0 7px;
}
.form-section-heading p {
  font-size: 11px;
  color: #a5b093;
  line-height: 1.8;
  margin: 0;
}
.cover-preview {
  border: 1px dashed #dce7cc;
  background: #f8fbf3;
  border-radius: 13px;
  padding: 12px;
  box-sizing: border-box;
}
.cover-caption {
  align-self: center;
  font-size: 12px;
  color: #94a47f;
}
.cover-caption strong {
  font-weight: 500;
  color: #7f9768;
}
.cover-caption p {
  line-height: 1.9;
  font-size: 11px;
  color: #a6b192;
  max-width: 320px;
}
.image-upload {
  display: grid;
  gap: 10px;
}
.image-upload input[type='file']::file-selector-button {
  border: 1px solid #dce7cc;
  border-radius: 6px;
  background: #edf5e5;
  color: #687e54;
  padding: 8px 12px;
  margin-right: 12px;
  cursor: pointer;
}
.detail-image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(145px, 1fr));
  gap: 14px;
  margin-top: 16px;
}
.detail-image-card {
  min-width: 0;
  border: 1px solid #e0e8d7;
  border-radius: 10px;
  padding: 10px;
  background: #f8fbf3;
  color: #81966d;
  font-size: 12px;
}
.detail-image-card img {
  display: block;
  width: 100%;
  height: 140px;
  object-fit: contain;
  border-radius: 6px;
  background: white;
  margin-bottom: 10px;
}
.image-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
}
.image-empty,
.upload-status {
  color: #81966d;
  font-size: 12px;
  line-height: 1.8;
}
.editor-actions {
  border-top: 1px solid #e9efdf;
  padding-top: 22px;
  justify-content: flex-end;
  gap: 12px;
}
.editor-actions .secondary-button {
  order: -1;
}
.product-help {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 11px;
  color: #a0ac90;
  line-height: 1.9;
  margin-top: 20px;
}
.product-help svg {
  flex-shrink: 0;
  margin-top: 3px;
}
.product-alert,
.product-notice {
  border: 1px solid #eddcd1;
  background: #fcf4ef;
  color: #b08e78;
  padding: 14px 18px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.8;
}
.product-notice {
  display: flex;
  align-items: center;
  gap: 9px;
  border-color: #e0e9d5;
  background: #f2f8eb;
  color: #849d6c;
}
@media (max-width: 1100px) {
  .product-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .product-overview {
    gap: 10px;
    margin: 20px 0;
  }
  .product-overview article {
    padding: 15px;
    gap: 8px;
    border-radius: 12px;
  }
  .product-overview strong {
    font-size: 25px;
  }
  .product-overview small {
    font-size: 10px;
  }
  .overview-icon {
    width: 30px;
    height: 30px;
    border-radius: 10px;
  }
  .products-page .product-filters {
    padding: 16px;
    gap: 10px;
  }
  .product-filters .table-search {
    flex-basis: 100%;
    max-width: none;
  }
  .products-page .product-filters > select {
    flex: 1;
    min-width: 100px;
  }
  .product-status-tabs {
    padding: 12px;
    gap: 3px;
  }
  .product-status-tabs button {
    padding: 9px 12px;
  }
  .product-editor {
    padding: 20px 16px;
    border-radius: 14px;
  }
  .product-editor > .panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .product-fields {
    gap: 20px;
  }
  .cover-caption {
    grid-column: 1/-1;
  }
  .category-form {
    padding: 15px;
  }
  .category-form > label {
    min-width: 0;
    width: 100%;
  }
  .products-page .pagination {
    padding: 15px;
    gap: 12px;
    flex-wrap: wrap;
  }
  .products-page .pagination > div {
    display: flex;
    align-items: center;
    gap: 7px;
    flex-wrap: wrap;
  }
  .editor-actions button {
    flex: 1;
    justify-content: center;
  }
}
@media (prefers-reduced-motion: reduce) {
  .products-page * {
    scroll-behavior: auto !important;
    transition: none !important;
  }
}
</style>
