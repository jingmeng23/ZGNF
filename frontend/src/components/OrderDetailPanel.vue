<script setup lang="ts">
import { orderStatusLabels, type OrderRecord } from '../api/orders'
defineProps<{ order: OrderRecord }>()
const money = (value: number | null | undefined) => `¥${Number(value ?? 0).toFixed(2)}`
const paymentLabels: Record<string, string> = {
  pending: '待支付',
  processing: '支付处理中',
  success: '支付成功',
  failed: '支付失败',
  closed: '已关闭',
  refunded: '已退款',
}
</script>
<template>
  <div class="order-detail-content">
    <dl class="order-facts">
      <div>
        <dt>订单编号</dt>
        <dd>{{ order.orderNo }}</dd>
      </div>
      <div>
        <dt>订单状态</dt>
        <dd>{{ orderStatusLabels[order.status] || order.status }}</dd>
      </div>
      <div>
        <dt>创建时间</dt>
        <dd>{{ order.createTime }}</dd>
      </div>
      <div>
        <dt>支付期限</dt>
        <dd>{{ order.expireTime || '—' }}</dd>
      </div>
    </dl>
    <h3>收货信息</h3>
    <p v-if="order.address">
      {{ order.address.receiver }} · {{ order.address.phone }}<br />{{ order.address.province }}
      {{ order.address.city }} {{ order.address.district }} {{ order.address.detail }}
    </p>
    <p v-else>历史订单暂无地址快照</p>
    <p>下单备注：{{ order.remark || '无' }}</p>
    <h3>商品明细</h3>
    <div class="order-items-wrap">
      <table>
        <thead>
          <tr>
            <th>商品</th>
            <th>单价</th>
            <th>数量</th>
            <th>原金额</th>
            <th>优惠分摊</th>
            <th>应付分摊</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in order.items" :key="`${item.productId}-${index}`">
            <td>{{ item.productName }}</td>
            <td>{{ money(item.unitPrice) }}</td>
            <td>{{ item.quantity }}</td>
            <td>{{ money(item.amount) }}</td>
            <td>{{ money(item.discountAmount) }}</td>
            <td>{{ money(item.payAmount) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <dl class="order-facts">
      <div>
        <dt>商品总额</dt>
        <dd>{{ money(order.totalAmount) }}</dd>
      </div>
      <div>
        <dt>优惠总额</dt>
        <dd>{{ money(order.discountAmount) }}</dd>
      </div>
      <div>
        <dt>满减 / 优惠券</dt>
        <dd>{{ money(order.fullReductionAmount) }} / {{ money(order.couponDiscountAmount) }}</dd>
      </div>
      <div>
        <dt>运费</dt>
        <dd>{{ money(order.freightAmount) }}</dd>
      </div>
      <div>
        <dt>订单应付</dt>
        <dd>
          <strong>{{ money(order.payAmount) }}</strong>
        </dd>
      </div>
    </dl>
    <h3>物流信息</h3>
    <p v-if="order.logistics">
      {{ order.logistics.carrier }} · {{ order.logistics.trackingNo }}<br />{{
        order.logistics.summary
      }}
    </p>
    <p v-else>暂无物流记录</p>
    <h3>付款记录</h3>
    <ul v-if="order.payments?.length">
      <li v-for="payment in order.payments" :key="payment.paymentNo">
        {{ payment.paymentNo }} · {{ payment.channel }} ·
        {{ paymentLabels[payment.status] || payment.status }} · {{ money(payment.amount) }} ·
        {{ payment.paidTime || '尚未支付成功' }}
      </li>
    </ul>
    <p v-else>暂无付款记录</p>
    <h3>状态记录</h3>
    <ol v-if="order.timeline?.length">
      <li v-for="entry in order.timeline" :key="entry.id">
        {{ entry.occurredAt }} ·
        {{ entry.fromStatus ? `${orderStatusLabels[entry.fromStatus] || entry.fromStatus} → ` : ''
        }}{{ orderStatusLabels[entry.toStatus] || entry.toStatus }}
      </li>
    </ol>
    <p v-else>暂无状态记录</p>
  </div>
</template>
<style scoped>
.order-detail-content {
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.order-detail-content h3 {
  margin: 22px 0 8px;
}
.order-facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 12px;
  margin: 18px 0;
}
.order-facts > div {
  padding: 12px;
  background: #f5f7f5;
  border-radius: 6px;
}
.order-facts dt {
  color: #75837b;
}
.order-facts dd {
  margin: 0;
}
.order-items-wrap {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 10px;
  border-bottom: 1px solid #e8ece8;
  min-width: 70px;
}
ul,
ol {
  padding-left: 22px;
}
li {
  margin: 6px 0;
}
</style>
