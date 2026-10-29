<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { Download, RefreshCw } from 'lucide-vue-next'
import OrderDetailPanel from '../components/OrderDetailPanel.vue'
import {
  adminOrdersApi,
  orderStatusLabels,
  type OrderRecord,
  type OrderOperation,
} from '../api/orders'
import { ApiError } from '../api/http'
import { toCsv } from './csv'
const rows = ref<OrderRecord[]>([]),
  total = ref(0),
  pages = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  orderNo: '',
  status: '',
  userId: '',
  startTime: '',
  endTime: '',
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
    <p v-if="error" class="demo-banner" role="alert">
      {{ error }}<button class="text-action" :disabled="busy" @click="load">重新加载列表</button>
    </p>
    <p v-if="message" class="demo-banner" role="status">{{ message }}</p>
    <section class="panel list-panel" :aria-busy="loading">
      <form class="list-toolbar order-filters" @submit.prevent="search">
        <input
          v-model="query.orderNo"
          maxlength="40"
          placeholder="完整订单编号"
          aria-label="订单编号"
        /><input
          v-model="query.userId"
          pattern="[1-9][0-9]*"
          placeholder="用户编号"
          aria-label="用户编号"
        />
        <select v-model="query.status" aria-label="订单状态">
          <option value="">全部状态</option>
          <option v-for="(label, status) in orderStatusLabels" :key="status" :value="status">
            {{ label }}
          </option>
        </select>
        <label>下单开始<input v-model="query.startTime" type="datetime-local" step="1" /></label
        ><label>下单结束<input v-model="query.endTime" type="datetime-local" step="1" /></label
        ><button class="primary-button" :disabled="busy || loading">查询</button
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
            <tr v-for="row in rows" :key="row.id">
              <td>
                {{ row.orderNo }}<small>{{ row.createTime }}</small>
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
                {{ money(row.payAmount) }}<small>优惠 {{ money(row.discountAmount) }}</small>
              </td>
              <td>
                <span class="status-tag" :class="`order-${row.status}`">{{
                  orderStatusLabels[row.status] || row.status
                }}</span>
              </td>
              <td>
                <button class="text-action" :disabled="busy || detailLoading" @click="open(row.id)">
                  详情与操作
                </button>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6" class="empty-state">
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
        <h2>订单详情</h2>
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
          <label>物流公司<input v-model="carrier" required maxlength="80" :disabled="busy" /></label
          ><label
            >物流单号<input
              v-model="trackingNo"
              required
              maxlength="100"
              pattern="[A-Za-z0-9][A-Za-z0-9_-]{2,99}"
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
</style>
