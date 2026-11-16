import { ApiError, request } from './http'
import type { OrderStatus } from './orders'
import type { PageResult } from './types'

export interface StatisticsRange {
  startDate: string
  endDate: string
}
export interface BusinessMetrics {
  createdOrders: number
  paidOrders: number
  receivedAmount: number
  refundedOrders: number
  refundAmount: number
  netReceivedAmount: number
  newCustomers: number
}
export interface RecentOrder {
  id: string
  orderNo: string
  status: OrderStatus
  payAmount: number
  createTime: string
}
export interface BusinessOverview extends StatisticsRange {
  previousStartDate: string
  previousEndDate: string
  current: BusinessMetrics
  previous: BusinessMetrics
  receivedGrowthPercent: number | null
  orderGrowthPercent: number | null
  pendingWorkOrders: number
  overdueWorkOrders: number
  orderStatuses: Record<OrderStatus, number>
  recentOrders: RecentOrder[]
  todos: { paidOrders: number; pendingAftersales: number; pendingRefunds: number }
}
export interface BusinessDaily {
  date: string
  metrics: BusinessMetrics
}
export interface ProductSales {
  productId: string
  productName: string
  paidOrders: number
  quantity: number
  merchandiseAmount: number
}
function params(range: StatisticsRange, extra: Record<string, string | number> = {}) {
  return new URLSearchParams(
    Object.entries({ ...range, ...extra }).map(([key, value]) => [key, String(value)]),
  )
}
const root = '/admin/statistics'
export const statisticsApi = {
  overview: (range: StatisticsRange) =>
    request<BusinessOverview>(`${root}/overview?${params(range)}`),
  trend: (range: StatisticsRange) => request<BusinessDaily[]>(`${root}/trend?${params(range)}`),
  products: (range: StatisticsRange, limit = 5) =>
    request<ProductSales[]>(`${root}/products?${params(range, { limit })}`),
  daily: (range: StatisticsRange, page = 1, size = 10) =>
    request<PageResult<BusinessDaily>>(
      `${root}/reports/daily?${params(range, { page, size, sort: 'date', order: 'desc' })}`,
    ),
  exportDaily: (range: StatisticsRange) =>
    request<string>(`${root}/reports/daily/export?${params(range)}`, {}, 30000, 'csv'),
}
export function localizedDailyCsv(csv: string) {
  const lines = csv.replace(/^\uFEFF/, '').split(/\r?\n/)
  if (
    lines[0] !==
    'date,created_orders,paid_orders,received_amount,refunded_orders,refund_amount,net_received_amount,new_customers'
  ) {
    throw new ApiError('日报导出格式不正确，请重试', 0)
  }
  lines[0] = '日期,新建订单数,支付订单数,实收金额,退款订单数,退款金额,净收款,新增客户数'
  return '\uFEFF' + lines.join('\r\n')
}
