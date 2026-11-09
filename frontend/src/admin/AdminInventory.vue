<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  ArrowDownToLine,
  ArrowUpFromLine,
  ClipboardCheck,
  Plus,
  RefreshCw,
  Boxes,
  PackageCheck,
  TriangleAlert,
  ScrollText,
  X,
  Check,
  Search,
} from 'lucide-vue-next'
import {
  inventoryApi,
  type InventoryLog,
  type InventoryLogQuery,
  type InventoryMutation,
  type InventoryQuery,
  type InventoryRow,
  type InventoryOptions,
} from '../api/inventory'
import { ApiError } from '../api/http'

const rows = ref<InventoryRow[]>([]),
  logs = ref<InventoryLog[]>([])
const total = ref(0),
  logTotal = ref(0),
  pages = ref(1),
  logPages = ref(1)
const stockLoading = ref(false),
  logLoading = ref(false),
  busy = ref(false),
  notice = ref('')
const stockError = ref(''),
  logError = ref(''),
  actionError = ref('')
const options = ref<InventoryOptions>({ products: [] })
const tab = ref<'stock' | 'logs'>('stock')
const loading = computed(() => (tab.value === 'stock' ? stockLoading.value : logLoading.value))
const error = computed(
  () => actionError.value || (tab.value === 'stock' ? stockError.value : logError.value),
)
const query = reactive<InventoryQuery>({
  page: 1,
  size: 20,
  keyword: '',
  availableMax: '',
})
const logQuery = reactive<InventoryLogQuery>({
  page: 1,
  size: 20,
  changeType: '',
  referenceNo: '',
  inventoryId: '',
})
const mode = ref<'create' | 'inbound' | 'outbound' | 'adjust' | null>(null)
const selected = ref<InventoryRow | null>(null)
const editor = ref<HTMLElement | null>(null)
const projectedQuantity = computed(() => {
  const value = Number(form.quantity) || 0
  return mode.value === 'inbound'
    ? (selected.value?.quantity || 0) + value
    : mode.value === 'outbound'
      ? (selected.value?.quantity || 0) - value
      : value
})
const form = reactive({ productId: '', quantity: 0, requestId: '', remark: '' })
const logTypeNames: Record<string, string> = {
  initial: '期初建档',
  inbound: '入库',
  outbound: '出库',
  adjustment: '盘点',
  order_lock: '订单锁定',
  order_release: '释放锁定',
  order_deduct: '订单扣减',
}
const summary = computed(() => ({
  low: rows.value.filter((row) => row.availableQuantity <= 10).length,
  available: rows.value.reduce((sum, row) => sum + row.availableQuantity, 0),
}))
function explain(e: unknown) {
  return e instanceof ApiError && e.traceId
    ? `${e.message}（请求编号：${e.traceId}）`
    : e instanceof Error
      ? e.message
      : '操作失败，请重试'
}
let stockVersion = 0,
  logVersion = 0
async function loadStock() {
  const current = ++stockVersion
  stockLoading.value = true
  stockError.value = ''
  try {
    const r = await inventoryApi.list({ ...query })
    if (current !== stockVersion) return
    rows.value = r.list
    total.value = r.total
    pages.value = Math.max(1, r.pages)
  } catch (e) {
    if (current === stockVersion) stockError.value = explain(e)
  } finally {
    if (current === stockVersion) stockLoading.value = false
  }
}
async function loadLogs() {
  const current = ++logVersion
  logLoading.value = true
  logError.value = ''
  try {
    const r = await inventoryApi.logs({ ...logQuery })
    if (current !== logVersion) return
    logs.value = r.list
    logTotal.value = r.total
    logPages.value = Math.max(1, r.pages)
  } catch (e) {
    if (current === logVersion) logError.value = explain(e)
  } finally {
    if (current === logVersion) logLoading.value = false
  }
}
function refresh() {
  actionError.value = ''
  return tab.value === 'stock' ? loadStock() : loadLogs()
}
function search() {
  if (tab.value === 'stock') {
    query.page = 1
    void loadStock()
  } else {
    logQuery.page = 1
    void loadLogs()
  }
}
async function open(action: 'create' | 'inbound' | 'outbound' | 'adjust', row?: InventoryRow) {
  if (busy.value) return
  busy.value = true
  actionError.value = ''
  notice.value = ''
  try {
    if (action === 'create') options.value = await inventoryApi.options()
    else if (row) row = await inventoryApi.detail(row.id)
    selected.value = row ?? null
    mode.value = action
    Object.assign(form, {
      productId: row?.productId ?? '',
      quantity: action === 'adjust' ? row!.quantity : action === 'create' ? 0 : 1,
      requestId: `inv-${crypto.randomUUID()}`,
      remark: '',
    })
    await nextTick()
    editor.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    editor.value
      ?.querySelector<HTMLElement>('select, input:not([type=hidden])')
      ?.focus({ preventScroll: true })
  } catch (e) {
    actionError.value = explain(e)
  } finally {
    busy.value = false
  }
}
function showLogs(row: InventoryRow) {
  logQuery.changeType = ''
  logQuery.referenceNo = ''
  logQuery.inventoryId = row.id
  logQuery.page = 1
  tab.value = 'logs'
  void loadLogs()
}
function resetFilters() {
  if (tab.value === 'stock') Object.assign(query, { keyword: '', availableMax: '' })
  else Object.assign(logQuery, { changeType: '', referenceNo: '', inventoryId: '' })
  search()
}
function lowStock() {
  query.availableMax = '10'
  tab.value = 'stock'
  search()
}
function clearLogScope() {
  logQuery.inventoryId = ''
  search()
}
function changeStockPage(delta: number) {
  if (stockLoading.value) return
  query.page = Math.max(1, Math.min(pages.value, query.page + delta))
  void loadStock()
}
function changeLogPage(delta: number) {
  if (logLoading.value) return
  logQuery.page = Math.max(1, Math.min(logPages.value, logQuery.page + delta))
  void loadLogs()
}
async function submit() {
  if (busy.value || !mode.value) return
  actionError.value = ''
  notice.value = ''
  if (
    !Number.isInteger(form.quantity) ||
    form.quantity < (mode.value === 'create' || mode.value === 'adjust' ? 0 : 1) ||
    form.quantity > 2147483647 ||
    !form.remark.trim()
  ) {
    actionError.value = '请输入有效整数数量和非空备注'
    return
  }
  if (mode.value === 'outbound' && form.quantity > selected.value!.availableQuantity) {
    actionError.value = '出库数量不能超过可用库存'
    return
  }
  if (mode.value === 'adjust' && form.quantity < selected.value!.lockedQuantity) {
    actionError.value = '盘点实物数量不能小于订单锁定数量'
    return
  }
  busy.value = true
  try {
    const input: InventoryMutation = {
      quantity: form.quantity,
      requestId: form.requestId,
      remark: form.remark.trim(),
    }
    let createdInventoryId: string | null = null
    if (mode.value === 'create') {
      const created = await inventoryApi.create({
        ...input,
        productId: form.productId.trim(),
      })
      createdInventoryId = created.inventoryId
    } else if (mode.value === 'inbound') await inventoryApi.inbound(selected.value!.id, input)
    else if (mode.value === 'outbound') await inventoryApi.outbound(selected.value!.id, input)
    else await inventoryApi.adjust(selected.value!.id, input)
    mode.value = null
    if (createdInventoryId) {
      query.keyword = ''
      query.availableMax = ''
      query.page = 1
      tab.value = 'stock'
      notice.value = `库存档案 #${createdInventoryId} 已创建，初始流水已记录`
    } else notice.value = '库存变更已保存，流水已记录'
    await Promise.all([loadStock(), loadLogs()])
    if (createdInventoryId) {
      const createdRow = rows.value.find((row) => row.id === createdInventoryId)
      if (createdRow) showLogs(createdRow)
    }
  } catch (e) {
    actionError.value = explain(e)
  } finally {
    busy.value = false
  }
}
function date(value: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}
onMounted(() => {
  void Promise.all([loadStock(), loadLogs()])
})
</script>

<template>
  <div class="admin-page inventory-page">
    <header class="page-heading">
      <div>
        <span class="page-kicker">INVENTORY</span>
        <h1>库存管理</h1>
        <p>跟踪实物、订单锁定和可用库存；每次调整都会保留操作流水。</p>
      </div>
      <button class="primary-button" :disabled="busy" @click="open('create')">
        <Plus :size="16" />新建库存档案
      </button>
    </header>
    <p v-if="error && !mode" class="inventory-alert" role="alert">
      {{ error }}<button class="text-action" :disabled="busy" @click="refresh">重试</button>
    </p>
    <p v-if="notice" class="inventory-notice" role="status">{{ notice }}</p>
    <section class="stats-grid inventory-stats">
      <article class="stat-panel">
        <div class="stat-top">
          库存档案<span class="stat-icon"><Boxes :size="20" /></span>
        </div>
        <strong class="stat-value">{{ total }}</strong>
        <div class="stat-foot">当前筛选结果总数</div>
      </article>
      <article class="stat-panel">
        <div class="stat-top">
          本页可用库存<span class="stat-icon"><PackageCheck :size="20" /></span>
        </div>
        <strong class="stat-value">{{ summary.available }}</strong>
        <div class="stat-foot">已扣除订单锁定数量</div>
      </article>
      <article class="stat-panel">
        <div class="stat-top">
          本页低库存<span class="stat-icon"><TriangleAlert :size="20" /></span>
        </div>
        <strong class="stat-value">{{ summary.low }}</strong>
        <div class="stat-foot">
          可用数量不超过 10 <button class="low-stock-link" @click="lowStock">查看低库存 →</button>
        </div>
      </article>
      <article class="stat-panel">
        <div class="stat-top">
          库存流水<span class="stat-icon"><ScrollText :size="20" /></span>
        </div>
        <strong class="stat-value">{{ logTotal }}</strong>
        <div class="stat-foot">订单与人工操作均留痕</div>
      </article>
    </section>
    <div class="inventory-tabs" role="tablist">
      <button
        role="tab"
        :aria-selected="tab === 'stock'"
        :class="{ active: tab === 'stock' }"
        @click="tab = 'stock'"
      >
        库存台账</button
      ><button
        role="tab"
        :aria-selected="tab === 'logs'"
        :class="{ active: tab === 'logs' }"
        @click="tab = 'logs'"
      >
        变更流水
      </button>
    </div>
    <section v-if="mode" ref="editor" class="panel inventory-editor">
      <div class="panel-heading">
        <div>
          <h2>
            {{
              mode === 'create'
                ? '新建库存档案'
                : mode === 'inbound'
                  ? '库存入库'
                  : mode === 'outbound'
                    ? '库存出库'
                    : '实物盘点'
            }}
          </h2>
          <p v-if="selected">
            {{ selected.productName }} · 当前实物 {{ selected.quantity }}，锁定
            {{ selected.lockedQuantity }}
          </p>
        </div>
        <button
          type="button"
          class="editor-close"
          aria-label="关闭库存表单"
          :disabled="busy"
          @click="mode = null"
        >
          <X :size="19" />
        </button>
      </div>
      <form class="inventory-form" @submit.prevent="submit">
        <label v-if="mode === 'create'"
          >商品<select v-model="form.productId" required :disabled="busy">
            <option disabled value="">请选择商品</option>
            <option v-for="product in options.products" :key="product.id" :value="product.id">
              {{ product.name }} (#{{ product.id }})
            </option>
          </select></label
        ><label
          >{{
            mode === 'adjust'
              ? '盘点后的实物数量'
              : mode === 'create'
                ? '初始库存数量'
                : mode === 'inbound'
                  ? '本次入库数量'
                  : '本次出库数量'
          }}<input
            v-model.number="form.quantity"
            :disabled="busy"
            required
            type="number"
            :min="mode === 'adjust' || mode === 'create' ? 0 : 1"
            max="2147483647"
            step="1" /></label
        ><label
          >操作备注<textarea
            v-model="form.remark"
            :disabled="busy"
            required
            maxlength="255"
            rows="2"
            placeholder="请填写本次操作原因，例如：采购到货入库"
          /></label
        ><input v-model="form.requestId" type="hidden" />
        <div class="stock-preview">
          <PackageCheck :size="18" /><span
            >操作后实物库存 <strong>{{ projectedQuantity }}</strong></span
          ><small v-if="selected"
            >当前可用 {{ selected.availableQuantity }} · 订单锁定
            {{ selected.lockedQuantity }}</small
          >
        </div>
        <p v-if="actionError" class="inventory-alert form-error" role="alert">{{ actionError }}</p>
        <div class="inventory-form-actions">
          <button class="primary-button" :disabled="busy">
            {{ busy ? '提交中…' : '确认并记录流水' }}</button
          ><button type="button" class="secondary-button" :disabled="busy" @click="mode = null">
            取消
          </button>
        </div>
      </form>
    </section>
    <section v-if="tab === 'stock'" class="panel list-panel" :aria-busy="loading">
      <form class="list-toolbar inventory-toolbar" @submit.prevent="search">
        <label class="table-search"
          ><Search :size="16" /><input
            v-model="query.keyword"
            type="search"
            maxlength="200"
            placeholder="按商品名称搜索"
            aria-label="商品名称" /></label
        ><label class="inventory-low-filter"
          >可用库存 ≤
          <input
            v-model="query.availableMax"
            type="number"
            min="0"
            max="2147483647"
            placeholder="不限"
            aria-label="可用库存上限" /></label
        ><button class="primary-button" :disabled="loading">查询</button
        ><button type="button" class="reset-button" :disabled="loading" @click="resetFilters">
          重置</button
        ><button
          type="button"
          class="secondary-button"
          aria-label="刷新库存"
          :disabled="loading"
          @click="refresh"
        >
          <RefreshCw :size="15" />
        </button>
      </form>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>商品</th>
              <th>实物库存</th>
              <th>订单锁定</th>
              <th>可用库存</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>
                <strong>{{ row.productName }}</strong
                ><small>商品 #{{ row.productId }} · 库存档案 #{{ row.id }}</small>
              </td>
              <td>{{ row.quantity }}</td>
              <td>{{ row.lockedQuantity }}</td>
              <td class="amount-cell">
                <span class="available-value" :class="{ low: row.availableQuantity <= 10 }">{{
                  row.availableQuantity
                }}</span>
              </td>
              <td>
                <span
                  class="status-tag"
                  :class="row.availableQuantity <= 10 ? 'status-库存偏低' : 'status-正常'"
                  >{{ row.availableQuantity <= 10 ? '库存偏低' : '正常' }}</span
                >
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-action" :disabled="busy" @click="open('inbound', row)">
                    <ArrowDownToLine :size="14" />入库</button
                  ><button class="text-action" :disabled="busy" @click="open('outbound', row)">
                    <ArrowUpFromLine :size="14" />出库</button
                  ><button class="text-action" :disabled="busy" @click="open('adjust', row)">
                    <ClipboardCheck :size="14" />盘点
                  </button>
                  <button class="text-action" :disabled="busy" @click="showLogs(row)">流水</button>
                </div>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6" class="empty-state">
                {{ loading ? '正在加载库存…' : error ? '加载失败，请重试' : '暂无库存记录' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="pagination">
        <span>共 {{ total }} 条库存</span>
        <div>
          <select v-model.number="query.size" aria-label="每页数量" @change="search">
            <option :value="10">10 条 / 页</option>
            <option :value="20">20 条 / 页</option>
            <option :value="50">50 条 / 页</option></select
          ><button :disabled="loading || query.page <= 1" @click="changeStockPage(-1)">
            上一页</button
          ><span>{{ query.page }} / {{ pages }}</span
          ><button :disabled="loading || query.page >= pages" @click="changeStockPage(1)">
            下一页
          </button>
        </div>
      </footer>
    </section>
    <section v-else class="panel list-panel" :aria-busy="loading">
      <form class="list-toolbar inventory-toolbar" @submit.prevent="search">
        <span v-if="logQuery.inventoryId"
          >库存 #{{ logQuery.inventoryId }}
          <button type="button" class="text-action" @click="clearLogScope">查看全部</button></span
        ><select v-model="logQuery.changeType" aria-label="流水类型">
          <option value="">全部类型</option>
          <option v-for="(label, key) in logTypeNames" :key="key" :value="key">
            {{ label }}
          </option></select
        ><input
          v-model="logQuery.referenceNo"
          class="inventory-reference-filter"
          maxlength="64"
          placeholder="订单编号"
          aria-label="订单编号"
        /><button class="primary-button" :disabled="loading">查询</button
        ><button type="button" class="reset-button" :disabled="loading" @click="resetFilters">
          重置</button
        ><button
          type="button"
          class="secondary-button"
          aria-label="刷新流水"
          :disabled="loading"
          @click="refresh"
        >
          <RefreshCw :size="15" />
        </button>
      </form>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>商品 / 库存档案</th>
              <th>变更类型</th>
              <th>数量变化</th>
              <th>实物库存</th>
              <th>锁定数量</th>
              <th>操作人 / 请求编号</th>
              <th>关联订单</th>
              <th>备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td>{{ date(log.createTime) }}</td>
              <td>
                商品 #{{ log.productId }}<small>档案 #{{ log.inventoryId || '—' }}</small>
              </td>
              <td>
                <span class="status-tag">{{ logTypeNames[log.changeType] || log.changeType }}</span>
              </td>
              <td :class="log.changeQuantity < 0 ? 'inventory-negative' : 'inventory-positive'">
                {{ log.changeQuantity > 0 ? '+' : '' }}{{ log.changeQuantity }}
              </td>
              <td>{{ log.quantityBefore ?? '—' }} → {{ log.quantityAfter ?? '—' }}</td>
              <td>{{ log.lockedBefore ?? '—' }} → {{ log.lockedAfter ?? '—' }}</td>
              <td>
                {{ log.operatorId || '系统' }}<small>{{ log.requestId || '—' }}</small>
              </td>
              <td>{{ log.referenceNo || '—' }}</td>
              <td class="inventory-remark">{{ log.remark || '—' }}</td>
            </tr>
            <tr v-if="!logs.length">
              <td colspan="9" class="empty-state">
                {{ loading ? '正在加载流水…' : error ? '加载失败，请重试' : '暂无库存流水' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="pagination">
        <span>共 {{ logTotal }} 条流水</span>
        <div>
          <select v-model.number="logQuery.size" aria-label="每页数量" @change="search">
            <option :value="10">10 条 / 页</option>
            <option :value="20">20 条 / 页</option>
            <option :value="50">50 条 / 页</option></select
          ><button :disabled="loading || logQuery.page <= 1" @click="changeLogPage(-1)">
            上一页</button
          ><span>{{ logQuery.page }} / {{ logPages }}</span
          ><button :disabled="loading || logQuery.page >= logPages" @click="changeLogPage(1)">
            下一页
          </button>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.inventory-stats {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.inventory-tabs {
  display: flex;
  gap: 18px;
  border-bottom: 1px solid #e8ece8;
}
.inventory-tabs button {
  padding: 10px 2px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #75837b;
  font: inherit;
  cursor: pointer;
}
.inventory-tabs button.active {
  color: #257251;
  border-bottom-color: #257251;
  font-weight: 600;
}
.inventory-toolbar {
  flex-wrap: wrap;
}
.inventory-toolbar > select,
.inventory-reference-filter,
.inventory-low-filter {
  height: 33px;
  border: 1px solid #e8ece8;
  border-radius: 4px;
  padding: 0 9px;
  background: #fff;
  color: #4c5c51;
  font: inherit;
}
.inventory-low-filter {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #75837b;
  white-space: nowrap;
}
.inventory-low-filter input {
  width: 68px;
  border: 0;
  outline: 0;
  font: inherit;
}
.inventory-reference-filter {
  width: 180px;
}
.inventory-editor {
  padding: 18px;
}
.inventory-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}
.inventory-form label {
  display: grid;
  gap: 6px;
  color: #5c6c61;
}
.inventory-form select,
.inventory-form input:not([type='hidden']) {
  min-width: 0;
  height: 36px;
  border: 1px solid #dce4df;
  border-radius: 4px;
  padding: 0 10px;
  font: inherit;
}
.inventory-form-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: 8px;
}
.inventory-page .admin-table {
  min-width: 900px;
}
.inventory-page .admin-table td small {
  display: block;
  margin-top: 4px;
  color: #87928a;
}
.inventory-page .row-actions {
  min-width: 245px;
  gap: 12px;
}
.inventory-page .row-actions .text-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.status-库存偏低 {
  background: #fff5e5;
  color: #a7792d;
}
.status-正常 {
  background: #eaf4ed;
  color: #34734f;
}
.inventory-negative {
  color: #b24c40 !important;
  font-weight: 600;
}
.inventory-positive {
  color: #28734f !important;
  font-weight: 600;
}
.inventory-remark {
  max-width: 220px;
  white-space: normal;
}
@media (max-width: 700px) {
  .inventory-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .inventory-form {
    grid-template-columns: 1fr;
  }
  .inventory-form-actions {
    grid-column: auto;
  }
  .inventory-toolbar > * {
    max-width: 100%;
  }
}

/* Soft surfaces and consistent everyday controls. */
.inventory-page {
  --stock-green: #5b876b;
  --stock-line: #e5ece3;
  --stock-muted: #89968a;
}
.inventory-page .page-heading {
  margin-bottom: 26px;
}
.inventory-page .page-heading h1 {
  color: #2e4837;
}
.inventory-page .page-heading p {
  color: #8a978b;
  line-height: 1.8;
}
.inventory-page .primary-button,
.inventory-page .secondary-button {
  border-radius: 9px;
  min-height: 38px;
  gap: 7px;
}
.inventory-page .primary-button {
  background: #5b876b;
  box-shadow: 0 3px 9px #5b876b18;
}
.inventory-page .primary-button:hover {
  background: #497658;
}
.inventory-stats {
  gap: 16px;
  margin-bottom: 26px;
}
.inventory-page .stat-panel {
  position: relative;
  padding: 22px 24px;
  background: linear-gradient(145deg, #fff, #f8fbf6);
  border: 1px solid #e5ece2;
  border-radius: 16px;
  box-shadow: 0 4px 18px #294b3407;
}
.inventory-page .stat-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #7c8e7b;
  font-size: 12px;
}
.stat-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: #edf5e9;
  color: #7a9a6a;
}
.inventory-page .stat-panel:nth-child(2) .stat-icon {
  background: #eaf4ef;
  color: #6f9a83;
}
.inventory-page .stat-panel:nth-child(3) .stat-icon {
  background: #fbf1df;
  color: #bea06a;
}
.inventory-page .stat-panel:nth-child(4) .stat-icon {
  background: #edf0f8;
  color: #939bb8;
}
.inventory-page .stat-value {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -1px;
  color: #3e5d43;
  font-variant-numeric: tabular-nums;
}
.inventory-page .stat-foot {
  font-size: 11px;
  color: #98a18f;
  line-height: 1.8;
}
.low-stock-link {
  display: block;
  padding: 3px 0 0;
  border: 0;
  background: none;
  color: #ad9262;
  font-size: 11px;
  cursor: pointer;
}
.inventory-tabs {
  width: fit-content;
  padding: 5px;
  background: #eaf0e7;
  border: 1px solid #e1e8dc;
  border-radius: 11px;
  gap: 4px;
  margin: 0 0 18px;
}
.inventory-tabs button {
  padding: 9px 23px;
  border: 0;
  border-radius: 8px;
  color: #86947e;
  font-size: 13px;
}
.inventory-tabs button.active {
  background: #fff;
  color: #527a58;
  box-shadow: 0 2px 5px #304c3310;
}
.inventory-page .list-panel {
  border: 1px solid var(--stock-line);
  border-radius: 16px;
  box-shadow: 0 5px 24px #294b3407;
  overflow: hidden;
}
.inventory-toolbar {
  padding: 20px 24px;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #eef2eb;
}
.inventory-toolbar .table-search {
  background: #fafcf8;
  border: 1px solid #e2e9de;
  border-radius: 9px;
  min-height: 39px;
  flex: 1;
  max-width: 340px;
}
.inventory-toolbar .table-search input {
  background: transparent;
}
.inventory-toolbar > select,
.inventory-reference-filter,
.inventory-low-filter {
  height: 39px;
  border-radius: 9px;
  border-color: #e3eade;
  background: #fafcf8;
  padding: 0 12px;
}
.inventory-low-filter input {
  background: transparent;
  width: 64px;
}
.reset-button {
  border: 0;
  background: transparent;
  padding: 9px;
  color: #89957f;
  cursor: pointer;
}
.reset-button:hover {
  color: #527a58;
}
.inventory-toolbar:focus-within {
  outline: none;
}
.inventory-page .admin-table th {
  background: #f5f8f2;
  color: #8a9681;
  font-size: 11px;
  font-weight: 500;
  padding-top: 15px;
  padding-bottom: 15px;
}
.inventory-page .admin-table td {
  padding-top: 19px;
  padding-bottom: 19px;
  border-color: #eff3eb;
  color: #6d7c63;
  font-variant-numeric: tabular-nums;
}
.inventory-page .admin-table tbody tr:hover {
  background: #fbfdf8;
}
.inventory-page .admin-table td strong {
  font-weight: 500;
  color: #4a6244;
}
.inventory-page .admin-table td small {
  color: #a6af9c;
  font-size: 10px;
  margin-top: 7px;
}
.inventory-page .row-actions {
  gap: 5px;
}
.inventory-page .row-actions .text-action {
  padding: 7px 8px;
  border-radius: 7px;
  color: #75916c;
  font-size: 12px;
  white-space: nowrap;
}
.inventory-page .row-actions .text-action:hover {
  background: #edf4e8;
}
.inventory-page .row-actions .text-action:nth-child(2) {
  color: #ae936d;
}
.available-value {
  display: inline-block;
  min-width: 35px;
  padding: 5px 9px;
  border-radius: 7px;
  background: #edf5e9;
  color: #6a8e58;
  text-align: center;
  font-weight: 600;
}
.available-value.low {
  background: #fbf2e3;
  color: #b09258;
}
.inventory-page .status-tag {
  border-radius: 20px;
  font-size: 11px;
  padding: 5px 10px;
  font-weight: 400;
}
.inventory-page .pagination {
  padding: 18px 24px;
  border-top: 1px solid #edf1e8;
  color: #98a18f;
}
.inventory-page .pagination select,
.inventory-page .pagination button {
  border-radius: 7px;
  border-color: #e5ebdf;
  min-height: 32px;
}
.inventory-editor {
  border: 1px solid #dfe9da !important;
  border-radius: 16px;
  background: linear-gradient(155deg, #fff, #fcfdf9);
  box-shadow: 0 8px 28px #4764440b;
  padding: 26px;
  margin-bottom: 22px;
  scroll-margin-top: 24px;
}
.inventory-editor .panel-heading {
  padding: 0 0 20px;
  border-bottom: 1px solid #edf1e7;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.inventory-editor h2 {
  font-size: 18px;
  color: #48613e;
  margin: 0 0 9px;
}
.inventory-editor .panel-heading p {
  font-size: 12px;
  color: #95a087;
  margin: 0;
  line-height: 1.8;
}
.editor-close {
  width: 31px;
  height: 31px;
  display: grid;
  place-items: center;
  border: 1px solid #e7ecdf;
  border-radius: 9px;
  color: #99a38b;
  background: #f8faf4;
  cursor: pointer;
}
.inventory-form {
  gap: 20px;
  margin-top: 24px;
}
.inventory-form label {
  gap: 10px;
  color: #7a8b6a;
  font-size: 12px;
}
.inventory-form select,
.inventory-form input:not([type='hidden']),
.inventory-form textarea {
  height: 43px;
  border: 1px solid #e0e8d8;
  border-radius: 9px;
  background: #fafcf7;
  color: #5e7250;
  padding: 10px 12px;
  font: inherit;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
}
.inventory-form textarea {
  height: auto;
  min-height: 80px;
  resize: vertical;
}
.inventory-form input::placeholder,
.inventory-form textarea::placeholder {
  color: #adb69f;
}
.inventory-page input:focus-visible,
.inventory-page select:focus-visible,
.inventory-page textarea:focus-visible {
  outline: 2px solid #91b17e;
  outline-offset: 2px;
}
.inventory-page button:focus-visible {
  outline: 2px solid #91b17e;
  outline-offset: 3px;
}
.inventory-form label:has(textarea) {
  grid-column: 1/-1;
}
.stock-preview {
  grid-column: 1/-1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 15px 18px;
  border-radius: 10px;
  background: #f0f6e9;
  color: #879a73;
  font-size: 12px;
}
.stock-preview strong {
  font-size: 20px;
  color: #65834b;
  padding-left: 10px;
  font-variant-numeric: tabular-nums;
}
.stock-preview small {
  margin-left: auto;
  font-size: 11px;
  color: #9ca98b;
}
.inventory-form-actions {
  border-top: 1px solid #edf1e7;
  padding-top: 20px;
  justify-content: flex-end;
  gap: 10px;
}
.inventory-form-actions .secondary-button {
  order: -1;
}
.inventory-alert {
  padding: 13px 16px;
  border: 1px solid #f0ddd6;
  background: #fcf3ef;
  color: #af8170;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.8;
}
.form-error {
  grid-column: 1/-1;
  margin: 0;
}
.inventory-notice {
  padding: 13px 16px;
  border: 1px solid #dfeadb;
  background: #f0f7ec;
  color: #759263;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.8;
}
.inventory-page button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@media (max-width: 1050px) {
  .inventory-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .inventory-page .stat-panel {
    padding: 18px 20px;
  }
}
@media (max-width: 600px) {
  .inventory-stats {
    gap: 10px;
  }
  .inventory-page .stat-panel {
    padding: 14px;
    border-radius: 12px;
  }
  .stat-icon {
    width: 29px;
    height: 29px;
    border-radius: 9px;
  }
  .inventory-page .stat-value {
    font-size: 26px;
  }
  .inventory-page .stat-foot {
    font-size: 10px;
  }
  .inventory-toolbar {
    padding: 16px;
    gap: 10px;
  }
  .inventory-toolbar .table-search {
    max-width: none;
    flex-basis: 100%;
  }
  .inventory-low-filter {
    flex: 1;
  }
  .inventory-editor {
    padding: 20px 16px;
  }
  .stock-preview {
    flex-wrap: wrap;
  }
  .stock-preview small {
    margin-left: 28px;
    width: 100%;
  }
  .inventory-page .pagination {
    padding: 14px;
    flex-wrap: wrap;
    gap: 12px;
  }
  .inventory-page .pagination > div {
    display: flex;
    gap: 7px;
    align-items: center;
    flex-wrap: wrap;
  }
  .inventory-tabs {
    width: auto;
  }
  .inventory-tabs button {
    flex: 1;
  }
  .inventory-form-actions button {
    flex: 1;
    justify-content: center;
  }
}
@media (prefers-reduced-motion: reduce) {
  .inventory-page * {
    scroll-behavior: auto !important;
    transition: none !important;
  }
}
</style>
