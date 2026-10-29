<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  userAftersalesApi,
  aftersaleStatusLabels,
  aftersaleLabel,
  type AftersaleRecord,
  type AftersaleOperation,
  type AftersaleQuery,
} from '../api/aftersales'
import { userOrdersApi, type OrderRecord } from '../api/orders'
import AftersaleDetailPanel from '../components/AftersaleDetailPanel.vue'

const route = useRoute(),
  router = useRouter()
const rows = ref<AftersaleRecord[]>([]),
  selected = ref<AftersaleRecord | null>(null),
  operations = ref<AftersaleOperation[]>([])
const applicationOrder = ref<OrderRecord | null>(null),
  reason = ref(''),
  requestId = ref('')
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  message = ref(''),
  total = ref(0),
  pages = ref(1)
const detail = ref<HTMLElement>()
const query = reactive<AftersaleQuery>({
  page: 1,
  size: 10,
  status: '',
  aftersaleNo: '',
  orderNo: '',
})
let sequence = 0,
  applicationVersion = 0
function explain(e: unknown) {
  return e instanceof Error ? e.message : '操作失败，请重试'
}
async function load() {
  const version = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await userAftersalesApi.list({
      ...query,
      orderNo: query.orderNo.trim(),
      aftersaleNo: query.aftersaleNo.trim(),
    })
    if (version !== sequence) return
    rows.value = result.list
    total.value = result.total
    pages.value = Math.max(1, result.pages)
  } catch (e) {
    if (version === sequence) error.value = explain(e)
  } finally {
    if (version === sequence) loading.value = false
  }
}
function search() {
  query.page = 1
  void load()
}
function page(delta: number) {
  if (busy.value || loading.value) return
  query.page = Math.max(1, Math.min(pages.value, query.page + delta))
  void load()
}
async function prepareApplication(value: unknown) {
  const version = ++applicationVersion
  applicationOrder.value = null
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) return
  busy.value = true
  error.value = ''
  try {
    const order = await userOrdersApi.detail(value)
    if (version !== applicationVersion) return
    if (!['paid', 'processing', 'completed'].includes(order.status))
      throw new Error('当前订单状态不支持售后申请')
    applicationOrder.value = order
    reason.value = ''
    requestId.value = crypto.randomUUID()
  } catch (e) {
    if (version === applicationVersion) error.value = explain(e)
  } finally {
    if (version === applicationVersion) busy.value = false
  }
}
async function open(id: string) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const [row, history] = await Promise.all([
      userAftersalesApi.detail(id),
      userAftersalesApi.operations(id),
    ])
    selected.value = row
    operations.value = history
    await nextTick()
    detail.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) {
    selected.value = null
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function apply() {
  if (busy.value || !applicationOrder.value) return
  if (!reason.value.trim()) {
    error.value = '请填写申请原因'
    return
  }
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    selected.value = await userAftersalesApi.create({
      orderId: applicationOrder.value.id,
      reason: reason.value.trim(),
      requestId: requestId.value,
    })
    applicationOrder.value = null
    message.value = '售后申请已提交，请等待审核'
    query.page = 1
    await router.replace('/aftersales')
    await load()
    operations.value = await userAftersalesApi.operations(selected.value.id)
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function cancel() {
  if (busy.value || !selected.value || !window.confirm('确认撤销这条待审核的售后申请？')) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    selected.value = await userAftersalesApi.cancel(selected.value.id)
    message.value = '售后申请已撤销'
    await load()
    operations.value = await userAftersalesApi.operations(selected.value.id)
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
watch(() => route.query.orderId, prepareApplication)
onMounted(() => {
  void load()
  void prepareApplication(route.query.orderId)
})
onUnmounted(() => {
  sequence++
  applicationVersion++
})
</script>
<template>
  <header class="page-heading">
    <span class="eyebrow">AFTERSALES</span>
    <h1>我的售后</h1>
    <p>提交整单退款申请，查看审核意见与退款处理进度。</p>
  </header>
  <p v-if="error" role="alert" class="inline-notice">{{ error }}</p>
  <p v-if="message" role="status" class="inline-notice">{{ message }}</p>
  <section v-if="applicationOrder" class="panel aftersale-card">
    <h2>申请整单退款</h2>
    <p>
      订单 {{ applicationOrder.orderNo }} · 整单实付 ¥{{
        Number(applicationOrder.payAmount).toFixed(2)
      }}
    </p>
    <p>退款金额由服务端核对支付记录确定。申请通过后，由客服在支付渠道处理退款。</p>
    <form class="aftersale-form" @submit.prevent="apply">
      <label
        >申请原因<textarea
          v-model="reason"
          required
          maxlength="500"
          rows="4"
          :disabled="busy"
          placeholder="请说明需要退款的原因"
        />
      </label>
      <div class="aftersale-tools">
        <button class="button" :disabled="busy">{{ busy ? '提交中…' : '提交申请' }}</button
        ><button
          type="button"
          class="button secondary"
          :disabled="busy"
          @click="router.replace('/aftersales')"
        >
          取消填写
        </button>
      </div>
    </form>
  </section>
  <div class="aftersale-tools">
    <RouterLink to="/orders" class="button secondary">从订单详情申请售后</RouterLink
    ><button class="button secondary" :disabled="busy || loading" @click="load">刷新记录</button>
  </div>
  <form class="aftersale-tools" @submit.prevent="search">
    <select v-model="query.status" aria-label="售后状态">
      <option value="">全部状态</option>
      <option v-for="(label, state) in aftersaleStatusLabels" :key="state" :value="state">
        {{ label }}
      </option></select
    ><input
      v-model="query.orderNo"
      maxlength="40"
      aria-label="订单编号"
      placeholder="完整订单编号"
    /><button class="button secondary" :disabled="busy || loading">查询</button>
  </form>
  <p v-if="loading">正在加载售后记录…</p>
  <p v-else-if="!rows.length" class="state">
    {{ error ? '加载失败，请刷新重试' : '暂无匹配的售后申请' }}
  </p>
  <div class="aftersale-list">
    <article v-for="row in rows" :key="row.id" class="panel aftersale-card">
      <div class="aftersale-tools">
        <strong>{{ row.aftersaleNo }}</strong
        ><span>{{ aftersaleLabel(row) }}</span>
      </div>
      <p>订单 {{ row.orderNo || row.orderId }} · ¥{{ Number(row.amount).toFixed(2) }}</p>
      <p>{{ row.reason }}</p>
      <button class="button secondary" :disabled="busy" @click="open(row.id)">
        查看详情与处理记录
      </button>
    </article>
  </div>
  <div class="aftersale-tools">
    <span>共 {{ total }} 条 · {{ query.page }} / {{ pages }}</span
    ><button
      class="button secondary"
      :disabled="busy || loading || query.page <= 1"
      @click="page(-1)"
    >
      上一页</button
    ><button
      class="button secondary"
      :disabled="busy || loading || query.page >= pages"
      @click="page(1)"
    >
      下一页
    </button>
  </div>
  <section v-if="selected" ref="detail" class="panel aftersale-card">
    <div class="aftersale-tools">
      <h2>售后详情</h2>
      <button class="button secondary" :disabled="busy" @click="open(selected.id)">刷新详情</button
      ><button class="button secondary" :disabled="busy" @click="selected = null">关闭</button>
    </div>
    <AftersaleDetailPanel :row="selected" :operations="operations" /><button
      v-if="selected.status === 'pending'"
      class="button secondary"
      :disabled="busy"
      @click="cancel"
    >
      撤销申请
    </button>
  </section>
</template>
<style scoped>
.aftersale-card {
  padding: 24px;
  margin: 16px 0;
  scroll-margin-top: 24px;
  overflow-wrap: anywhere;
}
.aftersale-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin: 18px 0;
}
.aftersale-form label {
  display: grid;
  gap: 8px;
}
.aftersale-form textarea,
.aftersale-tools input,
.aftersale-tools select {
  border: 1px solid #dce4df;
  border-radius: 6px;
  padding: 10px;
  font: inherit;
  min-width: 0;
  max-width: 100%;
}
.aftersale-card p {
  line-height: 1.8;
  white-space: pre-wrap;
}
@media (max-width: 700px) {
  .aftersale-card {
    padding: 14px;
  }
}
</style>
