<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronRight, Heart, Minus, Plus, X, ZoomIn } from 'lucide-vue-next'
import { catalogApi, demoMode } from '../api/catalog'
import type { Product } from '../api/types'
import { useCartStore } from '../stores/cart'
import { session } from '../platform/session'
import ProductImage from '../components/ProductImage.vue'
import ShopHeader from '../components/ShopHeader.vue'
import { formatPrice, matchingSku, validQuantity } from '../components/shop'
import { mallApi, type Detail, type Matrix } from '../api/mall'
const route = useRoute(),
  router = useRouter(),
  cart = useCartStore()
const detail = ref<Detail>(),
  matrix = ref<Matrix>(),
  product = ref<Product>()
const selection = ref<Record<string, string>>({}),
  favorite = ref(false),
  favoriteBusy = ref(false)
const loading = ref(false),
  error = ref(''),
  notice = ref(''),
  actionError = ref(''),
  busy = ref(false)
const skuLoading = ref(false),
  skuError = ref(''),
  selected = ref(0),
  quantity = ref('1')
const preview = ref<HTMLDialogElement>()
const previewTrigger = ref<HTMLButtonElement>()
let sequence = 0,
  skuSequence = 0
const listLink = computed(() =>
  typeof route.query.from === 'string' && /^\/catalog(?:\?|$)/.test(route.query.from)
    ? route.query.from
    : '/catalog',
)
const hasSkus = computed(() => !!matrix.value?.skus.length)
const selectedSku = computed(() => matchingSku(matrix.value, selection.value))
const displayPrice = computed(() => selectedSku.value?.price ?? product.value?.price ?? 0)
const stock = computed(
  () =>
    selectedSku.value?.availableStock ??
    (hasSkus.value ? undefined : product.value?.availableStock),
)
const maximum = computed(() => Math.min(stock.value ?? 999, 999))
const quantityError = computed(() =>
  validQuantity(quantity.value, maximum.value)
    ? ''
    : maximum.value === 0
      ? '商品暂时缺货'
      : `请输入 1 至 ${maximum.value} 的整数`,
)
const unavailable = computed(
  () =>
    product.value?.purchasable === false ||
    product.value?.availableStock === 0 ||
    ['off_sale', 'sold_out', 'presale', 'scheduled'].includes(product.value?.status || ''),
)
const images = computed(() => [
  ...new Set(
    [
      selectedSku.value?.imageUrl,
      ...(detail.value?.mainImages || []),
      product.value?.coverUrl,
    ].filter((x): x is string => !!x),
  ),
])
const selectionHint = computed(() => {
  if (!hasSkus.value) return ''
  const missing = matrix
    .value!.dimensions.filter((d) => !selection.value[d.name])
    .map((d) => d.name)
  if (missing.length) return `请选择${missing.join('、')}`
  return selectedSku.value
    ? `已选：${Object.values(selectedSku.value.specs).join(' / ') || selectedSku.value.name}`
    : '当前规格组合不可购买，请重新选择'
})
const buttonText = computed(() =>
  busy.value
    ? '正在加入…'
    : unavailable.value
      ? '暂不可购买'
      : hasSkus.value
        ? '暂不支持在线购买'
        : '加入购物车',
)
const canAdd = computed(
  () =>
    !busy.value &&
    !unavailable.value &&
    !hasSkus.value &&
    !skuLoading.value &&
    !quantityError.value,
)
async function load() {
  const token = ++sequence
  skuSequence++
  loading.value = true
  error.value = ''
  notice.value = ''
  actionError.value = ''
  selected.value = 0
  product.value = undefined
  detail.value = undefined
  matrix.value = undefined
  selection.value = {}
  quantity.value = '1'
  favorite.value = false
  skuLoading.value = false
  skuError.value = ''
  preview.value?.close()
  const id = String(route.params.id)
  try {
    if (demoMode) {
      const result = await catalogApi.detail(id)
      if (token === sequence) product.value = result
    } else {
      const result = await mallApi.detail(id)
      if (token !== sequence) return
      detail.value = result
      matrix.value = result.matrix
      product.value = {
        ...result.product,
        skuCount: result.matrix.skus.length,
        description: result.description,
        detailImages: result.detailImages,
      }
      if (session.read()) {
        void mallApi.visit(id).catch(() => {})
        void mallApi
          .isFavorite(id)
          .then((saved) => {
            if (token === sequence) favorite.value = saved
          })
          .catch(() => {})
      }
    }
  } catch (e) {
    if (token === sequence) error.value = (e as Error).message
  } finally {
    if (token === sequence) loading.value = false
  }
}
async function refreshMatrix() {
  if (demoMode || !hasSkus.value || loading.value) return
  const token = ++skuSequence
  const chosen = { ...selection.value }
  skuLoading.value = true
  skuError.value = ''
  try {
    const result = await mallApi.matrix(String(route.params.id), chosen)
    if (token === skuSequence) matrix.value = result
  } catch (e) {
    if (token === skuSequence) skuError.value = (e as Error).message
  } finally {
    if (token === skuSequence) skuLoading.value = false
  }
}
function optionDisabled(name: string, value: string) {
  return !matrix.value?.skus.some(
    (s) =>
      s.selectable &&
      s.availableStock > 0 &&
      s.specs[name] === value &&
      Object.entries(selection.value).every(
        ([key, selectedValue]) => key === name || s.specs[key] === selectedValue,
      ),
  )
}
function choose(name: string, value: string) {
  selection.value = { ...selection.value, [name]: value }
  selected.value = 0
  void refreshMatrix()
}
function resetSelection() {
  selection.value = {}
  void refreshMatrix()
}
async function toggleFavorite() {
  if (favoriteBusy.value) return
  if (!session.read()) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const token = sequence
  favoriteBusy.value = true
  try {
    await mallApi.favorite(String(route.params.id), !favorite.value)
    if (token === sequence) {
      favorite.value = !favorite.value
      notice.value = favorite.value ? '已收藏商品' : '已取消收藏'
    }
  } catch (e) {
    if (token === sequence) actionError.value = (e as Error).message
  } finally {
    favoriteBusy.value = false
  }
}
async function add() {
  if (!product.value || busy.value) return
  actionError.value = ''
  notice.value = ''
  if (!canAdd.value) {
    actionError.value = hasSkus.value
      ? '该商品暂不支持在线购买'
      : quantityError.value || '商品暂不可购买'
    return
  }
  if (!demoMode && !session.read()) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const token = sequence
  busy.value = true
  try {
    await cart.add(product.value, Number(quantity.value))
    if (token === sequence) notice.value = demoMode ? '演示：已添加到购物车' : '已加入购物车'
  } catch (e) {
    if (token === sequence) actionError.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
function step(delta: number) {
  quantity.value = String(
    Math.min(maximum.value, Math.max(1, (Number(quantity.value) || 1) + delta)),
  )
}
function nextImage(delta: number) {
  if (images.value.length)
    selected.value = (selected.value + delta + images.value.length) % images.value.length
}
function openPreview() {
  preview.value?.showModal()
}
const refreshTimer = setInterval(() => {
  if (!document.hidden && !skuLoading.value) void refreshMatrix()
}, 20000)
onUnmounted(() => {
  clearInterval(refreshTimer)
  sequence++
  skuSequence++
})
watch(() => route.params.id, load, { immediate: true })
watch(
  () => selectedSku.value?.id,
  () => {
    selected.value = 0
  },
)
</script>

<template>
  <div class="product-page">
    <ShopHeader />
    <nav class="breadcrumbs" aria-label="面包屑">
      <RouterLink to="/">首页</RouterLink><ChevronRight :size="13" aria-hidden="true" /><RouterLink
        :to="listLink"
        >好物商城</RouterLink
      ><ChevronRight :size="13" aria-hidden="true" /><span aria-current="page">商品详情</span>
    </nav>
    <p v-if="loading" class="state" role="status">正在加载商品详情…</p>
    <section v-else-if="error || !product" class="detail-error" role="alert">
      <h1>暂时无法查看商品</h1>
      <p>{{ error || '商品不存在或已下架' }}</p>
      <button class="shop-button" @click="load">重新加载</button
      ><RouterLink :to="listLink">返回商品列表</RouterLink>
    </section>
    <article v-else>
      <div class="product-detail">
        <section class="gallery" aria-label="商品图库">
          <button
            ref="previewTrigger"
            class="detail-cover"
            :disabled="!images.length"
            :aria-label="`放大查看${product.name}图片`"
            @click="openPreview"
          >
            <ProductImage
              :src="images[selected]"
              :alt="product.name"
              :kind="product.id"
              placeholder
              priority
            /><span v-if="images.length" class="zoom-label"
              ><ZoomIn :size="16" aria-hidden="true" />点击放大</span
            >
          </button>
          <div v-if="images.length > 1" class="detail-thumbnails">
            <button
              v-for="(src, index) in images"
              :key="src"
              :aria-label="`查看商品图片 ${index + 1}`"
              :aria-pressed="selected === index"
              @click="selected = index"
            >
              <ProductImage
                :src="src"
                :alt="`${product.name}图片 ${index + 1}`"
                :kind="product.id"
                placeholder
              />
            </button>
          </div>
        </section>
        <section class="purchase-info" aria-label="商品购买信息">
          <p v-if="product.brandName" class="product-brand">{{ product.brandName }}</p>
          <h1>{{ product.name }}</h1>
          <p v-if="product.subtitle" class="product-subtitle">{{ product.subtitle }}</p>
          <div class="detail-price-box">
            <span>商品价格</span><strong>{{ formatPrice(displayPrice) }}</strong
            ><small v-if="hasSkus && !selectedSku">起 · 选择规格查看对应价格</small
            ><small v-if="product.sales > 0">已售 {{ product.sales }}</small>
          </div>
          <p v-if="unavailable" class="availability" role="status">
            {{
              product.statusMessage && product.statusMessage !== '已上架'
                ? product.statusMessage
                : '商品暂不可购买'
            }}
          </p>
          <section v-if="hasSkus" class="sku-picker" aria-label="选择商品规格">
            <fieldset v-for="dimension in matrix!.dimensions" :key="dimension.name">
              <legend>{{ dimension.name }}</legend>
              <div class="sku-options">
                <button
                  v-for="option in dimension.options"
                  :key="option.value"
                  :disabled="optionDisabled(dimension.name, option.value) || skuLoading"
                  :aria-pressed="selection[dimension.name] === option.value"
                  @click="choose(dimension.name, option.value)"
                >
                  {{ option.value }}
                </button>
              </div>
            </fieldset>
            <p class="selection-hint" role="status">{{ selectionHint }}</p>
            <div class="sku-tools">
              <button @click="resetSelection" :disabled="skuLoading">重选规格</button
              ><button :disabled="skuLoading" @click="refreshMatrix">刷新价格与库存</button>
            </div>
            <p v-if="skuLoading" class="selection-hint" role="status">正在确认价格与库存…</p>
            <p v-if="skuError" class="error-text" role="alert">{{ skuError }}，请刷新后重试。</p>
          </section>
          <p class="stock-state">
            库存：{{
              stock === undefined
                ? hasSkus
                  ? '选择规格后查看'
                  : '以提交时校验为准'
                : stock > 0
                  ? `有货（剩余 ${stock} 件）`
                  : '暂时缺货'
            }}
          </p>
          <div class="quantity-row">
            <label for="purchase-quantity">购买数量</label>
            <div class="quantity-stepper">
              <button
                aria-label="减少购买数量"
                :disabled="busy || maximum < 1 || Number(quantity) <= 1"
                @click="step(-1)"
              >
                <Minus :size="15" aria-hidden="true" /></button
              ><input
                id="purchase-quantity"
                v-model="quantity"
                inputmode="numeric"
                maxlength="4"
                :disabled="busy || maximum < 1"
                :aria-invalid="!!quantityError"
                :aria-describedby="quantityError ? 'quantity-error' : undefined"
              /><button
                aria-label="增加购买数量"
                :disabled="busy || Number(quantity) >= maximum"
                @click="step(1)"
              >
                <Plus :size="15" aria-hidden="true" />
              </button>
            </div>
          </div>
          <p v-if="quantityError" id="quantity-error" class="error-text" role="alert">
            {{ quantityError }}
          </p>
          <p v-if="hasSkus" id="sku-unavailable" class="availability">
            该商品暂不支持在线购买，可先收藏商品。
          </p>
          <ul v-if="detail?.servicePromises.length" class="service-promises" aria-label="商品服务">
            <li v-for="promise in detail.servicePromises" :key="promise">{{ promise }}</li>
          </ul>
          <div class="purchase-actions">
            <button
              class="shop-button add-button"
              :disabled="!canAdd"
              :aria-busy="busy"
              :aria-describedby="hasSkus ? 'sku-unavailable' : undefined"
              @click="add"
            >
              {{ buttonText }}</button
            ><button
              v-if="!demoMode"
              class="favorite-button"
              :disabled="favoriteBusy"
              :aria-pressed="favorite"
              @click="toggleFavorite"
            >
              <Heart :size="17" aria-hidden="true" :fill="favorite ? 'currentColor' : 'none'" />{{
                favoriteBusy ? '处理中…' : favorite ? '已收藏' : '收藏'
              }}
            </button>
          </div>
          <small class="price-note">价格与库存以提交时系统校验为准，加入购物车不锁定库存。</small>
          <p v-if="notice" class="action-notice" role="status">
            {{ notice }} <RouterLink to="/cart">查看购物车，去结算 →</RouterLink>
          </p>
          <p v-if="actionError" class="error-text" role="alert">{{ actionError }}</p>
        </section>
      </div>
      <section class="detail-description">
        <h2>商品介绍</h2>
        <p>{{ product.description || '暂无详细介绍。' }}</p>
        <div v-for="src in product.detailImages" :key="src" class="detail-long-image">
          <ProductImage :src="src" :alt="`${product.name}详情`" :kind="product.id" placeholder />
        </div>
      </section>
      <section class="detail-description">
        <h2>规格参数</h2>
        <dl class="parameter-list">
          <template v-if="product.brandName"
            ><dt>品牌</dt>
            <dd>{{ product.brandName }}</dd></template
          ><template v-for="(values, name) in detail?.attributes" :key="name"
            ><dt>{{ name }}</dt>
            <dd>{{ values.join(' / ') }}</dd></template
          ><template v-if="selectedSku"
            ><dt>所选规格</dt>
            <dd>{{ selectedSku.name }}</dd>
            <dt v-if="selectedSku.code">商品编码</dt>
            <dd v-if="selectedSku.code">{{ selectedSku.code }}</dd></template
          >
        </dl>
        <p
          v-if="!product.brandName && !Object.keys(detail?.attributes || {}).length && !selectedSku"
        >
          暂无规格参数。
        </p>
      </section>
      <section v-if="detail && detail.review.count > 0" class="detail-description">
        <h2>评价概览</h2>
        <p>
          {{ detail.review.count }} 条评价 · 好评 {{ detail.review.positiveCount }} 条 · 平均
          {{ detail.review.averageRating }} 分
        </p>
      </section>
      <div class="mobile-purchase">
        <div>
          <strong>{{ formatPrice(displayPrice) }}</strong
          ><small>{{ hasSkus ? (selectedSku ? '已选规格' : '请选择规格') : '商品价格' }}</small>
        </div>
        <button class="shop-button" :disabled="!canAdd" :aria-busy="busy" @click="add">
          {{ buttonText }}</button
        ><RouterLink to="/cart" :aria-label="`购物车，${cart.count} 件商品`"
          >购物车 <b>{{ cart.count }}</b></RouterLink
        >
      </div>
      <dialog
        ref="preview"
        class="image-preview"
        @close="previewTrigger?.focus()"
        aria-labelledby="preview-title"
        @click="$event.target === $event.currentTarget && preview?.close()"
        @keydown.left.prevent="nextImage(-1)"
        @keydown.right.prevent="nextImage(1)"
      >
        <div class="preview-inner">
          <h2 id="preview-title">{{ product.name }}</h2>
          <button
            class="preview-close"
            aria-label="关闭图片预览"
            autofocus
            @click="preview?.close()"
          >
            <X :size="24" /></button
          ><ProductImage
            :src="images[selected]"
            :alt="product.name"
            :kind="product.id"
            placeholder
          />
          <div class="preview-navigation">
            <button :disabled="images.length < 2" @click="nextImage(-1)">上一张</button
            ><span>{{ selected + 1 }} / {{ images.length }}</span
            ><button :disabled="images.length < 2" @click="nextImage(1)">下一张</button>
          </div>
        </div>
      </dialog>
    </article>
  </div>
</template>

<style scoped>
.product-page {
  max-width: 1240px;
  margin: 0 auto;
  color: #292929;
}
.breadcrumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #666;
  margin-bottom: 20px;
}
.breadcrumbs a:hover {
  color: #a53e2b;
}
.product-detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 40px;
  margin: 0 0 28px;
  padding: 26px;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
}
.gallery {
  min-width: 0;
}
.detail-cover {
  display: block;
  position: relative;
  width: 100%;
  height: auto;
  aspect-ratio: 1;
  border: 1px solid #eee;
  padding: 12px;
  background: #fafafa;
  cursor: zoom-in;
}
.detail-cover:disabled {
  cursor: default;
}
.detail-cover :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.zoom-label {
  width: auto;
  height: auto;
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 9px;
  border-radius: 4px;
  background: #ffffffed;
  font-size: 12px;
  color: #555;
}
.detail-thumbnails {
  overflow-x: auto;
  padding: 2px;
}
.detail-thumbnails button {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  background: #fafafa;
}
.detail-thumbnails button[aria-pressed='true'] {
  border-color: #a53e2b;
}
.detail-thumbnails :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.purchase-info {
  min-width: 0;
}
.purchase-info h1 {
  margin: 0 0 10px;
  font-size: 25px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.product-brand {
  margin: 0 0 8px;
  color: #666;
  font-size: 12px;
}
.product-subtitle {
  color: #666;
  line-height: 1.7;
  font-size: 14px;
  margin-bottom: 22px;
}
.detail-price-box {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px 14px;
  padding: 20px;
  background: #faf3f0;
  border-radius: 7px;
}
.detail-price-box > span {
  font-size: 12px;
  color: #745d55;
}
.detail-price-box strong {
  font-size: 30px;
  color: #a53e2b;
}
.detail-price-box small {
  color: #735a51;
  font-size: 12px;
}
.availability {
  background: #fff4e9;
  color: #8e4029;
  padding: 12px;
  line-height: 1.7;
  border-radius: 5px;
  font-size: 13px;
}
.sku-picker {
  margin-top: 20px;
}
.sku-picker fieldset {
  border: 0;
  margin: 0 0 14px;
  padding: 0;
}
.sku-picker legend {
  font-size: 13px;
  color: #555;
  margin-bottom: 10px;
}
.sku-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.sku-options button {
  border: 1px solid #ddd;
  background: #fff;
  color: #333;
  border-radius: 5px;
  padding: 9px 13px;
  font-size: 13px;
}
.sku-options button[aria-pressed='true'] {
  background: #faf0ec;
  color: #9b3524;
  border-color: #a53e2b;
}
.sku-options button:disabled {
  color: #999;
  background: #f7f7f7;
  border-style: dashed;
  cursor: not-allowed;
}
.sku-options button[aria-pressed='true']:disabled {
  color: #9b3524;
}
.selection-hint {
  font-size: 13px;
  color: #555;
}
.sku-tools {
  display: flex;
  gap: 14px;
}
.sku-tools button {
  border: 0;
  padding: 6px 0;
  color: #98412f;
  background: transparent;
  font-size: 12px;
  text-decoration: underline;
}
.stock-state {
  margin: 20px 0;
  font-size: 13px;
  color: #555;
}
.quantity-row {
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 13px;
  color: #555;
}
.quantity-stepper {
  display: flex;
  height: 38px;
  border: 1px solid #ccc;
  border-radius: 5px;
  overflow: hidden;
}
.quantity-stepper button {
  width: 36px;
  border: 0;
  background: #fafafa;
  display: grid;
  place-items: center;
}
.quantity-stepper input {
  width: 55px;
  text-align: center;
  border: 0;
  border-left: 1px solid #ddd;
  border-right: 1px solid #ddd;
  background: #fff;
  color: #333;
}
.quantity-stepper button:disabled {
  color: #aaa;
  cursor: not-allowed;
}
.service-promises {
  padding-left: 18px;
  color: #555;
  font-size: 13px;
  line-height: 1.9;
}
.purchase-actions {
  display: flex;
  gap: 12px;
  margin: 24px 0 12px;
}
.shop-button {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  min-height: 44px;
  border: 1px solid #a53e2b;
  border-radius: 6px;
  padding: 10px 24px;
  color: #fff;
  background: #a53e2b;
  font-size: 14px;
}
.shop-button:hover:enabled {
  background: #8e3323;
}
.shop-button:disabled {
  background: #eee;
  color: #777;
  border-color: #ddd;
  cursor: not-allowed;
}
.add-button {
  min-width: 175px;
}
.favorite-button {
  display: flex;
  gap: 7px;
  align-items: center;
  justify-content: center;
  padding: 9px 16px;
  border: 1px solid #ddd;
  border-radius: 6px;
  background: #fff;
  color: #666;
  font-size: 13px;
}
.favorite-button[aria-pressed='true'] {
  color: #a53e2b;
}
.price-note {
  color: #6a6a6a;
  font-size: 11px;
  line-height: 1.7;
}
.action-notice,
.error-text {
  font-size: 13px;
  line-height: 1.8;
}
.action-notice a {
  display: inline-block;
  color: #a53e2b;
  text-decoration: underline;
}
.error-text {
  color: #a13223;
}
.detail-description {
  margin-bottom: 20px;
  padding: 26px;
  border-color: #e5e5e5;
}
.detail-description h2 {
  margin: 0 0 20px;
  font-size: 18px;
  padding-left: 10px;
  border-left: 3px solid #a53e2b;
}
.detail-description p {
  color: #555;
  font-size: 14px;
}
.detail-long-image :deep(img) {
  height: auto;
}
.parameter-list {
  display: grid;
  grid-template-columns: 130px 1fr;
  margin: 0;
  font-size: 13px;
}
.parameter-list dt,
.parameter-list dd {
  margin: 0;
  padding: 13px 16px;
  border-bottom: 1px solid #eee;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.parameter-list dt {
  background: #f7f7f7;
  color: #666;
}
.detail-error {
  padding: 50px 20px;
  background: #fff;
  text-align: center;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
}
.detail-error h1 {
  font-size: 22px;
}
.detail-error p {
  color: #666;
}
.detail-error a {
  display: inline-block;
  padding: 15px;
  color: #a53e2b;
}
.mobile-purchase {
  display: none;
}
.image-preview {
  border: 0;
  padding: 0;
  border-radius: 10px;
  width: min(900px, 94vw);
  max-height: 92dvh;
  color: #333;
  background: #fff;
}
.image-preview::backdrop {
  background: #000b;
}
.preview-inner {
  padding: 20px;
}
.preview-inner h2 {
  margin: 0 40px 16px 0;
  font-size: 15px;
}
.preview-inner > :deep(img),
.preview-inner > :deep(.image-placeholder) {
  display: block;
  width: 100%;
  height: 65dvh;
  object-fit: contain;
}
.preview-close {
  position: absolute;
  right: 12px;
  top: 12px;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  background: #f4f4f4;
  border: 0;
  border-radius: 50%;
}
.preview-navigation {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  margin-top: 16px;
  font-size: 13px;
}
.preview-navigation button {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: #fff;
}
@media (max-width: 1100px) {
  .product-detail {
    padding: 20px;
    gap: 24px;
  }
  .purchase-info h1 {
    font-size: 22px;
  }
  .detail-price-box {
    padding: 16px;
  }
  .detail-price-box strong {
    font-size: 26px;
  }
}
@media (max-width: 800px) {
  .product-detail {
    grid-template-columns: 1fr;
  }
  .gallery {
    max-width: 560px;
    width: 100%;
    margin: 0 auto;
  }
}
@media (max-width: 620px) {
  .product-page {
    padding-bottom: 96px;
  }
  .product-detail {
    padding: 14px;
    gap: 24px;
  }
  .purchase-info h1 {
    font-size: 21px;
  }
  .detail-description {
    padding: 20px 16px;
  }
  .parameter-list {
    grid-template-columns: 90px 1fr;
  }
  .parameter-list dt,
  .parameter-list dd {
    padding: 11px;
  }
  .purchase-actions .add-button {
    display: none;
  }
  .purchase-actions {
    margin-top: 18px;
  }
  .mobile-purchase {
    position: fixed;
    z-index: 6;
    inset: auto 0 0;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    border-top: 1px solid #e3e3e3;
    background: #fff;
    box-shadow: 0 -3px 14px #00000006;
  }
  .mobile-purchase > div {
    margin-right: auto;
  }
  .mobile-purchase strong {
    color: #a53e2b;
    font-size: 20px;
  }
  .mobile-purchase small {
    display: block;
    color: #666;
    font-size: 10px;
    margin-top: 3px;
  }
  .mobile-purchase .shop-button {
    padding: 9px 14px;
    font-size: 13px;
  }
  .mobile-purchase > a {
    display: grid;
    gap: 2px;
    justify-items: center;
    color: #555;
    font-size: 11px;
  }
  .mobile-purchase b {
    color: #a53e2b;
    font-size: 13px;
  }
}
@media (max-width: 359px) {
  .mobile-purchase {
    gap: 7px;
    padding-left: 10px;
    padding-right: 10px;
  }
  .mobile-purchase strong {
    font-size: 17px;
  }
  .mobile-purchase .shop-button {
    padding: 9px;
    font-size: 12px;
  }
}
</style>
