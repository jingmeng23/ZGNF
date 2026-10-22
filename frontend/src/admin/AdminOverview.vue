<script setup lang="ts">
import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  CircleAlert,
  Clock3,
  PackageCheck,
  RefreshCw,
  ShoppingCart,
  UsersRound,
  Wallet,
} from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import { ref } from 'vue'
import { adminDemoMode } from '../api/admin'

const updatedAt = ref('刚刚更新')
const refresh = () => {
  updatedAt.value =
    new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) + ' 更新'
}
const stats = [
  {
    label: '今日成交额',
    value: '¥ 28,460',
    change: '+12.8%',
    up: true,
    icon: Wallet,
    note: '较昨日同期',
  },
  {
    label: '今日订单',
    value: '186',
    change: '+8.2%',
    up: true,
    icon: ShoppingCart,
    note: '较昨日同期',
  },
  {
    label: '待处理工单',
    value: '23',
    change: '5 项超时',
    up: false,
    icon: Clock3,
    note: '需要优先跟进',
  },
  {
    label: '新增客户',
    value: '64',
    change: '+4.5%',
    up: true,
    icon: UsersRound,
    note: '较昨日同期',
  },
]
const bars = [42, 57, 48, 66, 53, 74, 61, 78, 58, 85, 68, 92]
const activities = [
  {
    id: 'GN202610210084',
    customer: '陈女士',
    item: '燃气热水器 16L',
    amount: '¥ 2,399.00',
    status: '待派单',
    time: '15:42',
  },
  {
    id: 'GN202610210083',
    customer: '周先生',
    item: '燃气灶具安装服务',
    amount: '¥ 180.00',
    status: '服务中',
    time: '15:18',
  },
  {
    id: 'GN202610210082',
    customer: '李女士',
    item: '家用燃气报警器',
    amount: '¥ 199.00',
    status: '待付款',
    time: '14:56',
  },
  {
    id: 'GN202610210081',
    customer: '王先生',
    item: '燃气安全检查',
    amount: '免费',
    status: '已完成',
    time: '14:30',
  },
]
</script>

<template>
  <div v-if="!adminDemoMode" class="admin-page">
    <div class="page-heading">
      <div>
        <h1>经营总览</h1>
        <p>统计接口尚未实现，经营数据暂不可用。</p>
      </div>
    </div>
  </div>
  <div v-else class="admin-page">
    <div class="page-heading">
      <div>
        <span class="page-kicker">BUSINESS OVERVIEW</span>
        <h1>经营总览</h1>
        <p>掌握商城交易与上门服务的实时进展。</p>
      </div>
      <div class="heading-actions">
        <span class="date-range"
          ><CalendarDays :size="16" /> 2026-10-21 <span>至</span> 2026-10-21</span
        ><button class="icon-button" title="刷新数据" aria-label="刷新数据" @click="refresh">
          <RefreshCw :size="16" />
        </button>
      </div>
    </div>
    <div v-if="adminDemoMode" class="demo-banner">
      <CircleAlert :size="16" /><span>当前展示演示数据，管理端 API 尚未实现。</span
      ><span class="updated-at">{{ updatedAt }}</span>
    </div>
    <section class="stats-grid" aria-label="经营指标">
      <article v-for="stat in stats" :key="stat.label" class="stat-panel">
        <div class="stat-top">
          <span>{{ stat.label }}</span
          ><component :is="stat.icon" :size="18" />
        </div>
        <strong class="stat-value">{{ stat.value }}</strong>
        <div class="stat-foot">
          <span :class="stat.up ? 'trend-up' : 'trend-alert'"
            ><component :is="stat.up ? ArrowUpRight : ArrowDownRight" :size="14" />{{
              stat.change
            }}</span
          ><span>{{ stat.note }}</span>
        </div>
      </article>
    </section>
    <section class="overview-grid">
      <article class="panel revenue-panel">
        <div class="panel-heading">
          <div>
            <h2>成交趋势</h2>
            <p>今日各时段成交额（演示）</p>
          </div>
          <span class="select-button">今日 <CalendarDays :size="14" /></span>
        </div>
        <div class="chart-total">
          <strong>¥ 28,460</strong><span class="trend-up"><ArrowUpRight :size="14" />12.8%</span>
        </div>
        <div class="bar-chart" aria-label="今日各时段成交额趋势">
          <div v-for="(bar, index) in bars" :key="index" class="chart-column">
            <div class="chart-bar" :style="{ height: `${bar}%` }"></div>
            <span>{{ index * 2 }}:00</span>
          </div>
        </div>
      </article>
      <article class="panel distribution-panel">
        <div class="panel-heading">
          <div>
            <h2>订单概况</h2>
            <p>今日订单状态分布</p>
          </div>
          <RouterLink class="text-action" to="/admin/orders"
            >查看订单 <ArrowRight :size="14"
          /></RouterLink>
        </div>
        <div class="donut-wrap">
          <div class="donut">
            <div><strong>186</strong><span>总订单</span></div>
          </div>
          <div class="legend">
            <div><i class="legend-green"></i><span>已完成</span><strong>108</strong></div>
            <div><i class="legend-blue"></i><span>处理中</span><strong>46</strong></div>
            <div><i class="legend-amber"></i><span>待处理</span><strong>23</strong></div>
            <div><i class="legend-gray"></i><span>已取消</span><strong>9</strong></div>
          </div>
        </div>
        <div class="distribution-foot">
          <span><PackageCheck :size="15" />订单完成占比</span><strong>58.1%</strong>
        </div>
      </article>
    </section>
    <section class="panel orders-panel">
      <div class="panel-heading">
        <div>
          <h2>最新订单</h2>
          <p>最近创建的商城订单与服务订单</p>
        </div>
        <RouterLink class="text-action" to="/admin/orders"
          >全部订单 <ArrowRight :size="14"
        /></RouterLink>
      </div>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>订单编号</th>
              <th>客户</th>
              <th>商品 / 服务</th>
              <th>实付金额</th>
              <th>状态</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in activities" :key="row.id">
              <td class="table-id">{{ row.id }}</td>
              <td>{{ row.customer }}</td>
              <td>{{ row.item }}</td>
              <td class="amount-cell">{{ row.amount }}</td>
              <td>
                <span class="status-tag" :class="`status-${row.status}`">{{ row.status }}</span>
              </td>
              <td class="muted-cell">{{ row.time }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
