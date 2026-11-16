<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ArrowDownLeft,
  ArrowRight,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Clock3,
  Download,
  Info,
  LoaderCircle,
  RefreshCw,
  Search,
  ShieldCheck,
  Wallet,
  X,
  XCircle,
} from 'lucide-vue-next'
import {
  adminAftersalesApi,
  aftersaleLabel,
  type AftersaleRecord,
  type AftersaleOperation,
  type AftersaleQuery,
} from '../api/aftersales'
import { ApiError } from '../api/http'
import AftersaleDetailPanel from '../components/AftersaleDetailPanel.vue'
import { can } from './permissions'
import { toCsv } from './csv'

const rows = ref<AftersaleRecord[]>([])
const total = ref(0),
  pages = ref(1),
  loading = ref(false),
  listError = ref('')
const notice = ref(''),
  summaryError = ref(false),
  summaryLoading = ref(false)
const counts = reactive<Record<string, number | null>>({
  all: null,
  pending: null,
  approved: null,
  completed: null,
})
const cards = [
  { key: 'all', status: '', title: '全部申请', hint: '查看所有售后记录', icon: ClipboardList },
  { key: 'pending', status: 'pending', title: '待审核', hint: '核实原因，及时响应', icon: Clock3 },
  {
    key: 'approved',
    status: 'approved',
    title: '待退款',
    hint: '审核已通过，待处理退款',
    icon: Wallet,
  },
  {
    key: 'completed',
    status: 'completed',
    title: '已完成',
    hint: '退款结果已记录',
    icon: CheckCircle2,
  },
]
const tabs = [
  { status: '', label: '全部申请' },
  { status: 'pending', label: '待审核' },
  { status: 'approved', label: '待退款' },
  { status: 'completed', label: '已完成' },
  { status: 'rejected', label: '已拒绝 / 已撤销' },
]
const filters = reactive({ aftersaleNo: '', orderNo: '' })
const sortChoice = ref('newest')
const route = useRoute()
function routeStatus() {
  const value = route.query.status
  return typeof value === 'string' &&
    ['pending', 'approved', 'rejected', 'completed'].includes(value)
    ? value
    : ''
}
const query = reactive<AftersaleQuery>({
  page: 1,
  size: 10,
  status: routeStatus(),
  aftersaleNo: '',
  orderNo: '',
  sort: 'createTime',
  order: 'desc',
})
const hasFilters = computed(() => !!(query.status || query.aftersaleNo || query.orderNo))
const rangeStart = computed(() => (total.value ? (query.page - 1) * query.size + 1 : 0))
const rangeEnd = computed(() => Math.min(query.page * query.size, total.value))
const canWrite = computed(() => can('aftersale:write'))
const drawer = ref<HTMLDialogElement>()
const detailVisible = ref(false),
  detailLoading = ref(false),
  busy = ref(false)
const selected = ref<AftersaleRecord | null>(null),
  operations = ref<AftersaleOperation[]>([])
const detailError = ref(''),
  actionError = ref(''),
  historyError = ref('')
const decision = ref<'approved' | 'rejected'>('approved'),
  reviewRemark = ref('')
const refundMethod = ref<'wechat' | 'manual'>('wechat')
const refundReference = ref(''),
  completionRemark = ref(''),
  confirmed = ref(false)
const hasDraft = computed(() =>
  selected.value?.status === 'pending'
    ? !!reviewRemark.value.trim()
    : selected.value?.status === 'approved' &&
      !!(refundReference.value.trim() || completionRemark.value.trim() || confirmed.value),
)
const steps = computed(() =>
  selected.value?.status === 'rejected'
    ? ['提交申请', aftersaleLabel(selected.value), '流程结束']
    : ['提交申请', '审核通过', '退款完成'],
)
const currentStep = computed(() =>
  selected.value?.status === 'pending' ? 0 : selected.value?.status === 'approved' ? 1 : 2,
)
let listVersion = 0,
  detailVersion = 0,
  summaryVersion = 0
let previousOverflow = ''
const money = (value: number) =>
  `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const dateTime = (value: string | null) => (value ? value.replace('T', ' ').slice(0, 19) : '—')
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
  listError.value = ''
  try {
    const result = await adminAftersalesApi.list({ ...query })
    if (version !== listVersion) return
    const lastPage = Math.max(1, result.pages)
    if (query.page > lastPage) {
      query.page = lastPage
      await load()
      return
    }
    rows.value = result.list
    total.value = result.total
    pages.value = lastPage
  } catch (e) {
    if (version !== listVersion) return
    listError.value = explain(e)
    rows.value = []
    total.value = 0
    pages.value = 1
  } finally {
    if (version === listVersion) loading.value = false
  }
}
async function loadSummary() {
  const version = ++summaryVersion
  summaryLoading.value = true
  const results = await Promise.allSettled(
    cards.map((card) =>
      adminAftersalesApi.list({
        page: 1,
        size: 1,
        status: card.status,
        aftersaleNo: '',
        orderNo: '',
      }),
    ),
  )
  if (version !== summaryVersion) return
  results.forEach((result, index) => {
    counts[cards[index].key] = result.status === 'fulfilled' ? result.value.total : null
  })
  summaryError.value = results.some((result) => result.status === 'rejected')
  summaryLoading.value = false
}
async function refresh() {
  await Promise.allSettled([load(), loadSummary()])
}
function search() {
  if (busy.value) return
  Object.assign(query, {
    page: 1,
    aftersaleNo: filters.aftersaleNo.trim(),
    orderNo: filters.orderNo.trim(),
  })
  void load()
}
function selectStatus(status: string) {
  if (busy.value) return
  query.status = status
  query.page = 1
  void load()
}
watch(
  () => route.query.status,
  (status) => {
    const nextStatus = routeStatus()
    if (nextStatus === query.status) return
    query.status = nextStatus
    query.page = 1
    void load()
  },
)
function resetFilters() {
  if (busy.value) return
  Object.assign(filters, { aftersaleNo: '', orderNo: '' })
  Object.assign(query, { status: '', sort: 'createTime', order: 'desc' })
  sortChoice.value = 'newest'
  search()
}
function changeSort() {
  query.sort = sortChoice.value.startsWith('amount') ? 'amount' : 'createTime'
  query.order = ['oldest', 'amount-asc'].includes(sortChoice.value) ? 'asc' : 'desc'
  query.page = 1
  void load()
}
function changePageSize() {
  query.page = 1
  void load()
}
function page(delta: number) {
  if (loading.value || busy.value) return
  query.page = Math.max(1, Math.min(pages.value, query.page + delta))
  void load()
}
function resetEditor() {
  decision.value = 'approved'
  reviewRemark.value = ''
  refundMethod.value = 'wechat'
  refundReference.value = ''
  completionRemark.value = ''
  confirmed.value = false
}
async function open(id: string) {
  if (busy.value || detailLoading.value) return
  const version = ++detailVersion
  const switching = selected.value?.id !== id
  if (switching) {
    notice.value = ''
    selected.value = null
    operations.value = []
    resetEditor()
  }
  detailLoading.value = true
  detailError.value = actionError.value = historyError.value = ''
  if (!detailVisible.value) {
    detailVisible.value = true
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    await nextTick()
    drawer.value?.showModal()
  }
  try {
    const [record, history] = await Promise.allSettled([
      adminAftersalesApi.detail(id),
      adminAftersalesApi.operations(id),
    ])
    if (version !== detailVersion) return
    if (record.status === 'rejected') throw record.reason
    if (selected.value && selected.value.status !== record.value.status) resetEditor()
    selected.value = record.value
    if (history.status === 'fulfilled') operations.value = history.value
    else {
      operations.value = []
      historyError.value = explain(history.reason)
    }
  } catch (e) {
    if (version === detailVersion) detailError.value = explain(e)
  } finally {
    if (version === detailVersion) detailLoading.value = false
  }
}
function closeDetail() {
  if (busy.value) return
  if (hasDraft.value && !window.confirm('已填写的处理内容尚未提交，确认关闭并放弃这些内容？'))
    return
  detailVersion++
  drawer.value?.close()
  detailVisible.value = false
  detailLoading.value = false
  selected.value = null
  operations.value = []
  resetEditor()
  document.body.style.overflow = previousOverflow
}
async function syncAfterAction(record: AftersaleRecord) {
  selected.value = record
  resetEditor()
  const version = detailVersion
  const history = adminAftersalesApi
    .operations(record.id)
    .then((result) => {
      if (version === detailVersion) {
        operations.value = result
        historyError.value = ''
      }
    })
    .catch((e) => {
      if (version === detailVersion) historyError.value = explain(e)
    })
  await Promise.allSettled([refresh(), history])
}
async function act(action: 'review' | 'wechat' | 'complete') {
  if (!selected.value || busy.value || detailLoading.value || detailError.value || !canWrite.value)
    return
  const row = selected.value
  actionError.value = ''
  if (action === 'review') {
    if (row.status !== 'pending') return
    if (!reviewRemark.value.trim()) {
      actionError.value = '请填写审核意见，说明处理依据。'
      return
    }
  } else {
    if (row.status !== 'approved') return
    if (action === 'complete' && (!confirmed.value || !refundReference.value.trim())) {
      actionError.value = '请填写真实退款流水号，并确认退款已到账。'
      return
    }
  }
  const question =
    action === 'review'
      ? `${decision.value === 'approved' ? '确认同意此退款申请？审核通过后仍需处理退款。' : '确认拒绝此申请？审核意见将向申请人展示。'}\n售后单：${row.aftersaleNo}\n退款金额：${money(row.amount)}`
      : action === 'wechat'
        ? `确认将 ${money(row.amount)} 原路退回此订单的微信付款账户？\n售后单：${row.aftersaleNo}\n退款成功后不可撤销；处理中可再次查询结果。`
        : `确认已通过支付渠道实际退还 ${money(row.amount)}？\n售后单：${row.aftersaleNo}\n流水号：${refundReference.value.trim()}\n登记后将同步订单与支付状态。`
  if (!window.confirm(question)) return
  busy.value = true
  notice.value = ''
  try {
    const record =
      action === 'review'
        ? await adminAftersalesApi.review(row.id, decision.value, reviewRemark.value.trim())
        : action === 'wechat'
          ? await adminAftersalesApi.wechatRefund(row.id)
          : await adminAftersalesApi.complete(
              row.id,
              refundReference.value.trim(),
              completionRemark.value.trim(),
            )
    notice.value =
      action === 'review'
        ? record.status === 'approved'
          ? '审核已通过，请继续处理退款。'
          : '已拒绝申请，审核意见已保存。'
        : action === 'wechat'
          ? '微信已确认退款成功，订单状态已同步。'
          : '退款完成记录已保存，订单状态已同步。'
    await syncAfterAction(record)
  } catch (e) {
    actionError.value = explain(e)
  } finally {
    busy.value = false
  }
}
function exportPage() {
  if (!rows.value.length || loading.value) return
  const columns = [
    { key: 'aftersaleNo', label: '售后单号' },
    { key: 'orderNo', label: '订单编号' },
    { key: 'reason', label: '申请原因' },
    { key: 'amount', label: '整单退款金额' },
    { key: 'status', label: '售后状态' },
    { key: 'createTime', label: '申请时间' },
  ]
  const data = rows.value.map((row) => ({
    id: row.id,
    aftersaleNo: row.aftersaleNo,
    orderNo: row.orderNo || row.orderId,
    reason: row.reason,
    amount: Number(row.amount).toFixed(2),
    status: aftersaleLabel(row),
    createTime: dateTime(row.createTime),
  }))
  const url = URL.createObjectURL(
    new Blob(['\ufeff' + toCsv(columns, data)], { type: 'text/csv;charset=utf-8' }),
  )
  const link = document.createElement('a')
  link.href = url
  link.download = `售后申请-第${query.page}页.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
onMounted(refresh)
onUnmounted(() => {
  listVersion++
  detailVersion++
  summaryVersion++
  if (detailVisible.value) document.body.style.overflow = previousOverflow
})
</script>

<template>
  <div class="admin-page aftersales-page">
    <header class="page-heading">
      <div>
        <span class="page-kicker">AFTERSALES</span>
        <h1>售后管理</h1>
        <p>及时响应每一份申请，让售后处理清晰有序。</p>
      </div>
      <div class="heading-actions">
        <button
          class="secondary-button"
          :disabled="loading || busy || !rows.length"
          @click="exportPage"
        >
          <Download :size="15" />导出本页
        </button>
        <button
          class="secondary-button"
          :disabled="loading || busy || summaryLoading"
          @click="refresh"
        >
          <RefreshCw :size="15" :class="{ spinning: loading }" />刷新
        </button>
      </div>
    </header>
    <div class="summary-grid" aria-label="全部售后概览" :aria-busy="summaryLoading">
      <button
        v-for="card in cards"
        :key="card.key"
        class="summary-card"
        :class="[card.key, { active: query.status === card.status }]"
        :aria-pressed="query.status === card.status"
        :disabled="busy"
        @click="selectStatus(card.status)"
      >
        <span class="summary-text"
          ><span>{{ card.title }}</span
          ><strong>{{ counts[card.key]?.toLocaleString('zh-CN') ?? '—' }}<small>单</small></strong
          ><small>{{ card.hint }}</small></span
        >
        <span class="summary-icon"
          ><component :is="card.icon" :size="21" :stroke-width="1.7" /></span
        ><ArrowRight class="summary-arrow" :size="15" />
      </button>
    </div>
    <p v-if="summaryError" class="summary-retry">
      部分概览暂时无法加载。<button :disabled="summaryLoading" @click="loadSummary">
        重新加载
      </button>
    </p>
    <div class="workflow-hint">
      <ShieldCheck :size="18" /><span
        >处理流程：<strong>审核申请</strong><ChevronRight :size="13" /><strong>处理退款</strong
        ><ChevronRight :size="13" /><strong>完成售后</strong></span
      ><small>按整单实付金额退款</small>
    </div>
    <p v-if="notice && !detailVisible" role="status" class="feedback success">
      <CheckCircle2 :size="17" />{{ notice
      }}<button aria-label="关闭提示" @click="notice = ''"><X :size="15" /></button>
    </p>
    <section class="panel list-panel aftersale-list" aria-label="售后申请列表">
      <nav class="status-tabs" aria-label="按售后状态筛选">
        <button
          v-for="tab in tabs"
          :key="tab.status"
          :class="{ active: query.status === tab.status }"
          :aria-pressed="query.status === tab.status"
          :disabled="busy"
          @click="selectStatus(tab.status)"
        >
          {{ tab.label
          }}<span v-if="counts[tab.status || 'all'] != null">{{
            counts[tab.status || 'all']
          }}</span>
        </button>
      </nav>
      <form class="aftersale-filters" @submit.prevent="search">
        <label
          ><span>售后单号</span
          ><input
            v-model="filters.aftersaleNo"
            maxlength="40"
            placeholder="输入完整售后单号"
            :disabled="busy"
        /></label>
        <label
          ><span>订单编号</span
          ><input
            v-model="filters.orderNo"
            maxlength="40"
            placeholder="输入完整订单编号"
            :disabled="busy"
        /></label>
        <div class="filter-actions">
          <button class="primary-button" :disabled="loading || busy">
            <Search :size="15" />查询</button
          ><button
            type="button"
            class="secondary-button"
            :disabled="loading || busy"
            @click="resetFilters"
          >
            重置
          </button>
        </div>
      </form>
      <div class="list-caption">
        <span
          ><strong>{{ tabs.find((tab) => tab.status === query.status)?.label }}</strong
          ><span class="result-count">共 {{ total }} 条</span></span
        ><label
          >排序<select v-model="sortChoice" :disabled="loading || busy" @change="changeSort">
            <option value="newest">申请时间：从新到旧</option>
            <option value="oldest">申请时间：从旧到新</option>
            <option value="amount-desc">退款金额：从高到低</option>
            <option value="amount-asc">退款金额：从低到高</option>
          </select></label
        >
      </div>
      <div v-if="listError" role="alert" class="list-failure">
        <XCircle :size="27" /><strong>售后申请加载失败</strong>
        <p>{{ listError }}</p>
        <button class="secondary-button" @click="load">重新加载</button>
      </div>
      <div v-else class="admin-table-wrap" :aria-busy="loading" :class="{ 'is-loading': loading }">
        <table class="admin-table aftersale-table">
          <thead>
            <tr>
              <th scope="col">售后单号 / 关联订单</th>
              <th scope="col">申请原因</th>
              <th scope="col" class="money-cell">整单退款金额</th>
              <th scope="col">处理状态</th>
              <th scope="col">申请时间</th>
              <th scope="col" class="operation-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>
                <button class="record-link" :disabled="busy || detailLoading" @click="open(row.id)">
                  {{ row.aftersaleNo }}</button
                ><small class="order-number">订单 {{ row.orderNo || row.orderId }}</small>
              </td>
              <td class="reason-cell">
                <span :title="row.reason">{{ row.reason }}</span>
              </td>
              <td class="money-cell">
                <strong>{{ money(row.amount) }}</strong>
              </td>
              <td>
                <span class="status-tag" :class="`aftersale-${row.status}`"
                  ><i />{{ aftersaleLabel(row) }}</span
                >
              </td>
              <td class="time-cell">
                <span>{{ dateTime(row.createTime).slice(0, 10) }}</span
                ><small>{{ dateTime(row.createTime).slice(11, 19) }}</small>
              </td>
              <td class="operation-cell">
                <button class="row-action" :disabled="busy || detailLoading" @click="open(row.id)">
                  {{
                    canWrite && row.status === 'pending'
                      ? '去审核'
                      : canWrite && row.status === 'approved'
                        ? '处理退款'
                        : '查看详情'
                  }}<ChevronRight :size="14" />
                </button>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6">
                <div class="list-empty">
                  <LoaderCircle v-if="loading" class="spinning" :size="30" /><Search
                    v-else-if="hasFilters"
                    :size="30"
                  /><ClipboardList v-else :size="30" /><strong>{{
                    loading
                      ? '正在加载售后申请…'
                      : hasFilters
                        ? '没有找到符合条件的申请'
                        : '暂无售后申请'
                  }}</strong>
                  <p>
                    {{
                      loading
                        ? '请稍候'
                        : hasFilters
                          ? '请检查完整单号，或调整筛选条件后重试。'
                          : '新的售后申请会显示在这里。'
                    }}
                  </p>
                  <button
                    v-if="hasFilters && !loading"
                    class="secondary-button"
                    @click="resetFilters"
                  >
                    清空筛选
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!listError" class="pagination">
        <span
          >共 {{ total }} 条<span class="page-range"
            >，当前显示 {{ rangeStart }}–{{ rangeEnd }} 条</span
          ></span
        >
        <div>
          <select
            v-model.number="query.size"
            aria-label="每页数量"
            :disabled="loading || busy"
            @change="changePageSize"
          >
            <option :value="10">10 条 / 页</option>
            <option :value="20">20 条 / 页</option>
            <option :value="50">50 条 / 页</option></select
          ><button
            aria-label="上一页"
            :disabled="loading || busy || query.page <= 1"
            @click="page(-1)"
          >
            <ChevronLeft :size="15" /></button
          ><span>{{ query.page }} / {{ pages }}</span
          ><button
            aria-label="下一页"
            :disabled="loading || busy || query.page >= pages"
            @click="page(1)"
          >
            <ChevronRight :size="15" />
          </button>
        </div>
      </footer>
    </section>
    <dialog
      ref="drawer"
      class="aftersale-drawer"
      aria-labelledby="aftersale-detail-title"
      @cancel.prevent="closeDetail"
      @click="
        (event) => {
          if (event.target === drawer) closeDetail()
        }
      "
    >
      <div class="drawer-layout">
        <header class="drawer-heading">
          <div>
            <span class="page-kicker">AFTERSALES DETAIL</span>
            <h2 id="aftersale-detail-title">售后详情与处理</h2>
          </div>
          <div class="drawer-heading-actions">
            <button
              v-if="selected"
              class="icon-button"
              aria-label="刷新详情"
              :disabled="busy || detailLoading"
              @click="open(selected.id)"
            >
              <RefreshCw :size="17" :class="{ spinning: detailLoading }" /></button
            ><button
              class="icon-button"
              aria-label="关闭售后详情"
              :disabled="busy"
              autofocus
              @click="closeDetail"
            >
              <X :size="21" />
            </button>
          </div>
        </header>
        <div class="drawer-body" :aria-busy="detailLoading || busy">
          <div v-if="detailLoading && !selected" class="detail-placeholder">
            <LoaderCircle class="spinning" :size="30" />
            <p>正在加载售后详情…</p>
          </div>
          <p v-if="detailError" role="alert" class="feedback failure">
            <XCircle :size="17" />{{ detailError }}<span>请关闭后重试，或刷新详情。</span>
          </p>
          <template v-if="selected">
            <p v-if="notice" role="status" class="feedback success">
              <CheckCircle2 :size="17" />{{ notice }}
            </p>
            <section class="detail-summary">
              <div>
                <span class="status-tag" :class="`aftersale-${selected.status}`"
                  ><i />{{ aftersaleLabel(selected) }}</span
                >
                <h3>{{ selected.aftersaleNo }}</h3>
                <p>申请时间 {{ dateTime(selected.createTime) }}</p>
              </div>
              <div class="refund-amount">
                <span>整单退款金额</span><strong>{{ money(selected.amount) }}</strong>
              </div>
            </section>
            <ol class="progress-steps" aria-label="处理进度">
              <li
                v-for="(step, index) in steps"
                :key="index"
                :class="{
                  reached: index <= currentStep,
                  rejected: selected.status === 'rejected' && index > 0,
                }"
              >
                <span
                  ><Check
                    v-if="index < currentStep || selected.status === 'completed'"
                    :size="13"
                  /><X
                    v-else-if="selected.status === 'rejected' && index > 0"
                    :size="13"
                  /><template v-else>{{ index + 1 }}</template></span
                >{{ step }}
              </li>
            </ol>
            <div class="detail-columns">
              <div class="detail-information">
                <AftersaleDetailPanel
                  :row="selected"
                  :operations="operations"
                  admin
                  :history-error="historyError"
                /><button
                  v-if="historyError"
                  class="secondary-button"
                  :disabled="detailLoading || busy"
                  @click="open(selected.id)"
                >
                  重新加载处理记录
                </button>
              </div>
              <aside class="processing-panel" aria-label="处理售后">
                <div class="processing-heading">
                  <ShieldCheck :size="18" />
                  <h3>
                    {{
                      selected.status === 'pending'
                        ? '审核申请'
                        : selected.status === 'approved'
                          ? '处理退款'
                          : '处理结果'
                    }}
                  </h3>
                </div>
                <p
                  v-if="!canWrite && ['pending', 'approved'].includes(selected.status)"
                  class="read-only-hint"
                >
                  <Info :size="17" />你拥有查看权限，请联系有售后处理权限的管理员操作。
                </p>
                <form
                  v-else-if="selected.status === 'pending'"
                  class="processing-form"
                  @submit.prevent="act('review')"
                >
                  <fieldset :disabled="busy || detailLoading || !!detailError">
                    <legend>审核结果</legend>
                    <label class="choice-card" :class="{ chosen: decision === 'approved' }"
                      ><input
                        v-model="decision"
                        type="radio"
                        value="approved"
                        name="review-decision" /><span
                        ><strong>同意退款</strong><small>通过后进入退款处理</small></span
                      ><CheckCircle2 :size="17" /></label
                    ><label
                      class="choice-card reject-choice"
                      :class="{ chosen: decision === 'rejected' }"
                      ><input
                        v-model="decision"
                        type="radio"
                        value="rejected"
                        name="review-decision" /><span
                        ><strong>拒绝申请</strong><small>请向申请人说明具体原因</small></span
                      ><XCircle :size="17"
                    /></label>
                  </fieldset>
                  <label class="field-label"
                    ><span>审核意见 <b>*</b></span
                    ><textarea
                      v-model="reviewRemark"
                      required
                      maxlength="500"
                      rows="4"
                      :disabled="busy || detailLoading || !!detailError"
                      :placeholder="
                        decision === 'approved'
                          ? '请说明审核依据或后续处理安排'
                          : '请填写明确的拒绝原因，便于申请人理解'
                      "
                    /><small class="field-help"
                      >申请人可见<span>{{ reviewRemark.length }}/500</span></small
                    ></label
                  >
                  <p class="form-note">
                    <Info :size="15" />{{
                      decision === 'approved'
                        ? '审核通过后，请继续完成退款操作。'
                        : '拒绝后本次申请将结束，请确认审核依据充分。'
                    }}
                  </p>
                  <p v-if="actionError" role="alert" class="action-error">{{ actionError }}</p>
                  <button
                    class="primary-button submit-action"
                    :class="{ 'reject-button': decision === 'rejected' }"
                    :disabled="busy || detailLoading || !!detailError || !reviewRemark.trim()"
                  >
                    <LoaderCircle v-if="busy" class="spinning" :size="16" /><ShieldCheck
                      v-else
                      :size="16"
                    />{{
                      busy ? '正在提交…' : decision === 'approved' ? '确认通过审核' : '确认拒绝申请'
                    }}
                  </button>
                </form>
                <form
                  v-else-if="selected.status === 'approved'"
                  class="processing-form"
                  @submit.prevent="act(refundMethod === 'wechat' ? 'wechat' : 'complete')"
                >
                  <fieldset :disabled="busy || detailLoading || !!detailError">
                    <legend>退款方式</legend>
                    <label class="choice-card" :class="{ chosen: refundMethod === 'wechat' }"
                      ><input
                        v-model="refundMethod"
                        type="radio"
                        value="wechat"
                        name="refund-method" /><span
                        ><strong>微信原路退款</strong><small>适用于微信支付订单</small></span
                      ><ArrowDownLeft :size="17" /></label
                    ><label class="choice-card" :class="{ chosen: refundMethod === 'manual' }"
                      ><input
                        v-model="refundMethod"
                        type="radio"
                        value="manual"
                        name="refund-method" /><span
                        ><strong>登记其他渠道退款</strong><small>已在支付渠道完成退款</small></span
                      ><ClipboardList :size="17"
                    /></label>
                  </fieldset>
                  <div v-if="refundMethod === 'wechat'" class="refund-explanation">
                    <Wallet :size="21" /><strong>原路退回付款账户</strong>
                    <p>确认后发起微信退款；如渠道仍在处理中，可再次点击查询结果。</p>
                    <span
                      >本次退款 <b>{{ money(selected.amount) }}</b></span
                    >
                  </div>
                  <template v-else
                    ><p class="form-note">
                      <Info
                        :size="15"
                      />此操作只登记已完成的退款。请先核实其他支付渠道退款成功；微信订单请使用原路退款。
                    </p>
                    <label class="field-label"
                      ><span>退款流水号 <b>*</b></span
                      ><input
                        v-model="refundReference"
                        required
                        maxlength="100"
                        placeholder="输入支付渠道的真实退款流水号"
                        :disabled="busy || detailLoading || !!detailError" /></label
                    ><label class="field-label"
                      ><span>登记备注 <small>选填</small></span
                      ><textarea
                        v-model="completionRemark"
                        maxlength="500"
                        rows="3"
                        placeholder="补充退款渠道或处理说明"
                        :disabled="busy || detailLoading || !!detailError"
                      /><small class="field-help">{{ completionRemark.length }}/500</small></label
                    ><label class="refund-confirmation"
                      ><input
                        v-model="confirmed"
                        type="checkbox"
                        required
                        :disabled="busy || detailLoading || !!detailError"
                      /><span
                        >我已核实支付渠道实际退款成功，金额为 {{ money(selected.amount) }}</span
                      ></label
                    ></template
                  >
                  <p v-if="actionError" role="alert" class="action-error">{{ actionError }}</p>
                  <button
                    class="primary-button submit-action"
                    :disabled="
                      busy ||
                      detailLoading ||
                      !!detailError ||
                      (refundMethod === 'manual' && (!confirmed || !refundReference.trim()))
                    "
                  >
                    <LoaderCircle v-if="busy" class="spinning" :size="16" /><Wallet
                      v-else
                      :size="16"
                    />{{
                      busy
                        ? '正在处理…'
                        : refundMethod === 'wechat'
                          ? '确认退款 / 查询结果'
                          : '登记退款完成'
                    }}
                  </button>
                </form>
                <div v-else class="processing-result">
                  <CheckCircle2 v-if="selected.status === 'completed'" :size="35" /><XCircle
                    v-else
                    :size="35"
                  /><strong>{{
                    selected.status === 'completed' ? '售后已完成' : aftersaleLabel(selected)
                  }}</strong>
                  <p>
                    {{
                      selected.status === 'completed'
                        ? '退款结果已记录，可在详情中查看退款流水及处理记录。'
                        : selected.reviewRemark || '此申请已结束，无需继续处理。'
                    }}
                  </p>
                  <small v-if="selected.completedTime"
                    >完成时间 {{ dateTime(selected.completedTime) }}</small
                  >
                </div>
              </aside>
            </div>
          </template>
        </div>
        <footer class="drawer-footer">
          <span><ShieldCheck :size="14" />处理操作将留存记录</span
          ><button class="secondary-button" :disabled="busy" @click="closeDetail">关闭</button>
        </footer>
      </div>
    </dialog>
  </div>
</template>

<style scoped>
.aftersales-page {
  --after-green: #257251;
  --after-muted: #76847c;
}
.aftersales-page .secondary-button,
.aftersales-page .primary-button {
  height: 36px;
  border-radius: 7px;
  font-size: 12px;
  white-space: nowrap;
}
.aftersales-page button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.aftersales-page button:focus-visible,
.aftersales-page input:focus-visible,
.aftersales-page select:focus-visible,
.aftersales-page textarea:focus-visible {
  outline: 3px solid #92bfa8;
  outline-offset: 3px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.summary-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 20px;
  text-align: left;
  background: white;
  border: 1px solid #e4eae5;
  border-radius: 12px;
  color: #40544a;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.summary-card:hover {
  border-color: #9cbea9;
  box-shadow: 0 3px 14px #254f3410;
}
.summary-card.active {
  border-color: #83ad95;
  box-shadow: inset 0 0 0 1px #83ad9530;
}
.summary-text {
  display: grid;
  gap: 10px;
  font-size: 13px;
}
.summary-text strong {
  color: #233c2d;
  font-size: 30px;
  font-weight: 650;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.summary-text strong small {
  margin-left: 7px;
  font-size: 11px;
  font-weight: 400;
}
.summary-text small {
  color: var(--after-muted);
  font-size: 11px;
  line-height: 1.5;
}
.summary-icon {
  display: grid;
  place-items: center;
  height: 40px;
  width: 40px;
  flex-shrink: 0;
  border-radius: 11px;
  color: #607e6b;
  background: #f0f5f1;
}
.pending .summary-icon {
  color: #ba8837;
  background: #fff6e6;
}
.approved .summary-icon {
  color: #4c86ad;
  background: #edf5fc;
}
.completed .summary-icon {
  color: #257251;
  background: #eaf5ee;
}
.summary-arrow {
  position: absolute;
  right: 20px;
  bottom: 22px;
  color: #9ba99f;
}
.summary-retry {
  margin: -5px 0 0;
  font-size: 12px;
  color: var(--after-muted);
}
.summary-retry button {
  border: 0;
  background: none;
  color: var(--after-green);
}
.workflow-hint {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #5a7764;
  padding: 2px 3px;
  font-size: 12px;
}
.workflow-hint > span {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}
.workflow-hint strong {
  font-weight: 500;
}
.workflow-hint > small {
  margin-left: auto;
  color: #8a978e;
  font-size: 11px;
}
.aftersale-list {
  overflow: hidden;
  border-radius: 12px;
}
.status-tabs {
  display: flex;
  gap: 26px;
  padding: 0 22px;
  border-bottom: 1px solid #e8ece8;
  overflow-x: auto;
}
.status-tabs button {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-shrink: 0;
  min-height: 57px;
  padding: 0 0 2px;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  color: #77847c;
  font-size: 13px;
}
.status-tabs button.active {
  border-bottom-color: var(--after-green);
  color: var(--after-green);
  font-weight: 600;
}
.status-tabs button > span {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 5px;
  background: #f0f3f0;
  color: #7e8b82;
  font-variant-numeric: tabular-nums;
}
.status-tabs button.active > span {
  color: #257251;
  background: #eaf4ed;
}
.aftersale-filters {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  padding: 22px;
  background: #fdfefd;
}
.aftersale-filters > label {
  display: grid;
  gap: 9px;
  flex: 1;
  max-width: 300px;
  min-width: 0;
  color: #526259;
  font-size: 12px;
}
.aftersale-filters input,
.field-label input,
.field-label textarea {
  width: 100%;
  min-width: 0;
  border: 1px solid #dfe7e1;
  border-radius: 7px;
  background: white;
  padding: 10px 11px;
  color: #32493a;
  font: inherit;
}
.aftersale-filters input {
  height: 37px;
}
.aftersale-filters input::placeholder,
.field-label input::placeholder,
.field-label textarea::placeholder {
  color: #a1aaa4;
}
.filter-actions {
  display: flex;
  gap: 8px;
}
.list-caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 22px;
  border-top: 1px solid #edf1ed;
}
.list-caption strong {
  font-size: 13px;
  font-weight: 600;
}
.result-count {
  margin-left: 10px;
  color: #8c988f;
  font-size: 11px;
}
.list-caption label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8c988f;
  font-size: 11px;
}
.list-caption select {
  border: 0;
  color: #617168;
  background: transparent;
  font: inherit;
  padding: 6px 0;
}
.aftersales-page .aftersale-table {
  min-width: 830px;
}
.aftersales-page .aftersale-table th {
  height: 42px;
  padding: 0 18px;
  font-size: 11px;
  background: #f6f8f6;
  color: #75867b;
}
.aftersales-page .aftersale-table td {
  height: 85px;
  padding: 14px 18px;
  font-size: 12px;
  border-color: #edf1ed;
}
.aftersale-table tbody tr:hover {
  background: #f9fcf9;
}
.record-link {
  display: block;
  border: 0;
  padding: 0;
  color: #355f47;
  background: transparent;
  font: inherit;
  font-weight: 600;
}
.record-link:hover {
  text-decoration: underline;
}
.order-number {
  display: block;
  margin-top: 8px;
  color: #8b978e;
  font-size: 11px;
}
.reason-cell {
  width: 23%;
  max-width: 250px;
  white-space: normal;
}
.reason-cell > span {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
  line-height: 1.8;
}
.aftersale-table .money-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.money-cell strong {
  font-weight: 600;
  color: #2f4537;
}
.aftersales-page .status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 25px;
  border-radius: 5px;
  padding: 3px 8px;
  white-space: nowrap;
  font-size: 11px;
}
.status-tag i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.aftersales-page .aftersale-pending {
  color: #ac7d2e;
  background: #fff6e5;
}
.aftersales-page .aftersale-approved {
  color: #457fa5;
  background: #edf5fc;
}
.aftersales-page .aftersale-completed {
  color: #257251;
  background: #eaf5ee;
}
.aftersales-page .aftersale-rejected {
  color: #808b84;
  background: #f0f2f0;
}
.time-cell {
  font-variant-numeric: tabular-nums;
  color: #63746a !important;
}
.time-cell small {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: #929d95;
}
.aftersale-table .operation-cell {
  text-align: right;
}
.row-action {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 3px;
  min-height: 34px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #257251;
  font: inherit;
}
.is-loading {
  opacity: 0.55;
  pointer-events: none;
}
.list-empty,
.list-failure,
.detail-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 250px;
  padding: 32px 20px;
  text-align: center;
  color: #9ba99f;
}
.list-empty strong,
.list-failure strong {
  color: #5c7163;
  font-size: 14px;
  font-weight: 500;
}
.list-empty p,
.list-failure p {
  color: #8a988f;
  font-size: 12px;
  margin: 0;
  white-space: normal;
  overflow-wrap: anywhere;
}
.aftersales-page .pagination {
  min-height: 64px;
  padding: 12px 22px;
  font-size: 11px;
  gap: 12px;
}
.aftersales-page .pagination > div {
  gap: 10px;
}
.aftersales-page .pagination button {
  display: grid;
  place-items: center;
  min-width: 30px;
  height: 30px;
  padding: 0;
}
.feedback {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 12px;
  line-height: 1.7;
}
.feedback > svg {
  flex-shrink: 0;
}
.feedback.success {
  color: #27704c;
  background: #ecf7ef;
  border: 1px solid #dbeee0;
}
.feedback.failure {
  color: #aa4f47;
  background: #fff2f0;
  flex-wrap: wrap;
}
.feedback > button {
  display: grid;
  place-items: center;
  margin-left: auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
}
.aftersale-drawer {
  position: fixed;
  inset: 0 0 0 auto;
  margin: 0;
  padding: 0;
  width: min(1020px, 94vw);
  max-width: 100vw;
  height: 100%;
  height: 100dvh;
  max-height: 100dvh;
  border: 0;
  border-left: 1px solid #e3eae4;
  color: #263d2f;
  background: #f8faf8;
  box-shadow: -15px 0 70px #15332318;
}
.aftersale-drawer::backdrop {
  background: #14281d66;
  backdrop-filter: blur(2px);
}
.drawer-layout {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.drawer-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  flex-shrink: 0;
  padding: 23px 28px;
  background: #fff;
  border-bottom: 1px solid #e6ece7;
}
.drawer-heading h2 {
  margin: 6px 0 0;
  font-size: 19px;
  font-weight: 600;
}
.drawer-heading-actions {
  display: flex;
  gap: 10px;
}
.icon-button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid #e3eae4;
  background: #fff;
  color: #6b7f71;
  border-radius: 7px;
}
.icon-button:hover {
  background: #f1f6f2;
}
.drawer-body {
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 24px 28px;
}
.drawer-body > .feedback {
  margin-bottom: 18px;
}
.detail-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.detail-summary h3 {
  font-size: 17px;
  font-weight: 600;
  margin: 12px 0 7px;
  overflow-wrap: anywhere;
}
.detail-summary p {
  margin: 0;
  color: #8a978e;
  font-size: 11px;
}
.refund-amount {
  display: grid;
  gap: 9px;
  text-align: right;
  flex-shrink: 0;
}
.refund-amount > span {
  color: #86958a;
  font-size: 11px;
}
.refund-amount strong {
  color: #234931;
  font-size: 25px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.progress-steps {
  display: flex;
  list-style: none;
  margin: 22px 0;
  padding: 18px 20px;
  border: 1px solid #e2eae3;
  border-radius: 9px;
  background: white;
}
.progress-steps li {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  font-size: 12px;
  color: #9aa59c;
}
.progress-steps li:not(:last-child)::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e5ece6;
  margin: 0 16px;
}
.progress-steps li:last-child {
  flex: none;
}
.progress-steps li > span {
  width: 23px;
  height: 23px;
  display: grid;
  place-items: center;
  background: #f0f3f0;
  color: #9aa59c;
  border-radius: 50%;
  flex-shrink: 0;
  font-size: 11px;
}
.progress-steps .reached {
  color: #34734d;
}
.progress-steps .reached > span {
  color: white;
  background: #4a8862;
}
.progress-steps .rejected {
  color: #7c8980;
}
.progress-steps .rejected > span {
  background: #a0aaa3;
}
.detail-columns {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(285px, 0.88fr);
  gap: 20px;
  align-items: start;
}
.detail-information {
  min-width: 0;
}
.processing-panel {
  padding: 20px;
  border-radius: 10px;
  border: 1px solid #dfe9e1;
  background: white;
}
.processing-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #3a6448;
  padding-bottom: 17px;
  border-bottom: 1px solid #edf1ed;
  margin-bottom: 19px;
}
.processing-heading h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}
.processing-form {
  display: grid;
  gap: 19px;
}
.processing-form fieldset {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 0;
  border: 0;
  min-width: 0;
}
.processing-form legend {
  margin-bottom: 10px;
  padding: 0;
  font-size: 12px;
  color: #5d7164;
}
.choice-card {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px;
  border: 1px solid #e2e9e3;
  border-radius: 8px;
  cursor: pointer;
}
.choice-card.chosen {
  border-color: #80af91;
  background: #f3f9f4;
}
.choice-card input,
.refund-confirmation input {
  accent-color: #2e7a50;
  width: 14px;
  height: 14px;
  margin: 0;
  flex-shrink: 0;
}
.choice-card > span {
  display: grid;
  gap: 5px;
  min-width: 0;
}
.choice-card strong {
  font-size: 12px;
  font-weight: 500;
}
.choice-card small {
  color: #8b998f;
  font-size: 11px;
  line-height: 1.5;
}
.choice-card > svg {
  margin-left: auto;
  flex-shrink: 0;
  color: #759a80;
}
.reject-choice.chosen {
  background: #fff7f5;
  border-color: #d6a49a;
}
.reject-choice.chosen > svg {
  color: #b16b5e;
}
.field-label {
  display: grid;
  gap: 9px;
  font-size: 12px;
  color: #536b5b;
}
.field-label b {
  color: #be7163;
  font-weight: 400;
}
.field-label span > small {
  margin-left: 3px;
  color: #99a49c;
  font-size: 11px;
}
.field-label textarea {
  resize: vertical;
  min-height: 95px;
  line-height: 1.8;
}
.field-help {
  display: flex;
  justify-content: space-between;
  color: #9aa59d;
  font-size: 10px;
}
.field-help > span {
  margin-left: auto;
}
.form-note,
.read-only-hint {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: #849488;
  font-size: 11px;
  line-height: 1.8;
  margin: 0;
}
.form-note svg,
.read-only-hint svg {
  flex-shrink: 0;
  margin-top: 3px;
}
.aftersales-page .submit-action {
  width: 100%;
  justify-content: center;
  min-height: 40px;
  border-radius: 7px;
  font-size: 12px;
}
.aftersales-page .reject-button {
  background: #aa6054;
  border-color: #aa6054;
}
.action-error {
  background: #fff2ef;
  color: #ad594d;
  padding: 10px 12px;
  margin: 0;
  border-radius: 7px;
  line-height: 1.8;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.refund-explanation {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
  background: #f5f8f5;
  color: #587e62;
}
.refund-explanation strong {
  font-size: 13px;
  font-weight: 500;
}
.refund-explanation p {
  margin: 0;
  color: #819286;
  font-size: 11px;
  line-height: 1.9;
}
.refund-explanation > span {
  padding-top: 11px;
  border-top: 1px solid #e5ece5;
  color: #7c8c80;
  font-size: 11px;
}
.refund-explanation b {
  float: right;
  color: #335e41;
  font-size: 14px;
  font-weight: 600;
}
.refund-confirmation {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  line-height: 1.9;
  font-size: 11px;
  color: #6a7f70;
}
.refund-confirmation input {
  margin-top: 4px;
}
.processing-result {
  display: grid;
  justify-items: center;
  gap: 15px;
  text-align: center;
  padding: 17px 0;
  color: #658a70;
}
.processing-result strong {
  font-size: 15px;
  font-weight: 500;
}
.processing-result p {
  margin: 0;
  line-height: 1.9;
  color: #85958a;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 12px;
}
.processing-result small {
  color: #9aa79d;
  font-size: 10px;
}
.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
  border-top: 1px solid #e3eae4;
  background: white;
  padding: 14px 28px max(14px, env(safe-area-inset-bottom));
}
.drawer-footer > span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #98a39b;
  font-size: 11px;
}
.spinning {
  animation: aftersale-spin 1s linear infinite;
}
@keyframes aftersale-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 1150px) {
  .summary-card {
    padding: 16px;
  }
  .summary-icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
  }
  .summary-arrow {
    display: none;
  }
  .summary-text > small {
    max-width: 120px;
  }
}
@media (max-width: 760px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }
  .summary-text strong {
    font-size: 26px;
  }
  .summary-text > small {
    display: none;
  }
  .aftersales-page .page-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .workflow-hint {
    align-items: flex-start;
  }
  .workflow-hint > svg {
    flex-shrink: 0;
  }
  .workflow-hint > span {
    gap: 5px;
    font-size: 11px;
  }
  .workflow-hint > small {
    display: none;
  }
  .status-tabs {
    gap: 20px;
    padding-inline: 16px;
  }
  .aftersale-filters {
    flex-wrap: wrap;
    padding: 16px;
    gap: 12px;
  }
  .aftersale-filters > label {
    flex: 1 1 180px;
    max-width: none;
  }
  .filter-actions {
    flex: 1 1 100%;
  }
  .list-caption {
    padding-inline: 16px;
  }
  .aftersales-page .aftersale-table {
    min-width: 0;
    white-space: normal;
  }
  .aftersale-table thead {
    display: none;
  }
  .aftersale-table tbody {
    display: grid;
    gap: 10px;
    padding: 0 14px 14px;
  }
  .aftersale-table tbody tr {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 13px 8px;
    padding: 15px;
    border: 1px solid #e5ece6;
    border-radius: 9px;
  }
  .aftersales-page .aftersale-table td {
    display: block;
    min-width: 0;
    height: auto;
    padding: 0;
    border: 0;
  }
  .aftersale-table td:first-child {
    grid-column: 1 / -1;
    grid-row: 1;
  }
  .aftersale-table td:nth-child(2) {
    grid-column: 1 / -1;
    grid-row: 3;
    width: auto;
    max-width: none;
    color: #819087;
  }
  .aftersale-table td:nth-child(3) {
    grid-column: 2;
    grid-row: 2;
    align-self: center;
  }
  .aftersale-table td:nth-child(4) {
    grid-column: 1;
    grid-row: 2;
  }
  .aftersale-table td:nth-child(5) {
    grid-column: 1;
    grid-row: 4;
    align-self: center;
    font-size: 11px;
  }
  .time-cell small {
    display: inline;
    margin-left: 6px;
  }
  .aftersale-table td:nth-child(6) {
    grid-column: 2;
    grid-row: 4;
  }
  .record-link {
    text-align: left;
    overflow-wrap: anywhere;
  }
  .order-number {
    overflow-wrap: anywhere;
  }
  .list-empty {
    min-height: 200px;
    padding-inline: 0;
  }
  .result-count {
    display: none;
  }
  .aftersales-page .pagination {
    padding: 14px 16px;
    flex-wrap: wrap;
  }
  .page-range {
    display: none;
  }
  .aftersale-drawer {
    width: 100vw;
    border: 0;
  }
  .drawer-heading {
    padding: 18px;
  }
  .drawer-body {
    padding: 20px 16px;
  }
  .detail-columns {
    grid-template-columns: 1fr;
  }
  .detail-summary {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 18px;
  }
  .refund-amount {
    text-align: left;
    gap: 5px;
  }
  .refund-amount strong {
    font-size: 23px;
  }
  .progress-steps {
    padding: 15px 12px;
  }
  .progress-steps li {
    gap: 6px;
    font-size: 11px;
  }
  .progress-steps li:not(:last-child)::after {
    margin: 0 8px;
  }
  .drawer-footer {
    padding-inline: 18px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .spinning {
    animation: none;
  }
  .summary-card {
    transition: none;
  }
}
</style>
