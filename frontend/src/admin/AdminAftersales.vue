<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import {
  adminAftersalesApi,
  aftersaleStatusLabels,
  aftersaleLabel,
  type AftersaleRecord,
  type AftersaleOperation,
  type AftersaleQuery,
} from '../api/aftersales'
import { ApiError } from '../api/http'
import AftersaleDetailPanel from '../components/AftersaleDetailPanel.vue'

const rows = ref<AftersaleRecord[]>([]),
  selected = ref<AftersaleRecord | null>(null)
const operations = ref<AftersaleOperation[]>([]),
  total = ref(0),
  pages = ref(1)
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  notice = ref('')
const detail = ref<HTMLElement>(),
  decision = ref<'approved' | 'rejected'>('approved')
const reviewRemark = ref(''),
  refundReference = ref(''),
  completionRemark = ref(''),
  confirmed = ref(false)
const query = reactive<AftersaleQuery>({
  page: 1,
  size: 20,
  status: '',
  aftersaleNo: '',
  orderNo: '',
})
let sequence = 0
function explain(e: unknown) {
  return e instanceof ApiError && e.traceId
    ? `${e.message}（请求编号：${e.traceId}）`
    : e instanceof Error
      ? e.message
      : '操作失败，请重试'
}
async function load() {
  const version = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await adminAftersalesApi.list({
      ...query,
      aftersaleNo: query.aftersaleNo.trim(),
      orderNo: query.orderNo.trim(),
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
  if (loading.value || busy.value) return
  query.page = Math.max(1, Math.min(pages.value, query.page + delta))
  void load()
}
async function open(id: string) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const [row, history] = await Promise.all([
      adminAftersalesApi.detail(id),
      adminAftersalesApi.operations(id),
    ])
    selected.value = row
    operations.value = history
    reviewRemark.value = ''
    refundReference.value = ''
    completionRemark.value = ''
    confirmed.value = false
    await nextTick()
    detail.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) {
    selected.value = null
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function act(action: 'review' | 'complete') {
  if (!selected.value || busy.value) return
  if (action === 'review' && !reviewRemark.value.trim()) {
    error.value = '请填写审核意见'
    return
  }
  if (action === 'complete' && (!confirmed.value || !refundReference.value.trim())) {
    error.value = '请先确认已在支付渠道退款，并填写真实退款流水号'
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    selected.value =
      action === 'review'
        ? await adminAftersalesApi.review(
            selected.value.id,
            decision.value,
            reviewRemark.value.trim(),
          )
        : await adminAftersalesApi.complete(
            selected.value.id,
            refundReference.value.trim(),
            completionRemark.value.trim(),
          )
    notice.value =
      action === 'review' ? '审核结果已保存' : '渠道退款完成记录已保存，订单与支付状态已同步'
    confirmed.value = false
    await load()
    operations.value = await adminAftersalesApi.operations(selected.value.id)
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
onMounted(load)
onUnmounted(() => sequence++)
</script>
<template>
  <div class="admin-page aftersales-page">
    <header class="page-heading">
      <div>
        <span class="page-kicker">AFTERSALES</span>
        <h1>售后管理</h1>
        <p>审核整单退款申请，登记渠道退款结果，追踪完整处理记录。</p>
      </div>
      <button class="secondary-button" :disabled="loading || busy" @click="load">
        <RefreshCw :size="15" />刷新
      </button>
    </header>
    <p class="demo-banner">
      此页面登记已完成的渠道退款，不会向客户发起打款。请先核实支付渠道退款成功，再登记流水号。
    </p>
    <p v-if="error" role="alert" class="demo-banner">{{ error }}</p>
    <p v-if="notice" role="status" class="demo-banner">{{ notice }}</p>
    <section class="panel list-panel" :aria-busy="loading">
      <form class="list-toolbar aftersale-filters" @submit.prevent="search">
        <input
          v-model="query.aftersaleNo"
          maxlength="40"
          placeholder="完整售后单号"
          aria-label="售后单号"
        /><input
          v-model="query.orderNo"
          maxlength="40"
          placeholder="完整订单编号"
          aria-label="订单编号"
        /><select v-model="query.status" aria-label="售后状态">
          <option value="">全部状态</option>
          <option v-for="(label, state) in aftersaleStatusLabels" :key="state" :value="state">
            {{ label }}
          </option></select
        ><button class="primary-button" :disabled="loading || busy">查询</button>
      </form>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>售后单号 / 订单</th>
              <th>申请原因</th>
              <th>整单退款金额</th>
              <th>状态</th>
              <th>申请时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>
                <strong>{{ row.aftersaleNo }}</strong
                ><small>{{ row.orderNo || row.orderId }}</small>
              </td>
              <td class="reason-cell">{{ row.reason }}</td>
              <td>¥{{ Number(row.amount).toFixed(2) }}</td>
              <td>
                <span class="status-tag" :class="`aftersale-${row.status}`">{{
                  aftersaleLabel(row)
                }}</span>
              </td>
              <td>{{ row.createTime }}</td>
              <td>
                <button class="text-action" :disabled="busy" @click="open(row.id)">
                  {{
                    row.status === 'pending'
                      ? '审核 / 详情'
                      : row.status === 'approved'
                        ? '登记退款 / 详情'
                        : '查看详情'
                  }}
                </button>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6" class="empty-state">
                {{
                  loading
                    ? '正在加载售后申请…'
                    : error
                      ? '加载失败，请刷新重试'
                      : '暂无匹配的售后申请'
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="pagination">
        <span>共 {{ total }} 条申请</span>
        <div>
          <select
            v-model.number="query.size"
            aria-label="每页数量"
            :disabled="loading || busy"
            @change="search"
          >
            <option :value="10">10 条 / 页</option>
            <option :value="20">20 条 / 页</option>
            <option :value="50">50 条 / 页</option></select
          ><button :disabled="loading || busy || query.page <= 1" @click="page(-1)">上一页</button
          ><span>{{ query.page }} / {{ pages }}</span
          ><button :disabled="loading || busy || query.page >= pages" @click="page(1)">
            下一页
          </button>
        </div>
      </footer>
    </section>
    <section v-if="selected" ref="detail" class="panel aftersale-editor">
      <div class="panel-heading">
        <h2>售后详情与处理</h2>
        <div class="aftersale-actions">
          <button class="secondary-button" :disabled="busy" @click="open(selected.id)">
            刷新详情</button
          ><button class="secondary-button" :disabled="busy" @click="selected = null">关闭</button>
        </div>
      </div>
      <AftersaleDetailPanel :row="selected" :operations="operations" admin />
      <form
        v-if="selected.status === 'pending'"
        class="aftersale-form"
        @submit.prevent="act('review')"
      >
        <h3>审核申请</h3>
        <label
          >审核结果<select v-model="decision" :disabled="busy">
            <option value="approved">同意退款</option>
            <option value="rejected">拒绝申请</option>
          </select></label
        ><label
          >审核意见<textarea
            v-model="reviewRemark"
            required
            maxlength="500"
            rows="3"
            :disabled="busy"
            placeholder="说明审核依据；拒绝申请时请给出具体原因"
          /></label
        ><button class="primary-button" :disabled="busy">
          {{ busy ? '保存中…' : '确认审核结果' }}
        </button>
      </form>
      <form
        v-if="selected.status === 'approved'"
        class="aftersale-form"
        @submit.prevent="act('complete')"
      >
        <h3>登记渠道退款完成</h3>
        <label
          >支付渠道退款流水号<input
            v-model="refundReference"
            required
            maxlength="100"
            :disabled="busy" /></label
        ><label
          >退款登记备注<textarea
            v-model="completionRemark"
            maxlength="500"
            rows="3"
            :disabled="busy"
          /></label
        ><label class="confirmation"
          ><input
            v-model="confirmed"
            type="checkbox"
            required
            :disabled="busy"
          />已核实支付渠道按本单金额实际退款成功</label
        ><button class="primary-button" :disabled="busy || !confirmed">
          {{ busy ? '保存中…' : '登记退款完成' }}
        </button>
      </form>
    </section>
  </div>
</template>
<style scoped>
.aftersale-filters {
  flex-wrap: wrap;
}
.aftersale-filters input,
.aftersale-filters select,
.aftersale-form input:not([type='checkbox']),
.aftersale-form textarea,
.aftersale-form select {
  border: 1px solid #dce4df;
  border-radius: 5px;
  padding: 9px;
  font: inherit;
  max-width: 100%;
  min-width: 0;
  background: white;
}
.aftersale-editor {
  padding: 22px;
  scroll-margin-top: 24px;
}
.aftersale-form {
  display: grid;
  gap: 14px;
  padding-top: 15px;
  margin-top: 18px;
  border-top: 1px solid #e8ece8;
}
.aftersale-form label {
  display: grid;
  gap: 7px;
}
.aftersale-form button {
  justify-self: start;
}
.aftersale-form .confirmation,
.aftersale-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.aftersales-page td small {
  display: block;
  color: #75837b;
  margin-top: 5px;
}
.reason-cell {
  max-width: 230px;
  white-space: normal;
  overflow-wrap: anywhere;
}
.aftersale-pending,
.aftersale-approved {
  color: #a7792d;
  background: #fff5e5;
}
.aftersale-completed {
  color: #257251;
  background: #eaf4ed;
}
.aftersale-rejected {
  color: #75837b;
  background: #f0f2f0;
}
@media (max-width: 700px) {
  .aftersale-editor {
    padding: 14px;
  }
  .aftersale-filters > * {
    max-width: 100%;
  }
}
</style>
