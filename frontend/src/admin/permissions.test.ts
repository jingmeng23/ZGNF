import { afterEach, describe, expect, it } from 'vitest'
import { adminProfile, can, canVisit, firstAdminPage } from './permissions'
afterEach(() => {
  adminProfile.value = null
})
describe('管理模块授权', () => {
  it('默认拒绝访问并提供无权限落地页', () => {
    expect(can('role:write')).toBe(false)
    expect(canVisit('/admin/roles')).toBe(false)
    expect(canVisit('/admin/unknown')).toBe(false)
    expect(firstAdminPage()).toBe('/admin/no-access')
  })
  it('只读角色可查看但不能修改，默认落到有权限的模块', () => {
    adminProfile.value = {
      employeeId: '1',
      username: 'reader',
      roles: ['reader'],
      permissions: ['role:read'],
    }
    expect(canVisit('/admin/roles')).toBe(true)
    expect(can('role:write')).toBe(false)
    expect(firstAdminPage()).toBe('/admin/roles')
  })
  it('仅有处理权限的员工可以进入自己的工单模块', () => {
    adminProfile.value = {
      employeeId: '2',
      username: 'worker',
      roles: ['worker'],
      permissions: ['workorder:process'],
    }
    expect(canVisit('/admin/work-orders')).toBe(true)
    expect(canVisit('/admin/employees')).toBe(false)
  })
})
