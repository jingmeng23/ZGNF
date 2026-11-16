<script setup lang="ts">
import { aftersaleLabel, type AftersaleRecord, type AftersaleOperation } from '../api/aftersales'

defineProps<{
  row: AftersaleRecord
  operations: AftersaleOperation[]
  admin?: boolean
  historyError?: string
}>()
const actions: Record<string, string> = {
  apply: '提交申请',
  cancel: '用户撤销',
  approve: '审核通过',
  reject: '审核拒绝',
  complete: '退款完成',
}
const dateTime = (value: string | null) => (value ? value.replace('T', ' ').slice(0, 19) : '—')
</script>
<template>
  <div class="aftersale-detail-content">
    <section class="detail-group">
      <h3>申请信息</h3>
      <dl class="application-facts">
        <div>
          <dt>售后单号</dt>
          <dd>{{ row.aftersaleNo }}</dd>
        </div>
        <div>
          <dt>关联订单</dt>
          <dd>{{ row.orderNo || row.orderId }}</dd>
        </div>
        <div>
          <dt>当前状态</dt>
          <dd>{{ aftersaleLabel(row) }}</dd>
        </div>
        <div>
          <dt>退款金额</dt>
          <dd class="amount">
            ¥{{
              Number(row.amount).toLocaleString('zh-CN', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              })
            }}<small>整单实付金额</small>
          </dd>
        </div>
        <div>
          <dt>申请时间</dt>
          <dd>{{ dateTime(row.createTime) }}</dd>
        </div>
        <div class="full-width">
          <dt>申请原因</dt>
          <dd class="reason">{{ row.reason }}</dd>
        </div>
      </dl>
    </section>
    <section
      v-if="row.reviewRemark || row.refundReference || row.status !== 'pending'"
      class="detail-group"
    >
      <h3>处理信息</h3>
      <dl>
        <div class="full-width">
          <dt>审核意见</dt>
          <dd>{{ row.reviewRemark || '尚未审核' }}</dd>
        </div>
        <div v-if="admin && row.reviewedBy">
          <dt>审核人编号</dt>
          <dd>{{ row.reviewedBy }}</dd>
        </div>
        <template v-if="row.refundReference || row.status === 'completed'">
          <div class="full-width">
            <dt>退款流水号</dt>
            <dd>{{ row.refundReference || '尚未登记' }}</dd>
          </div>
          <div class="full-width">
            <dt>退款登记备注</dt>
            <dd>{{ row.completionRemark || '—' }}</dd>
          </div>
          <div class="full-width">
            <dt>退款完成时间</dt>
            <dd>{{ dateTime(row.completedTime) }}</dd>
          </div>
        </template>
      </dl>
    </section>
    <section class="detail-group history-group">
      <h3>
        处理记录 <span v-if="operations.length">{{ operations.length }}</span>
      </h3>
      <p v-if="historyError" role="alert" class="history-error">
        处理记录加载失败：{{ historyError }}
      </p>
      <ol v-else-if="operations.length" class="aftersale-timeline">
        <li v-for="operation in operations" :key="operation.id" :class="operation.action">
          <div class="timeline-heading">
            <strong>{{ actions[operation.action] || operation.action }}</strong
            ><time>{{ dateTime(operation.occurredAt) }}</time>
          </div>
          <span class="operator"
            >{{ operation.operatorType === 'admin' ? '管理员' : '申请人'
            }}<template v-if="admin"> · {{ operation.operatorId }}</template></span
          >
          <p v-if="operation.remark">{{ operation.remark }}</p>
        </li>
      </ol>
      <p v-else class="empty-history">
        暂无处理记录。历史售后单可通过审核意见和退款信息查看处理结果。
      </p>
    </section>
  </div>
</template>
<style scoped>
.aftersale-detail-content {
  display: grid;
  gap: 16px;
  color: #405748;
}
.detail-group {
  min-width: 0;
  padding: 20px;
  background: white;
  border: 1px solid #e4ebe5;
  border-radius: 10px;
}
.detail-group h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 19px;
  color: #3d5946;
}
.detail-group h3 > span {
  font-size: 10px;
  color: #8c9c91;
  background: #f1f5f1;
  padding: 2px 6px;
  border-radius: 5px;
  font-weight: 400;
}
dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 22px;
  margin: 0;
}
dl > div {
  min-width: 0;
}
.full-width {
  grid-column: 1 / -1;
}
dt {
  color: #92a095;
  font-size: 11px;
  margin-bottom: 7px;
}
dd {
  font-size: 12px;
  margin: 0;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.amount {
  color: #3d684c;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.amount small {
  display: block;
  margin-top: 4px;
  color: #9aa69d;
  font-size: 10px;
  font-weight: 400;
}
.reason {
  padding: 11px 13px;
  border-radius: 7px;
  background: #f5f8f5;
  color: #697e6f;
}
.aftersale-timeline {
  list-style: none;
  padding: 0 0 0 15px;
  margin: 0;
}
.aftersale-timeline li {
  position: relative;
  border-left: 1px solid #e1eae2;
  padding: 0 0 23px 20px;
}
.aftersale-timeline li:last-child {
  padding-bottom: 0;
  border-color: transparent;
}
.aftersale-timeline li::before {
  position: absolute;
  content: '';
  left: -4px;
  top: 5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #87aa91;
  box-shadow: 0 0 0 4px white;
}
.aftersale-timeline .reject::before,
.aftersale-timeline .cancel::before {
  background: #b4beb6;
}
.timeline-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 12px;
}
.timeline-heading strong {
  font-size: 12px;
  font-weight: 500;
  color: #4f6b58;
}
.timeline-heading time {
  color: #9aa79e;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}
.operator {
  display: block;
  color: #9aa79e;
  font-size: 10px;
  margin-top: 7px;
}
.aftersale-timeline p {
  margin: 9px 0 0;
  color: #6d8173;
  font-size: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.empty-history,
.history-error {
  margin: 0;
  line-height: 1.9;
  color: #97a39a;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.history-error {
  color: #ad695b;
}
@media (max-width: 420px) {
  .detail-group {
    padding: 17px;
  }
  dl {
    gap: 16px;
  }
}
</style>
