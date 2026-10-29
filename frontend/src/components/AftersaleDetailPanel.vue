<script setup lang="ts">
import { aftersaleLabel, type AftersaleRecord, type AftersaleOperation } from '../api/aftersales'
defineProps<{ row: AftersaleRecord; operations: AftersaleOperation[]; admin?: boolean }>()
const actions: Record<string, string> = {
  apply: '提交申请',
  cancel: '用户撤销',
  approve: '审核通过',
  reject: '审核拒绝',
  complete: '登记退款完成',
}
</script>
<template>
  <div class="aftersale-detail-content">
    <dl>
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
        <dd>
          {{ aftersaleLabel(row) }}
        </dd>
      </div>
      <div>
        <dt>退款金额</dt>
        <dd>¥{{ Number(row.amount).toFixed(2) }}（整单实付金额）</dd>
      </div>
      <div>
        <dt>申请原因</dt>
        <dd>{{ row.reason }}</dd>
      </div>
      <div>
        <dt>提交时间</dt>
        <dd>{{ row.createTime }}</dd>
      </div>
      <div>
        <dt>审核意见</dt>
        <dd>{{ row.reviewRemark || '尚未审核' }}</dd>
      </div>
      <div v-if="admin && row.reviewedBy">
        <dt>审核人编号</dt>
        <dd>{{ row.reviewedBy }}</dd>
      </div>
      <div>
        <dt>退款渠道流水号</dt>
        <dd>{{ row.refundReference || '尚未登记' }}</dd>
      </div>
      <div>
        <dt>退款登记备注</dt>
        <dd>{{ row.completionRemark || '—' }}</dd>
      </div>
      <div>
        <dt>退款登记时间</dt>
        <dd>{{ row.completedTime || '—' }}</dd>
      </div>
    </dl>
    <h3>处理记录</h3>
    <ol v-if="operations.length" class="aftersale-timeline">
      <li v-for="operation in operations" :key="operation.id">
        <strong>{{ actions[operation.action] || operation.action }}</strong
        ><span
          >{{ operation.occurredAt }} · {{ operation.operatorType === 'admin' ? '管理员' : '申请人'
          }}<template v-if="admin"> #{{ operation.operatorId }}</template></span
        >
        <p>{{ operation.remark }}</p>
      </li>
    </ol>
    <p v-else>暂无处理记录。历史售后单仍可通过审核意见和退款登记信息查看处理结果。</p>
  </div>
</template>
<style scoped>
dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 28px;
  margin: 22px 0;
}
dl > div {
  min-width: 0;
}
dt {
  color: #75837b;
  font-size: 12px;
  margin-bottom: 5px;
}
dd {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
h3 {
  font-size: 14px;
}
.aftersale-timeline {
  padding-left: 22px;
}
.aftersale-timeline li {
  padding: 8px 0;
}
.aftersale-timeline span {
  display: block;
  margin-top: 5px;
  color: #75837b;
  font-size: 12px;
}
.aftersale-timeline p {
  margin: 6px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
@media (max-width: 700px) {
  dl {
    grid-template-columns: 1fr;
  }
}
</style>
