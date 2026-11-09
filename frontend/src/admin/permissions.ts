import { ref } from 'vue'
import { adminAuthApi, type AdminLoginResponse } from '../api/adminAuth'
import { adminDemoMode } from '../api/admin'
import { loginDisabled } from '../platform/testMode'

export const adminProfile = ref<AdminLoginResponse['admin'] | null>(null)
export const moduleNames: Record<string, string> = {
  statistics: '经营统计',
  order: '订单',
  product: '商品',
  category: '分类',
  inventory: '库存',
  workorder: '工单',
  aftersale: '售后',
  employee: '员工',
  user: '用户',
  role: '角色权限',
  operation: '运营与系统',
}
const pagePermissions: Record<string, string[]> = {
  '/admin': ['statistics:read'],
  '/admin/orders': ['order:read'],
  '/admin/products': ['product:read'],
  '/admin/categories': ['category:read'],
  '/admin/inventory': ['inventory:read'],
  '/admin/work-orders': ['workorder:read', 'workorder:process'],
  '/admin/aftersales': ['aftersale:read'],
  '/admin/employees': ['employee:read'],
  '/admin/users': ['user:read'],
  '/admin/roles': ['role:read'],
  '/admin/coupons': ['operation:read'],
  '/admin/logs': ['operation:read'],
  '/admin/config': ['operation:read'],
}
export function can(permission: string) {
  return loginDisabled || adminDemoMode || !!adminProfile.value?.permissions.includes(permission)
}
export function canVisit(path: string) {
  return (pagePermissions[path] || []).some(can)
}
export function firstAdminPage() {
  return Object.keys(pagePermissions).find(canVisit) || '/admin/no-access'
}
export async function refreshAdminProfile() {
  if (loginDisabled || adminDemoMode) return
  adminProfile.value = null
  adminProfile.value = await adminAuthApi.me()
}
