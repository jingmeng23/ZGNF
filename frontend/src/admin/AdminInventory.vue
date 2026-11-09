<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ArrowDownToLine,
  ArrowUpFromLine,
  ClipboardCheck,
  Plus,
  RefreshCw,
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
    <p v-if="error" class="demo-banner" role="alert">
      {{ error }}<button class="text-action" :disabled="busy" @click="refresh">重试</button>
    </p>
    <p v-if="notice" class="demo-banner" role="status">{{ notice }}</p>
    <section class="stats-grid inventory-stats">
      <article class="stat-panel">
        <div class="stat-top">库存档案</div>
        <strong class="stat-value">{{ total }}</strong>
        <div class="stat-foot">当前筛选结果总数</div>
      </article>
      <article class="stat-panel">
        <div class="stat-top">本页可用库存</div>
        <strong class="stat-value">{{ summary.available }}</strong>
        <div class="stat-foot">已扣除订单锁定数量</div>
      </article>
      <article class="stat-panel">
        <div class="stat-top">本页低库存</div>
        <strong class="stat-value">{{ summary.low }}</strong>
        <div class="stat-foot">可用数量不超过 10</div>
      </article>
      <article class="stat-panel">
        <div class="stat-top">库存流水</div>
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
    <section v-if="mode" class="panel inventory-editor">
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
            {{ selected.productName }} · 当前实物
            {{ selected.quantity }}，锁定 {{ selected.lockedQuantity }}
          </p>
        </div>
        <button class="secondary-button" :disabled="busy" @click="mode = null">关闭</button>
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
          >{{ mode === 'adjust' ? '盘点后的实物数量' : '数量'
          }}<input
            v-model.number="form.quantity"
            :disabled="busy"
            required
            type="number"
            :min="mode === 'adjust' || mode === 'create' ? 0 : 1"
            max="2147483647"
            step="1" /></label
        ><label
          >操作备注<input v-model="form.remark" :disabled="busy" required maxlength="255" /></label
        ><input v-model="form.requestId" type="hidden" />
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
              <td class="amount-cell">{{ row.availableQuantity }}</td>
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
              <th>商品 / 仓库</th>
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
                商品 #{{ log.productId
                }}<small
                  >档案 #{{
                    log.inventoryId || '—'
                  }}</small
                >
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
</style>
