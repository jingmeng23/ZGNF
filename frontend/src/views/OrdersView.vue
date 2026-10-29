<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { Package } from 'lucide-vue-next'
import OrderDetailPanel from '../components/OrderDetailPanel.vue'
import { userOrdersApi, orderStatusLabels, type OrderRecord } from '../api/orders'
const rows = ref<OrderRecord[]>([]),
  pages = ref(0),
  total = ref(0),
  selected = ref<OrderRecord | null>(null)
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  message = ref(''),
  detail = ref<HTMLElement>()
const query = reactive({ page: 1, size: 10, status: '', orderNo: '' })
let sequence = 0
async function load() {
  const version = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await userOrdersApi.list({ ...query, orderNo: query.orderNo.trim() })
    if (version !== sequence) return
    rows.value = result.list
    pages.value = result.pages
    total.value = result.total
    if (query.page > Math.max(1, result.pages)) query.page = Math.max(1, result.pages)
  } catch (e) {
    if (version === sequence) {
      error.value = (e as Error).message
      rows.value = []
      total.value = 0
      pages.value = 0
    }
  } finally {
    if (version === sequence) loading.value = false
  }
}
function search() {
  if (query.page !== 1) query.page = 1
  else void load()
}
watch(() => query.page, load)
watch(() => query.status, search)
onMounted(load)
onUnmounted(() => sequence++)
async function open(id: string) {
  busy.value = true
  error.value = ''
  try {
    selected.value = await userOrdersApi.detail(id)
    requestAnimationFrame(() =>
      detail.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
    )
  } catch (e) {
    selected.value = null
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function action(type: 'cancel' | 'receipt') {
  if (!selected.value || busy.value) return
  if (
    !window.confirm(
      type === 'cancel'
        ? '确认取消此待付款订单？'
        : '请确认已收到商品或服务已完成，再确认完成订单。',
    )
  )
    return
  const id = selected.value.id
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const result =
      type === 'cancel' ? await userOrdersApi.cancel(id) : await userOrdersApi.confirmReceipt(id)
    await load()
    selected.value = await userOrdersApi.detail(id)
    message.value =
      type === 'cancel'
        ? result.status === 'cancelled'
          ? '订单已取消'
          : '订单状态已变化，请核实付款情况'
        : '订单已确认完成'
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <header class="page-heading">
    <span class="eyebrow">MY ORDERS</span>
    <h1>我的订单</h1>
    <p>查看订单、付款记录与物流进度。</p>
  </header>
  <form class="order-tools" @submit.prevent="search">
    <input
      v-model="query.orderNo"
      maxlength="40"
      aria-label="订单编号"
      placeholder="完整订单编号"
    /><select v-model="query.status" aria-label="订单状态">
      <option value="">全部订单</option>
      <option v-for="(label, state) in orderStatusLabels" :key="state" :value="state">
        {{ label }}
      </option></select
    ><button class="button secondary" :disabled="busy || loading">查询</button>
  </form>
  <p v-if="error" class="inline-notice" role="alert">
    {{ error }}<button :disabled="busy || loading" @click="load">重试</button>
  </p>
  <p v-if="message" role="status">{{ message }}</p>
  <p v-if="loading" class="state">正在加载订单…</p>
  <div v-else-if="!rows.length" class="empty-state">
    <Package :size="48" :stroke-width="1.2" />
    <h2>{{ error ? '暂时无法加载订单' : '暂无匹配订单' }}</h2>
    <RouterLink to="/catalog" class="button">继续逛逛</RouterLink>
  </div>
  <div v-else class="cart-list">
    <article v-for="order in rows" :key="order.id" class="cart-row">
      <Package :size="25" />
      <div class="cart-description">
        <h3>{{ order.orderNo }}</h3>
        <p>
          {{ order.createTime }} · {{ order.items.reduce((sum, i) => sum + i.quantity, 0) }} 件商品
        </p>
        <p>{{ order.items.map((i) => i.productName).join('、') }}</p>
        <strong>¥{{ Number(order.payAmount).toFixed(2) }}</strong>
      </div>
      <span>{{ orderStatusLabels[order.status] || order.status }}</span
      ><button class="button secondary" :disabled="busy" @click="open(order.id)">查看详情</button>
    </article>
  </div>
  <div class="order-tools">
    <span>共 {{ total }} 笔 · {{ query.page }} / {{ Math.max(1, pages) }}</span
    ><button
      class="button secondary"
      :disabled="loading || busy || query.page <= 1"
      @click="query.page--"
    >
      上一页</button
    ><button
      class="button secondary"
      :disabled="loading || busy || query.page >= pages"
      @click="query.page++"
    >
      下一页
    </button>
  </div>
  <section v-if="selected" ref="detail" class="panel user-order-detail">
    <div class="order-tools">
      <h2>订单详情</h2>
      <button class="button secondary" :disabled="busy" @click="open(selected.id)">刷新</button
      ><button class="button secondary" :disabled="busy" @click="selected = null">关闭详情</button>
    </div>
    <OrderDetailPanel :order="selected" />
    <div class="order-tools">
      <RouterLink v-if="['paid', 'processing', 'completed'].includes(selected.status)" class="button secondary" :to="{ path: '/aftersales', query: { orderId: selected.id } }">申请整单退款</RouterLink>
      <RouterLink class="button secondary" to="/aftersales">查看售后记录</RouterLink>
      <button
        v-if="selected.status === 'pending_payment'"
        class="button secondary"
        :disabled="busy"
        @click="action('cancel')"
      >
        取消待付款订单</button
      ><button
        v-if="selected.status === 'processing'"
        class="button"
        :disabled="busy"
        @click="action('receipt')"
      >
        确认收到商品 / 完成服务
      </button>
    </div>
  </section>
</template>
<style scoped>
.order-tools {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
  margin: 20px 0;
}
.order-tools input,
.order-tools select {
  padding: 10px;
  border: 1px solid #dce4df;
  border-radius: 6px;
  max-width: 100%;
}
.user-order-detail {
  padding: 24px;
  margin: 24px 0;
  scroll-margin-top: 24px;
}
.cart-description p {
  overflow-wrap: anywhere;
}
.cart-row {
  flex-wrap: wrap;
}
@media (max-width: 700px) {
  .user-order-detail {
    padding: 14px;
  }
}
</style>
