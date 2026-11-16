<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Clock3,
  Download,
  Info,
  LoaderCircle,
  Package,
  RefreshCw,
  Search,
  ShoppingBag,
  TrendingUp,
  UsersRound,
  Wallet,
  Wrench,
  XCircle,
} from 'lucide-vue-next'
import {
  statisticsApi,
  localizedDailyCsv,
  type BusinessOverview,
  type BusinessDaily,
  type ProductSales,
  type BusinessMetrics,
  type StatisticsRange,
} from '../api/statistics'
import { ApiError } from '../api/http'
import { orderStatusLabels, type OrderStatus } from '../api/orders'
import BusinessTrendChart from '../components/BusinessTrendChart.vue'
import { can, canVisit } from './permissions'
import { presetRange, validateRange, type DatePreset } from './overview'
import './overview.css'

const presets = [
  { key: 'today', label: '今日' },
  { key: 'yesterday', label: '昨日' },
  { key: '7', label: '近 7 天' },
  { key: '30', label: '近 30 天' },
] as const
const preset = ref<DatePreset>('7')
const range = reactive<StatisticsRange>(presetRange('7'))
const dates = reactive<StatisticsRange>({ ...range })
const datesDirty = computed(
  () => dates.startDate !== range.startDate || dates.endDate !== range.endDate,
)
const overview = ref<BusinessOverview | null>(null),
  trend = ref<BusinessDaily[]>([]),
  products = ref<ProductSales[]>([])
const loading = ref(false),
  overviewError = ref(''),
  trendError = ref(''),
  productError = ref(''),
  dateError = ref('')
const updatedAt = ref(''),
  exporting = ref(false),
  exportError = ref(''),
  notice = ref('')
const dailyRows = ref<BusinessDaily[]>([]),
  dailyTotal = ref(0),
  dailyPages = ref(1),
  dailyPage = ref(1)
const dailyLoading = ref(false),
  dailyError = ref('')
const metric = ref<keyof BusinessMetrics>('receivedAmount')
const metrics = [
  { key: 'receivedAmount', label: '实收金额', currency: true },
  { key: 'refundAmount', label: '退款金额', currency: true },
  { key: 'netReceivedAmount', label: '净收款', currency: true },
  { key: 'createdOrders', label: '新建订单', currency: false },
  { key: 'paidOrders', label: '支付订单', currency: false },
] as const
const activeMetric = computed(() => metrics.find((item) => item.key === metric.value)!)
const trendTotal = computed(() =>
  trend.value.reduce((total, day) => total + Number(day.metrics[metric.value]), 0),
)
const money = (value: number) =>
  `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const count = (value: number) => Number(value).toLocaleString('zh-CN')
const dateTime = (value: string) => value.replace('T', ' ').slice(0, 19)
const rangeLabel = computed(() =>
  range.startDate === range.endDate ? range.startDate : `${range.startDate} 至 ${range.endDate}`,
)
const canExport = computed(() => can('statistics:export'))
const stats = computed(() => {
  const data = overview.value
  if (!data) return []
  const customerGrowth = data.previous.newCustomers
    ? ((data.current.newCustomers - data.previous.newCustomers) / data.previous.newCustomers) * 100
    : null
  return [
    {
      label: '实收金额',
      value: money(data.current.receivedAmount),
      growth: data.receivedGrowthPercent,
      icon: Wallet,
      tone: 'green',
      note: `支付订单 ${count(data.current.paidOrders)} 单`,
    },
    {
      label: '新建订单',
      value: count(data.current.createdOrders),
      growth: data.orderGrowthPercent,
      icon: ShoppingBag,
      tone: 'blue',
      note: '包含待付款、已关闭与已退款订单',
    },
    {
      label: '退款金额',
      value: money(data.current.refundAmount),
      growth: null,
      icon: ArrowDownRight,
      tone: 'amber',
      note: `完成退款 ${count(data.current.refundedOrders)} 单`,
      refund: true,
    },
    {
      label: '新增客户',
      value: count(data.current.newCustomers),
      growth: customerGrowth,
      icon: UsersRound,
      tone: 'violet',
      note: '按客户注册时间统计',
    },
  ]
})
const growthText = (value: number | null) =>
  value == null ? '上期基数为 0' : `${value > 0 ? '+' : ''}${Number(value.toFixed(2))}%`
const statusColors: Record<OrderStatus, string> = {
  pending_payment: '#e9bd69',
  paid: '#7ea8c5',
  processing: '#8aa9a0',
  completed: '#4b8b65',
  cancelled: '#c4cbc6',
  refunded: '#ae9bc0',
}
const statusRows = computed(() =>
  Object.entries(overview.value?.orderStatuses || {}).map(([key, value]) => ({
    key,
    label: orderStatusLabels[key as OrderStatus] || key,
    value,
    color: statusColors[key as OrderStatus] || '#bbc6bf',
  })),
)
const statusTotal = computed(() => statusRows.value.reduce((sum, row) => sum + row.value, 0))
const circumference = 2 * Math.PI * 66
const statusSegments = computed(() => {
  let offset = 0
  return statusRows.value
    .filter((row) => row.value > 0)
    .map((row) => {
      const length = (row.value / statusTotal.value) * circumference
      const segment = { ...row, length, offset }
      offset += length
      return segment
    })
})
const completedRatio = computed(() =>
  statusTotal.value
    ? `${(((overview.value?.orderStatuses.completed || 0) / statusTotal.value) * 100).toFixed(1)}%`
    : '—',
)
const tasks = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    {
      label: '待处理订单',
      value: data.todos?.paidOrders ?? null,
      hint: '已付款，等待处理',
      icon: ShoppingBag,
      path: '/admin/orders',
      query: { status: 'paid' },
      allowed: canVisit('/admin/orders'),
      tone: 'blue',
    },
    {
      label: '待审核售后',
      value: data.todos?.pendingAftersales ?? null,
      hint: '及时响应退款申请',
      icon: ClipboardList,
      path: '/admin/aftersales',
      query: { status: 'pending' },
      allowed: canVisit('/admin/aftersales'),
      tone: 'amber',
    },
    {
      label: '待退款售后',
      value: data.todos?.pendingRefunds ?? null,
      hint: '审核已通过，待退款',
      icon: Wallet,
      path: '/admin/aftersales',
      query: { status: 'approved' },
      allowed: canVisit('/admin/aftersales'),
      tone: 'green',
    },
    {
      label: '未完成工单',
      value: data.pendingWorkOrders,
      hint: `${count(data.overdueWorkOrders)} 单已逾预约时间`,
      icon: Wrench,
      path: '/admin/work-orders',
      query: {},
      allowed: can('workorder:read'),
      tone: 'violet',
    },
  ]
})
let version = 0,
  dailyVersion = 0,
  disposed = false
function explain(error: unknown) {
  return error instanceof ApiError && error.traceId
    ? `${error.message}（请求编号：${error.traceId}）`
    : error instanceof Error
      ? error.message
      : '数据加载失败，请重试。'
}
async function load() {
  const current = ++version,
    requested = { ...range }
  loading.value = true
  overviewError.value = trendError.value = productError.value = ''
  overview.value = null
  trend.value = []
  products.value = []
  updatedAt.value = ''
  const calls = [
    statisticsApi
      .overview(requested)
      .then((data) => {
        if (current === version) {
          overview.value = data
          updatedAt.value = new Date().toLocaleTimeString('zh-CN', {
            timeZone: 'Asia/Shanghai',
            hour12: false,
          })
        }
      })
      .catch((error) => {
        if (current === version) overviewError.value = explain(error)
      }),
    statisticsApi
      .trend(requested)
      .then((data) => {
        if (current === version) trend.value = data
      })
      .catch((error) => {
        if (current === version) trendError.value = explain(error)
      }),
    statisticsApi
      .products(requested)
      .then((data) => {
        if (current === version) products.value = data
      })
      .catch((error) => {
        if (current === version) productError.value = explain(error)
      }),
    loadDaily(),
  ]
  await Promise.allSettled(calls)
  if (current === version) loading.value = false
}
async function loadDaily() {
  const current = ++dailyVersion
  dailyLoading.value = true
  dailyError.value = ''
  dailyRows.value = []
  try {
    const data = await statisticsApi.daily({ ...range }, dailyPage.value, 7)
    if (current !== dailyVersion) return
    dailyRows.value = data.list
    dailyTotal.value = data.total
    dailyPages.value = Math.max(1, data.pages)
  } catch (error) {
    if (current === dailyVersion) {
      dailyError.value = explain(error)
      dailyTotal.value = 0
      dailyPages.value = 1
    }
  } finally {
    if (current === dailyVersion) dailyLoading.value = false
  }
}
function applyRange() {
  dateError.value = validateRange(dates)
  if (dateError.value) return
  Object.assign(range, dates)
  preset.value =
    presets.find((item) => {
      const candidate = presetRange(item.key)
      return candidate.startDate === range.startDate && candidate.endDate === range.endDate
    })?.key || 'custom'
  dailyPage.value = 1
  notice.value = exportError.value = ''
  void load()
}
function choosePreset(value: Exclude<DatePreset, 'custom'>) {
  preset.value = value
  Object.assign(dates, presetRange(value))
  applyRange()
}
function customDates() {
  preset.value = 'custom'
  dateError.value = ''
}
function resetDates() {
  Object.assign(dates, range)
  preset.value =
    presets.find((item) => {
      const candidate = presetRange(item.key)
      return candidate.startDate === range.startDate && candidate.endDate === range.endDate
    })?.key || 'custom'
  dateError.value = ''
}
function changePage(delta: number) {
  if (dailyLoading.value || loading.value) return
  dailyPage.value = Math.max(1, Math.min(dailyPages.value, dailyPage.value + delta))
  void loadDaily()
}
async function exportDaily() {
  if (exporting.value || !canExport.value) return
  exporting.value = true
  exportError.value = ''
  notice.value = ''
  const requested = { ...range }
  try {
    const csv = localizedDailyCsv(await statisticsApi.exportDaily(requested))
    if (disposed) return
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `经营日报-${requested.startDate}至${requested.endDate}.csv`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    notice.value = `已导出 ${requested.startDate} 至 ${requested.endDate} 的全部经营日报。`
  } catch (error) {
    if (!disposed) exportError.value = explain(error)
  } finally {
    if (!disposed) exporting.value = false
  }
}
function orderTarget(orderNo?: string, status?: string) {
  return {
    path: '/admin/orders',
    query: {
      startTime: `${range.startDate}T00:00:00`,
      endTime: `${range.endDate}T23:59:59`,
      ...(orderNo ? { orderNo } : {}),
      ...(status ? { status } : {}),
    },
  }
}
onMounted(load)
onUnmounted(() => {
  version++
  dailyVersion++
  disposed = true
})
</script>

<template>
  <div class="admin-page business-overview">
    <header class="page-heading">
      <div>
        <h1>经营总览</h1>
        <p>掌握经营表现，及时跟进待办事项</p>
      </div>
      <div class="heading-actions">
        <button
          v-if="canExport"
          class="secondary-button"
          :disabled="exporting || loading"
          @click="exportDaily"
        >
          <LoaderCircle v-if="exporting" :size="15" class="spin" /><Download v-else :size="15" />{{
            exporting ? '正在导出' : '导出日报'
          }}</button
        ><button class="secondary-button" :disabled="loading" @click="load">
          <RefreshCw :size="15" :class="{ spin: loading }" />{{ loading ? '刷新中…' : '刷新数据' }}
        </button>
      </div>
    </header>
    <section class="range-panel panel" aria-label="统计日期筛选">
      <div class="range-presets">
        <span class="range-label"><CalendarDays :size="17" />统计时间</span>
        <div class="preset-buttons">
          <button
            v-for="item in presets"
            :key="item.key"
            :class="{ active: preset === item.key }"
            :aria-pressed="preset === item.key"
            @click="choosePreset(item.key)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
      <form class="custom-range" @submit.prevent="applyRange">
        <label
          ><span class="date-label">开始日期</span
          ><input
            v-model="dates.startDate"
            type="date"
            required
            min="1970-01-01"
            max="9998-12-31"
            :aria-invalid="!!dateError"
            :aria-describedby="dateError || datesDirty ? 'range-feedback' : undefined"
            @input="customDates" /></label
        ><span>至</span
        ><label
          ><span class="date-label">结束日期</span
          ><input
            v-model="dates.endDate"
            type="date"
            required
            min="1970-01-01"
            max="9998-12-31"
            :aria-invalid="!!dateError"
            :aria-describedby="dateError || datesDirty ? 'range-feedback' : undefined"
            @input="customDates" /></label
        ><button class="range-submit" type="submit"><Search :size="16" />查询</button>
      </form>
      <div class="filter-context">
        <span
          >当前统计：<strong>{{ rangeLabel }}</strong
          ><small>上海时间 · 按日统计</small></span
        >
        <span v-if="updatedAt" class="updated-time"><i />{{ updatedAt }} 更新</span>
        <span v-else-if="loading" class="updated-time"
          ><LoaderCircle :size="13" class="spin" />正在更新数据…</span
        >
      </div>
      <p
        v-if="dateError || datesDirty"
        id="range-feedback"
        class="range-feedback"
        :class="{ failure: dateError }"
        :role="dateError ? 'alert' : 'status'"
      >
        <template v-if="dateError"> <XCircle :size="16" />{{ dateError }} </template>
        <template v-else-if="datesDirty"
          ><Info :size="15" />日期已修改，点击“查询”后更新数据。<button
            type="button"
            @click="resetDates"
          >
            撤销修改
          </button></template
        >
      </p>
    </section>
    <p v-if="notice" role="status" class="overview-feedback success">
      <CheckCircle2 :size="16" />{{ notice }}
    </p>
    <p v-if="exportError" role="alert" class="overview-feedback failure">
      <XCircle :size="16" />{{ exportError }}
    </p>
    <section v-if="overviewError" role="alert" class="panel section-error">
      <XCircle :size="27" /><strong>经营指标暂时无法加载</strong>
      <p>{{ overviewError }}</p>
      <button class="secondary-button" @click="load">重新加载</button>
    </section>
    <section v-else class="stats-grid" aria-label="经营指标" :aria-busy="!overview && loading">
      <template v-if="overview"
        ><article
          v-for="stat in stats"
          :key="stat.label"
          class="stat-panel"
          :class="`metric-${stat.tone}`"
        >
          <div class="metric-heading">
            <span>{{ stat.label }}</span
            ><span class="metric-icon" :class="stat.tone"
              ><component :is="stat.icon" :size="18"
            /></span>
          </div>
          <strong class="metric-value">{{ stat.value }}</strong>
          <div class="metric-change" v-if="!stat.refund">
            <span
              :class="stat.growth == null ? 'neutral' : stat.growth < 0 ? 'negative' : 'positive'"
              ><ArrowUpRight
                v-if="stat.growth != null && stat.growth > 0"
                :size="13"
              /><ArrowDownRight v-else-if="stat.growth != null && stat.growth < 0" :size="13" />{{
                growthText(stat.growth)
              }}</span
            ><small v-if="stat.growth != null">较上期</small>
          </div>
          <div v-else class="metric-change"><span class="neutral">以退款完成时间为准</span></div>
          <p>{{ stat.note }}</p>
        </article></template
      >
      <article v-for="index in overview ? 0 : 4" :key="index" class="stat-panel skeleton-card">
        <span class="skeleton short" /><span class="skeleton large" /><span class="skeleton" />
      </article>
    </section>
    <div v-if="overview" class="settlement-strip">
      <span
        ><TrendingUp :size="16" />本期净收款
        <strong :class="{ negative: overview.current.netReceivedAmount < 0 }">{{
          money(overview.current.netReceivedAmount)
        }}</strong></span
      ><span>实收 − 退款<small>跨期退款可能导致净收款为负</small></span
      ><span class="comparison-range"
        >上期 {{ overview.previousStartDate }} 至 {{ overview.previousEndDate
        }}<small>比较完整日期区间，非昨日同一时刻</small></span
      >
    </div>
    <section v-if="overview" class="panel todo-panel" aria-label="当前业务待办">
      <div class="section-heading">
        <div>
          <h2>业务待办</h2>
          <p>当前全部待办 · 不受统计时间影响</p>
        </div>
        <span v-if="overview.overdueWorkOrders" class="overdue-badge"
          ><Clock3 :size="13" />{{ overview.overdueWorkOrders }} 单工单已逾期</span
        >
      </div>
      <div class="todo-grid">
        <component
          :is="task.allowed ? RouterLink : 'div'"
          v-for="task in tasks"
          :key="task.label"
          :to="task.allowed ? { path: task.path, query: task.query } : undefined"
          class="todo-item"
          ><span class="todo-icon" :class="task.tone"
            ><component :is="task.icon" :size="19"
          /></span>
          <div>
            <span>{{ task.label }}</span
            ><strong>{{ task.value == null ? '—' : count(task.value) }}<small>单</small></strong>
            <p>{{ task.hint }}</p>
          </div>
          <span v-if="task.allowed" class="todo-action">去处理<ArrowRight :size="14" /></span>
        </component>
      </div>
    </section>
    <div class="business-charts-grid">
      <section class="panel trend-panel" aria-label="经营趋势">
        <div class="section-heading">
          <div>
            <h2>经营趋势</h2>
            <p>{{ rangeLabel }} · 每日变化</p>
          </div>
          <div v-if="trend.length && !trendError" class="trend-total">
            <span>区间合计</span
            ><strong>{{
              activeMetric.currency ? money(trendTotal) : `${count(trendTotal)} 单`
            }}</strong>
          </div>
        </div>
        <div class="trend-metrics" role="group" aria-label="趋势指标">
          <button
            v-for="item in metrics"
            :key="item.key"
            type="button"
            :aria-pressed="metric === item.key"
            :class="{ active: metric === item.key }"
            @click="metric = item.key"
          >
            {{ item.label }}
          </button>
        </div>
        <div v-if="trendError" role="alert" class="section-error">
          <XCircle :size="25" />
          <p>{{ trendError }}</p>
          <button class="secondary-button" :disabled="loading" @click="load">重新加载</button>
        </div>
        <div v-else-if="loading && !trend.length" class="chart-placeholder">
          <LoaderCircle class="spin" :size="25" /><span>正在加载趋势…</span>
        </div>
        <BusinessTrendChart
          v-else-if="trend.length"
          :days="trend"
          :metric="metric"
          :label="activeMetric.label"
          :currency="activeMetric.currency"
        />
        <div v-else class="chart-placeholder">
          <TrendingUp :size="27" /><span>所选日期暂无趋势数据</span>
        </div>
      </section>
      <section class="panel status-panel" aria-label="订单状态分布">
        <div class="section-heading">
          <div>
            <h2>订单概况</h2>
            <p>本期创建订单的当前状态</p>
          </div>
          <RouterLink v-if="canVisit('/admin/orders')" class="overview-link" :to="orderTarget()"
            >查看订单<ArrowRight :size="13"
          /></RouterLink>
        </div>
        <div v-if="overview" class="order-distribution">
          <div class="status-donut">
            <svg
              viewBox="0 0 170 170"
              role="img"
              :aria-label="`本期共${statusTotal}单订单，各状态数量见下方列表`"
            >
              <circle cx="85" cy="85" r="66" fill="none" stroke="#edf2ed" stroke-width="17" />
              <circle
                v-for="segment in statusSegments"
                :key="segment.key"
                cx="85"
                cy="85"
                r="66"
                fill="none"
                :stroke="segment.color"
                stroke-width="17"
                :stroke-dasharray="`${segment.length} ${circumference - segment.length}`"
                :stroke-dashoffset="-segment.offset"
                transform="rotate(-90 85 85)"
              >
                <title>{{ segment.label }} {{ segment.value }} 单</title>
              </circle>
            </svg>
            <div>
              <strong>{{ count(statusTotal) }}</strong
              ><span>新建订单</span>
            </div>
          </div>
          <div class="status-legend">
            <component
              :is="canVisit('/admin/orders') ? RouterLink : 'div'"
              v-for="row in statusRows"
              :key="row.key"
              :to="orderTarget(undefined, row.key)"
              class="status-legend-item"
              ><i :style="{ background: row.color }" /><span>{{ row.label }}</span
              ><strong>{{ count(row.value) }}</strong></component
            >
          </div>
        </div>
        <div v-else class="chart-placeholder">
          <Info :size="25" /><span>{{ loading ? '正在加载订单概况…' : '订单概况暂不可用' }}</span>
        </div>
        <div v-if="overview" class="status-foot">
          <span>订单完成占比</span><strong>{{ completedRatio }}</strong>
        </div>
      </section>
    </div>
    <div class="business-bottom-grid">
      <section class="panel ranking-panel" aria-label="商品销量排行">
        <div class="section-heading">
          <div>
            <h2>热销商品</h2>
            <p>按支付时间统计 · 销量前 5 名</p>
          </div>
          <span class="section-icon"><Package :size="18" /></span>
        </div>
        <div v-if="productError" role="alert" class="section-error">
          <XCircle :size="25" />
          <p>{{ productError }}</p>
          <button class="secondary-button" :disabled="loading" @click="load">重新加载</button>
        </div>
        <ol v-else-if="products.length" class="product-ranking">
          <li v-for="(product, index) in products" :key="product.productId">
            <span class="rank-number" :class="{ top: index < 3 }">{{
              String(index + 1).padStart(2, '0')
            }}</span>
            <div class="rank-product">
              <strong :title="product.productName">{{ product.productName }}</strong
              ><small
                >{{ product.paidOrders }} 单成交 · 商品金额
                {{ money(product.merchandiseAmount) }}</small
              >
            </div>
            <strong class="rank-quantity">{{ count(product.quantity) }}<small>件</small></strong>
          </li>
        </ol>
        <div v-else class="chart-placeholder">
          <LoaderCircle v-if="loading" class="spin" :size="25" /><Package
            v-else
            :size="27"
          /><span>{{ loading ? '正在加载商品排行…' : '所选日期暂无已支付商品' }}</span>
        </div>
        <p class="ranking-note">销量未扣除后续退款；商品金额为优惠前金额，不等于实收。</p>
      </section>
      <section class="panel recent-panel" aria-label="最近订单">
        <div class="section-heading">
          <div>
            <h2>最近订单</h2>
            <p>所选日期最新 10 条 · 金额为订单应付</p>
          </div>
          <RouterLink v-if="canVisit('/admin/orders')" class="overview-link" :to="orderTarget()"
            >全部订单<ArrowRight :size="13"
          /></RouterLink>
        </div>
        <div v-if="overview?.recentOrders.length" class="recent-orders">
          <article v-for="order in overview.recentOrders.slice(0, 5)" :key="order.id">
            <span class="order-symbol"><ShoppingBag :size="16" /></span>
            <div class="recent-order-main">
              <RouterLink v-if="canVisit('/admin/orders')" :to="orderTarget(order.orderNo)">{{
                order.orderNo
              }}</RouterLink
              ><strong v-else>{{ order.orderNo }}</strong
              ><time>{{ dateTime(order.createTime) }}</time>
            </div>
            <div class="recent-order-value">
              <strong>{{ money(order.payAmount) }}</strong
              ><span class="overview-status" :class="order.status">{{
                orderStatusLabels[order.status] || order.status
              }}</span>
            </div>
          </article>
        </div>
        <div v-else class="chart-placeholder">
          <ShoppingBag :size="27" /><span>{{
            loading ? '正在加载最近订单…' : overviewError ? '最近订单暂不可用' : '所选日期暂无订单'
          }}</span>
        </div>
        <details v-if="overview && overview.recentOrders.length > 5" class="more-orders">
          <summary>展开其余 {{ overview.recentOrders.length - 5 }} 条订单</summary>
          <div class="recent-orders">
            <article v-for="order in overview.recentOrders.slice(5)" :key="order.id">
              <div class="recent-order-main">
                <RouterLink v-if="canVisit('/admin/orders')" :to="orderTarget(order.orderNo)">{{
                  order.orderNo
                }}</RouterLink
                ><strong v-else>{{ order.orderNo }}</strong
                ><time>{{ dateTime(order.createTime) }}</time>
              </div>
              <div class="recent-order-value">
                <strong>{{ money(order.payAmount) }}</strong
                ><span class="overview-status" :class="order.status">{{
                  orderStatusLabels[order.status] || order.status
                }}</span>
              </div>
            </article>
          </div>
        </details>
      </section>
    </div>
    <section class="panel daily-panel" aria-label="经营日报">
      <div class="section-heading">
        <div>
          <h2>经营日报</h2>
          <p>按日核对收款、退款和客户变化 · 共 {{ dailyTotal }} 天</p>
        </div>
        <button
          v-if="canExport"
          class="overview-link"
          :disabled="exporting || loading"
          @click="exportDaily"
        >
          <Download :size="14" />导出全部日期
        </button>
      </div>
      <div v-if="dailyError" role="alert" class="section-error">
        <XCircle :size="25" />
        <p>{{ dailyError }}</p>
        <button class="secondary-button" :disabled="dailyLoading" @click="loadDaily">
          重新加载日报
        </button>
      </div>
      <p v-if="!dailyError" class="daily-scroll-hint">
        <ArrowRight :size="13" />左右滑动查看完整日报
      </p>
      <div
        v-if="!dailyError"
        class="daily-table-wrap"
        :aria-busy="dailyLoading"
        tabindex="0"
        role="region"
        aria-label="经营日报明细，可左右滚动"
      >
        <table class="daily-table">
          <thead>
            <tr>
              <th scope="col">日期</th>
              <th scope="col">新建订单</th>
              <th scope="col">支付订单</th>
              <th scope="col">实收金额<small>元</small></th>
              <th scope="col">退款金额<small>元</small></th>
              <th scope="col">净收款<small>元</small></th>
              <th scope="col">新增客户</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="day in dailyRows" :key="day.date">
              <th scope="row">{{ day.date }}</th>
              <td>{{ count(day.metrics.createdOrders) }}</td>
              <td>{{ count(day.metrics.paidOrders) }}</td>
              <td>{{ money(day.metrics.receivedAmount) }}</td>
              <td>{{ money(day.metrics.refundAmount) }}</td>
              <td class="net-value" :class="{ negative: day.metrics.netReceivedAmount < 0 }">
                {{ money(day.metrics.netReceivedAmount) }}
              </td>
              <td>{{ count(day.metrics.newCustomers) }}</td>
            </tr>
            <tr v-if="!dailyRows.length">
              <td colspan="7" class="daily-empty">
                {{ dailyLoading ? '正在加载日报…' : '所选日期暂无日报' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!dailyError" class="daily-pagination">
        <span>共 {{ dailyTotal }} 天 · 每页 7 天 · 日期从近到远</span>
        <div>
          <button
            aria-label="日报上一页"
            :disabled="loading || dailyLoading || dailyPage <= 1"
            @click="changePage(-1)"
          >
            <ChevronLeft :size="15" /><span>上一页</span></button
          ><span>{{ dailyPage }} / {{ dailyPages }}</span
          ><button
            aria-label="日报下一页"
            :disabled="loading || dailyLoading || dailyPage >= dailyPages"
            @click="changePage(1)"
          >
            <span>下一页</span><ChevronRight :size="15" />
          </button>
        </div>
      </footer>
    </section>
    <details class="metric-definitions">
      <summary><Info :size="15" />统计口径与数据说明</summary>
      <div>
        <p>
          实收按支付时间统计，退款按售后完成时间统计；净收款为同期实收减同期退款。历史收款不会因后续退款而抹去。
        </p>
        <p>
          订单状态为所选日期创建订单的当前状态。业务待办为全量当前积压；工单预约时间已过且尚未完成时计为逾期。
        </p>
        <p>
          当前数据来自系统业务记录，不代表支付渠道已完成对账。不同区域由独立接口加载，刷新时可能存在短暂差异。
        </p>
      </div>
    </details>
  </div>
</template>
