<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  Download,
  RefreshCw,
  Search,
  ShoppingBag,
  Clock3,
  PackageCheck,
  Wallet,
  ChevronRight,
  CheckCircle2,
} from 'lucide-vue-next'
import OrderDetailPanel from '../components/OrderDetailPanel.vue'
import {
  adminOrdersApi,
  orderStatusLabels,
  type OrderRecord,
  type OrderOperation,
} from '../api/orders'
import { ApiError } from '../api/http'
import { useRoute } from 'vue-router'
import { toCsv } from './csv'
const rows = ref<OrderRecord[]>([]),
  total = ref(0),
  pages = ref(0)
const route = useRoute()
function queryString(key: string) {
  return typeof route.query[key] === 'string' ? route.query[key] : ''
}
function orderDate(key: 'startTime' | 'endTime') {
  const value = queryString(key)
  const match = /^(\d{4}-\d{2}-\d{2})T(00:00:00|23:59:59)$/.exec(value)
  if (!match) return ''
  const date = match[1]
  const timestamp = Date.parse(`${date}T00:00:00Z`)
  if (!Number.isFinite(timestamp) || new Date(timestamp).toISOString().slice(0, 10) !== date) return ''
  return value
}
const query = reactive({
  page: 1,
  size: 10,
  orderNo: queryString('orderNo'),
  status: ['pending_payment', 'paid', 'processing', 'completed', 'cancelled', 'refunded'].includes(
    queryString('status'),
  )
    ? queryString('status')
    : '',
  userId: '',
  startTime: orderDate('startTime'),
  endTime: orderDate('endTime'),
})
const loading = ref(false),
  detailLoading = ref(false),
  busy = ref(false),
  error = ref(''),
  message = ref('')
const selected = ref<OrderRecord | null>(null),
  operations = ref<OrderOperation[]>([]),
  detailSection = ref<HTMLElement>()
const note = ref(''),
  closeReason = ref(''),
  carrier = ref(''),
  trackingNo = ref('')
const actionLabels: Record<string, string> = {
  process: '开始处理',
  cancel: '关闭订单',
  cancel_blocked: '关闭未执行',
  ship: '登记发货',
  shipment_update: '修改物流',
  note: '内部备注',
}
let listVersion = 0,
  detailVersion = 0
const money = (value: number) => `¥${Number(value || 0).toFixed(2)}`
const summary = computed(() => ({
  pending: rows.value.filter((row) => row.status === 'pending_payment').length,
  fulfilling: rows.value.filter((row) => ['paid', 'processing'].includes(row.status)).length,
  amount: rows.value.reduce((sum, row) => sum + Number(row.payAmount || 0), 0),
}))
function resetFilters() {
  Object.assign(query, { orderNo: '', userId: '', status: '', startTime: '', endTime: '' })
  search()
}
function selectStatus(status: string) {
  query.status = status
  search()
}
function explain(e: unknown) {
  return e instanceof ApiError && e.traceId
    ? `${e.message}（请求编号：${e.traceId}）`
    : e instanceof Error
      ? e.message
      : '操作失败，请重试'
}
async function load() {
  const version = ++listVersion
  loading.value = true
  error.value = ''
  try {
    const result = await adminOrdersApi.list({
      ...query,
      orderNo: query.orderNo.trim(),
      userId: query.userId.trim(),
    })
    if (version !== listVersion) return
    rows.value = result.list
    total.value = result.total
    pages.value = result.pages
    if (query.page > Math.max(1, result.pages)) query.page = Math.max(1, result.pages)
  } catch (e) {
    if (version === listVersion) {
      error.value = explain(e)
      rows.value = []
      total.value = 0
      pages.value = 0
    }
  } finally {
    if (version === listVersion) loading.value = false
  }
}
function search() {
  if (query.startTime && query.endTime && query.startTime > query.endTime) {
    error.value = '开始时间不能晚于结束时间'
    return
  }
  if (query.page !== 1) query.page = 1
  else void load()
}
watch(() => query.page, load)
watch(
  () => [route.query.orderNo, route.query.status, route.query.startTime, route.query.endTime],
  () => {
    Object.assign(query, {
      orderNo: queryString('orderNo'),
      status: [
        'pending_payment',
        'paid',
        'processing',
        'completed',
        'cancelled',
        'refunded',
      ].includes(queryString('status'))
        ? queryString('status')
        : '',
      startTime: orderDate('startTime'),
      endTime: orderDate('endTime'),
      userId: '',
    })
    search()
  },
)
onMounted(load)
onUnmounted(() => {
  listVersion++
  detailVersion++
})
function closeDetail() {
  detailVersion++
  selected.value = null
  operations.value = []
  detailLoading.value = false
}
async function open(id: string, scroll = true) {
  const version = ++detailVersion
  detailLoading.value = true
  error.value = ''
  try {
    const [order, logs] = await Promise.all([
      adminOrdersApi.detail(id),
      adminOrdersApi.operations(id),
    ])
    if (version !== detailVersion) return
    selected.value = order
    operations.value = logs
    carrier.value = order.logistics?.carrier || ''
    trackingNo.value = order.logistics?.trackingNo || ''
    note.value = ''
    closeReason.value = ''
    if (scroll)
      requestAnimationFrame(() =>
        detailSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      )
  } catch (e) {
    if (version === detailVersion) {
      selected.value = null
      operations.value = []
      error.value = explain(e)
    }
  } finally {
    if (version === detailVersion) detailLoading.value = false
  }
}
async function act(action: 'process' | 'cancel' | 'ship') {
  if (busy.value || detailLoading.value || !selected.value) return
  const id = selected.value.id
  if (action === 'cancel' && !closeReason.value.trim()) {
    error.value = '关闭订单前请填写原因'
    return
  }
  if (
    action === 'ship' &&
    (!carrier.value.trim() || !/^[A-Za-z0-9][A-Za-z0-9_-]{2,99}$/.test(trackingNo.value.trim()))
  ) {
    error.value = '请填写物流公司和有效单号（3–100 位字母、数字、下划线或连字符）'
    return
  }
  const title =
    action === 'cancel'
      ? '关闭待付款订单并释放预占资源'
      : action === 'process'
        ? '开始处理订单并扣减预占库存'
        : '保存发货信息'
  if (!window.confirm(`确认${title}？订单：${selected.value.orderNo}`)) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const result =
      action === 'cancel'
        ? await adminOrdersApi.cancel(id, closeReason.value.trim())
        : action === 'process'
          ? await adminOrdersApi.process(id)
          : await adminOrdersApi.ship(id, {
              carrier: carrier.value.trim(),
              trackingNo: trackingNo.value.trim(),
            })
    await load()
    await open(id, false)
    message.value =
      action === 'cancel'
        ? result.status === 'cancelled'
          ? '订单已关闭，预占资源按既有释放流程处理。'
          : '订单状态已变化，关闭未执行，请核实付款记录。'
        : action === 'ship'
          ? '发货信息已保存，用户订单详情可查看。'
          : '订单已开始处理'
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function saveNote() {
  if (busy.value || detailLoading.value || !selected.value || !note.value.trim()) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    operations.value = await adminOrdersApi.note(selected.value.id, note.value.trim())
    note.value = ''
    message.value = '内部备注已保存'
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
function exportPage() {
  const columns = [
    { key: 'orderNo', label: '订单编号' },
    { key: 'userId', label: '用户编号' },
    { key: 'receiver', label: '收货人' },
    { key: 'goods', label: '商品' },
    { key: 'totalAmount', label: '商品金额' },
    { key: 'discountAmount', label: '优惠' },
    { key: 'freightAmount', label: '运费' },
    { key: 'payAmount', label: '应付' },
    { key: 'status', label: '状态' },
    { key: 'createTime', label: '下单时间' },
  ]
  const data = rows.value.map((row) => ({
    id: row.id,
    orderNo: row.orderNo,
    userId: row.userId,
    receiver: row.address?.receiver || '',
    goods: row.items.map((i) => `${i.productName} × ${i.quantity}`).join('；'),
    totalAmount: row.totalAmount,
    discountAmount: row.discountAmount || 0,
    freightAmount: row.freightAmount || 0,
    payAmount: row.payAmount,
    status: orderStatusLabels[row.status],
    createTime: row.createTime,
  }))
  const link = document.createElement('a'),
    url = URL.createObjectURL(
      new Blob(['\ufeff' + toCsv(columns, data)], { type: 'text/csv;charset=utf-8' }),
    )
  link.href = url
  link.download = `订单-第${query.page}页.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>
<template>
  <div class="admin-page order-management">
    <header class="page-heading">
      <div>
        <span class="page-kicker">ORDERS</span>
        <h1>订单管理</h1>
        <p>查看付款与履约进度。已付款订单的退款请通过售后流程处理。</p>
      </div>
      <button
        class="secondary-button"
        :disabled="loading || busy || !rows.length"
        @click="exportPage"
      >
        <Download :size="16" />导出本页
      </button>
    </header>
    <p v-if="error" class="order-alert" role="alert">
      {{ error }}<button class="text-action" :disabled="busy" @click="load">重新加载列表</button>
    </p>
    <p v-if="message" class="order-notice" role="status">
      <CheckCircle2 :size="17" />{{ message }}
    </p>
    <section class="order-overview" aria-label="订单概览">
      <article>
        <div>
          <span>订单总数</span><strong>{{ total }}</strong
          ><small>当前筛选结果</small>
        </div>
        <span class="overview-icon"><ShoppingBag :size="22" /></span>
      </article>
      <article>
        <div>
          <span>本页待付款</span><strong>{{ summary.pending }}</strong
          ><small>等待用户完成支付</small>
        </div>
        <span class="overview-icon amber"><Clock3 :size="22" /></span>
      </article>
      <article>
        <div>
          <span>本页待履约</span><strong>{{ summary.fulfilling }}</strong
          ><small>已付款及处理中订单</small>
        </div>
        <span class="overview-icon blue"><PackageCheck :size="22" /></span>
      </article>
      <article>
        <div>
          <span>本页应付金额</span
          ><strong class="overview-amount">{{ money(summary.amount) }}</strong
          ><small>本页订单应付金额合计</small>
        </div>
        <span class="overview-icon lavender"><Wallet :size="22" /></span>
      </article>
    </section>
    <section class="panel list-panel" :aria-busy="loading">
      <div class="order-status-tabs" aria-label="按订单状态筛选">
        <button
          type="button"
          :class="{ active: !query.status }"
          :aria-pressed="!query.status"
          :disabled="loading || busy"
          @click="selectStatus('')"
        >
          全部订单</button
        ><button
          v-for="(label, status) in orderStatusLabels"
          :key="status"
          type="button"
          :class="{ active: query.status === status }"
          :aria-pressed="query.status === status"
          :disabled="loading || busy"
          @click="selectStatus(status)"
        >
          {{ label }}
        </button>
      </div>
      <form class="list-toolbar order-filters" @submit.prevent="search">
        <label class="filter-field order-number-field"
          ><span>订单编号</span
          ><span class="search-input"
            ><Search :size="16" /><input
              v-model="query.orderNo"
              maxlength="40"
              placeholder="完整订单编号"
              aria-label="订单编号" /></span></label
        ><label class="filter-field"
          ><span>用户编号</span
          ><input
            v-model="query.userId"
            pattern="[1-9][0-9]*"
            placeholder="用户编号"
            aria-label="用户编号"
        /></label>
        <label>下单开始<input v-model="query.startTime" type="datetime-local" step="1" /></label
        ><label>下单结束<input v-model="query.endTime" type="datetime-local" step="1" /></label
        ><button class="primary-button" :disabled="busy || loading">
          <Search :size="15" />查询</button
        ><button
          type="button"
          class="reset-button"
          :disabled="busy || loading"
          @click="resetFilters"
        >
          重置</button
        ><button
          type="button"
          class="secondary-button"
          :disabled="busy || loading"
          aria-label="刷新订单"
          @click="load"
        >
          <RefreshCw :size="16" />
        </button>
      </form>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>订单 / 下单时间</th>
              <th>用户 / 收货人</th>
              <th>商品</th>
              <th>应付 / 优惠</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.id"
              :class="{ 'selected-order': selected?.id === row.id }"
            >
              <td>
                <button
                  class="order-number"
                  :disabled="busy || detailLoading"
                  @click="open(row.id)"
                >
                  {{ row.orderNo }}</button
                ><small>{{ row.createTime }}</small>
              </td>
              <td>
                {{ row.address?.receiver || '暂无收货人' }}<small>用户 {{ row.userId }}</small>
              </td>
              <td class="goods-cell">
                <span v-for="(item, index) in row.items" :key="`${item.productId}-${index}`"
                  >{{ item.productName }} × {{ item.quantity }}<br
                /></span>
              </td>
              <td>
                <strong class="order-amount">{{ money(row.payAmount) }}</strong
                ><small>优惠 {{ money(row.discountAmount) }}</small>
              </td>
              <td>
                <span class="status-tag" :class="`order-${row.status}`">{{
                  orderStatusLabels[row.status] || row.status
                }}</span>
              </td>
              <td>
                <button class="text-action" :disabled="busy || detailLoading" @click="open(row.id)">
                  查看详情 <ChevronRight :size="14" />
                </button>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6" class="empty-state">
                <ShoppingBag :size="30" class="empty-icon" />
                {{ loading ? '正在加载订单…' : error ? '加载失败，请重试' : '暂无匹配订单' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="pagination">
        <span>共 {{ total }} 笔订单</span>
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
    <p v-if="detailLoading" role="status">正在加载订单详情…</p>
    <section
      v-if="selected"
      ref="detailSection"
      class="panel order-detail"
      :aria-busy="detailLoading"
    >
      <div class="panel-heading">
        <div>
          <span class="detail-kicker">ORDER DETAILS</span>
          <h2>
            订单详情
            <span class="status-tag" :class="`order-${selected.status}`">{{
              orderStatusLabels[selected.status] || selected.status
            }}</span>
          </h2>
          <p class="detail-order-no">{{ selected.orderNo }}</p>
        </div>
        <div class="order-actions">
          <button
            class="secondary-button"
            :disabled="busy || detailLoading"
            @click="open(selected.id, false)"
          >
            刷新详情</button
          ><button class="secondary-button" :disabled="busy" @click="closeDetail">关闭详情</button>
        </div>
      </div>
      <OrderDetailPanel :order="selected" />
      <div class="fulfillment-actions">
        <h3>运营操作</h3>
        <button
          v-if="selected.status === 'paid'"
          class="primary-button"
          :disabled="busy || detailLoading"
          @click="act('process')"
        >
          开始处理
        </button>
        <form
          v-if="selected.status === 'pending_payment'"
          class="order-form"
          @submit.prevent="act('cancel')"
        >
          <label
            >关闭原因<textarea
              v-model="closeReason"
              required
              maxlength="1000"
              rows="2"
              placeholder="请填写关闭原因，便于后续核查"
              :disabled="busy"
            /></label
          ><button class="secondary-button danger" :disabled="busy || detailLoading">
            关闭未付款订单
          </button>
        </form>
        <form
          v-if="['paid', 'processing'].includes(selected.status)"
          class="order-form"
          @submit.prevent="act('ship')"
        >
          <h4>发货 / 维护物流</h4>
          <p>一单登记一份物流信息；已开始处理后登记发货不会重复扣库存。</p>
          <label
            >物流公司<input
              v-model="carrier"
              required
              maxlength="80"
              :disabled="busy"
              placeholder="例如：顺丰速运" /></label
          ><label
            >物流单号<input
              v-model="trackingNo"
              required
              maxlength="100"
              pattern="[A-Za-z0-9][A-Za-z0-9_-]{2,99}"
              placeholder="请输入准确的物流单号"
              :disabled="busy" /></label
          ><button class="primary-button" :disabled="busy || detailLoading">保存发货信息</button>
        </form>
        <form class="order-form" @submit.prevent="saveNote">
          <h4>添加内部备注</h4>
          <label
            >仅管理端可见<textarea
              v-model="note"
              required
              maxlength="1000"
              rows="3"
              placeholder="记录沟通情况、处理说明等，仅管理端可见"
              :disabled="busy"
            /></label
          ><button class="secondary-button" :disabled="busy || detailLoading || !note.trim()">
            保存备注
          </button>
        </form>
      </div>
      <h3>管理操作记录</h3>
      <ol v-if="operations.length" class="operation-list">
        <li v-for="entry in operations" :key="entry.id">
          <strong>{{ actionLabels[entry.action] || entry.action }}</strong> · 操作员
          {{ entry.operatorId }} · {{ entry.occurredAt }}
          <p>{{ entry.remark }}</p>
        </li>
      </ol>
      <p v-else>暂无管理操作记录</p>
    </section>
  </div>
</template>
<style scoped>
.order-filters {
  flex-wrap: wrap;
  align-items: end;
}
.order-filters > input,
.order-filters > select,
.order-filters label > input,
.order-form input,
.order-form textarea {
  padding: 10px;
  border: 1px solid #dce4df;
  border-radius: 6px;
  background: white;
  max-width: 100%;
  font: inherit;
}
.order-filters label,
.order-form label {
  display: grid;
  gap: 6px;
}
.order-detail {
  margin-top: 24px;
  padding: 24px;
  scroll-margin-top: 24px;
}
.order-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.goods-cell {
  min-width: 180px;
  max-width: 350px;
  white-space: normal;
  overflow-wrap: anywhere;
}
td small {
  display: block;
  color: #75837b;
  margin-top: 5px;
}
.order-form {
  display: grid;
  gap: 12px;
  padding: 18px;
  background: #f5f7f5;
  margin: 16px 0;
  border-radius: 8px;
}
.order-form button {
  justify-self: start;
}
.order-form textarea {
  resize: vertical;
}
.order-form h4,
.order-form p {
  margin: 0;
}
.fulfillment-actions {
  margin-top: 24px;
  padding-top: 10px;
  border-top: 1px solid #e8ece8;
}
.operation-list {
  padding-left: 22px;
  overflow-wrap: anywhere;
}
.operation-list li {
  padding: 8px 0;
}
.operation-list p {
  white-space: pre-wrap;
  margin: 4px 0;
}
.danger {
  color: #b94343;
}
.order-paid,
.order-completed {
  background: #eaf4ed;
  color: #257251;
}
.order-pending_payment {
  background: #fff3df;
  color: #916011;
}
@media (max-width: 700px) {
  .order-detail {
    padding: 16px;
  }
  .order-filters > * {
    max-width: 100%;
  }
}

.order-management {
  --order-green: #5b876b;
  --order-line: #e5ece4;
  --order-muted: #8d998c;
}
.order-management .page-heading h1 {
  color: #304b38;
}
.order-management .page-heading p {
  color: #8c998c;
  line-height: 1.8;
}
.order-management .primary-button,
.order-management .secondary-button {
  border-radius: 9px;
  min-height: 38px;
  gap: 7px;
}
.order-management .primary-button {
  background: #5b876b;
  box-shadow: 0 3px 9px #5b876b15;
}
.order-management .primary-button:hover {
  background: #497657;
}
.order-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 24px 0;
}
.order-overview article {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 23px;
  border: 1px solid #e5ece2;
  border-radius: 16px;
  background: linear-gradient(140deg, #fff, #f8fbf6);
  box-shadow: 0 4px 18px #294b3407;
}
.order-overview article > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.order-overview article span {
  font-size: 12px;
  color: #8b9986;
}
.order-overview strong {
  font-size: 29px;
  font-weight: 600;
  letter-spacing: -0.8px;
  color: #466443;
  margin: 14px 0 9px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.order-overview strong.overview-amount {
  font-size: 24px;
  margin-top: 18px;
}
.order-overview small {
  color: #a0aa96;
  font-size: 11px;
  line-height: 1.7;
}
.overview-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  background: #edf5e9;
  border-radius: 13px;
  color: #7c9d6d !important;
}
.overview-icon.amber {
  background: #fbf1e1;
  color: #b99c6a !important;
}
.overview-icon.blue {
  background: #ecf3f6;
  color: #83a0b1 !important;
}
.overview-icon.lavender {
  background: #f1eef7;
  color: #a298b8 !important;
}
.order-management .list-panel {
  border: 1px solid var(--order-line);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 5px 24px #294b3407;
}
.order-status-tabs {
  display: flex;
  gap: 7px;
  padding: 17px 22px;
  border-bottom: 1px solid #edf1e8;
  overflow-x: auto;
  background: #fff;
}
.order-status-tabs button {
  border: 0;
  background: transparent;
  padding: 9px 15px;
  border-radius: 8px;
  white-space: nowrap;
  color: #909c86;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}
.order-status-tabs button.active {
  background: #edf5e8;
  color: #658451;
  font-weight: 600;
}
.order-status-tabs button:hover {
  background: #f5f8f0;
}
.order-management .order-filters {
  display: flex;
  gap: 14px;
  padding: 22px 24px;
  background: #fcfdfb;
  border-bottom: 1px solid #edf1e7;
}
.order-filters > label {
  font-size: 11px;
  color: #8c9981;
  gap: 8px;
  flex: 1;
  min-width: 150px;
}
.order-filters .order-number-field {
  flex: 1.25;
  min-width: 200px;
}
.order-management .order-filters input {
  border: 1px solid #e1e8da;
  border-radius: 8px;
  background: #fff;
  height: 40px;
  box-sizing: border-box;
  width: 100%;
  padding: 10px 12px;
  color: #6c7c60;
  font-size: 12px;
  min-width: 0;
}
.order-filters .search-input {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e1e8da;
  border-radius: 8px;
  padding-left: 12px;
  background: #fff;
  color: #9aa58d;
}
.order-management .search-input input {
  border: 0;
  background: transparent;
  padding-left: 0;
}
.order-management .order-filters input::placeholder {
  color: #afb7a4;
}
.reset-button {
  background: transparent;
  border: 0;
  color: #93a083;
  font: inherit;
  padding: 11px 6px;
  cursor: pointer;
}
.order-management input:focus-visible,
.order-management textarea:focus-visible,
.order-management select:focus-visible {
  outline: 2px solid #91b17d;
  outline-offset: 2px;
}
.order-management button:focus-visible {
  outline: 2px solid #91b17d;
  outline-offset: 3px;
}
.order-management .admin-table {
  min-width: 920px;
}
.order-management .admin-table th {
  background: #f5f8f1;
  color: #919d84;
  font-size: 11px;
  font-weight: 500;
  padding-top: 15px;
  padding-bottom: 15px;
}
.order-management .admin-table td {
  padding-top: 20px;
  padding-bottom: 20px;
  color: #748367;
  border-color: #eff3e9;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.order-management .admin-table td small {
  font-size: 10px;
  color: #a5ae99;
  margin-top: 8px;
}
.order-management .admin-table tr:hover td {
  background: #fbfdf7;
}
.order-management .admin-table tr.selected-order td {
  background: #f3f8ed;
}
.order-number {
  padding: 0;
  border: 0;
  background: none;
  color: #607d51;
  font: inherit;
  cursor: pointer;
  text-align: left;
}
.order-number:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.order-amount {
  font-weight: 600;
  font-size: 14px;
  color: #58714b;
}
.order-management .goods-cell {
  line-height: 1.9;
}
.order-management .text-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #7e996b;
  font-size: 12px;
}
.order-management .status-tag {
  padding: 5px 11px;
  border-radius: 20px;
  font-size: 11px;
  white-space: nowrap;
  font-weight: 400;
}
.order-paid {
  background: #edf5e8;
  color: #6f9156;
}
.order-processing {
  background: #edf3f8;
  color: #7d9bb2;
}
.order-shipped {
  background: #eeeff8;
  color: #9692b5;
}
.order-completed {
  background: #eaf4ed;
  color: #6d957b;
}
.order-cancelled {
  background: #f0f2ed;
  color: #a1a995;
}
.order-pending_payment {
  background: #fcf2e2;
  color: #b49761;
}
.order-management .pagination {
  padding: 18px 24px;
  border-top: 1px solid #edf1e7;
  color: #98a28c;
}
.order-management .pagination button,
.order-management .pagination select {
  border-radius: 7px;
  min-height: 32px;
  border-color: #e4ebdc;
}
.empty-icon {
  display: block;
  margin: 10px auto 14px;
  color: #bbc8ad;
}
.order-alert,
.order-notice {
  padding: 14px 18px;
  border: 1px solid #eeded6;
  border-radius: 10px;
  background: #fcf4ef;
  color: #b08370;
  font-size: 12px;
  line-height: 1.8;
}
.order-notice {
  display: flex;
  align-items: center;
  gap: 9px;
  border-color: #dfe9d7;
  background: #f2f8ec;
  color: #80976c;
}
.order-detail {
  border: 1px solid #e1e9da;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 6px 24px #294b3408;
  padding: 28px;
}
.order-detail > .panel-heading {
  padding: 0 0 20px;
  border-bottom: 1px solid #edf1e7;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 22px;
}
.detail-kicker {
  font-size: 10px;
  letter-spacing: 2px;
  color: #a1ad94;
}
.order-detail h2 {
  font-size: 20px;
  color: #4b6540;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 9px 0;
}
.detail-order-no {
  margin: 0;
  color: #9ba68e;
  font-size: 12px;
}
.order-detail h3 {
  font-size: 15px;
  color: #708460;
}
.fulfillment-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  align-items: start;
  border-color: #e9efdf;
  padding-top: 22px;
}
.fulfillment-actions > h3 {
  flex-basis: 100%;
  margin: 0;
}
.fulfillment-actions > .order-form {
  flex: 1 1 280px;
  min-width: 0;
  margin: 0;
  padding: 22px;
  border: 1px solid #e6ecde;
  border-radius: 13px;
  background: #fafcf6;
  gap: 15px;
}
.order-form h4 {
  color: #6c805c;
  font-size: 14px;
  font-weight: 600;
}
.order-form p {
  font-size: 11px;
  color: #a0ab91;
  line-height: 1.8;
}
.order-form label {
  color: #879673;
  font-size: 12px;
  gap: 9px;
}
.order-form input,
.order-form textarea {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  border-color: #e1e9d6;
  border-radius: 8px;
  background: #fff;
  color: #6d7e5c;
  padding: 11px 12px;
  line-height: 1.7;
}
.order-form input::placeholder,
.order-form textarea::placeholder {
  color: #b2bba3;
}
.order-form .danger {
  color: #b18a78;
  border-color: #ebddd3;
  background: #fcf5f0;
}
.operation-list {
  list-style: none;
  margin: 22px 0 0;
  padding: 0 0 0 18px;
  border-left: 1px solid #e1e9d8;
  color: #9da98d;
  font-size: 11px;
}
.operation-list li {
  position: relative;
  padding: 0 0 22px 10px;
  line-height: 1.8;
}
.operation-list li:before {
  content: '';
  position: absolute;
  left: -23px;
  top: 5px;
  width: 8px;
  height: 8px;
  background: #a7bc95;
  border: 3px solid #f4f8ee;
  border-radius: 50%;
}
.operation-list strong {
  color: #789266;
  font-weight: 500;
}
.operation-list p {
  padding: 9px 12px;
  border-radius: 8px;
  background: #f7faf2;
  color: #9aa88a;
  line-height: 1.8;
  margin-top: 8px;
}
.order-management button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@media (max-width: 1100px) {
  .order-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .order-filters > label {
    flex-basis: calc(50% - 14px);
  }
}
@media (max-width: 600px) {
  .order-overview {
    gap: 10px;
    margin: 20px 0;
  }
  .order-overview article {
    padding: 15px;
    gap: 8px;
    border-radius: 12px;
  }
  .overview-icon {
    width: 30px;
    height: 30px;
    border-radius: 10px;
  }
  .order-overview strong {
    font-size: 25px;
  }
  .order-overview strong.overview-amount {
    font-size: 20px;
  }
  .order-overview small {
    font-size: 10px;
  }
  .order-status-tabs {
    padding: 12px;
    gap: 3px;
  }
  .order-status-tabs button {
    padding: 9px 12px;
  }
  .order-management .order-filters {
    padding: 16px;
    gap: 12px;
  }
  .order-filters > label,
  .order-filters .order-number-field {
    flex-basis: 100%;
    min-width: 0;
  }
  .order-detail {
    padding: 18px 15px;
  }
  .order-detail > .panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .order-actions {
    width: 100%;
  }
  .order-actions button {
    flex: 1;
  }
  .fulfillment-actions > .order-form {
    padding: 17px;
  }
  .order-management .pagination {
    padding: 16px;
    gap: 12px;
    flex-wrap: wrap;
  }
  .order-management .pagination > div {
    display: flex;
    align-items: center;
    gap: 7px;
    flex-wrap: wrap;
  }
}
@media (prefers-reduced-motion: reduce) {
  .order-management * {
    scroll-behavior: auto !important;
    transition: none !important;
  }
}
</style>
