<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  Activity,
  Archive,
  BadgePercent,
  Boxes,
  Building2,
  ChevronDown,
  ClipboardList,
  Flame,
  LayoutDashboard,
  ScrollText,
  Settings2,
  ShieldCheck,
  ShoppingBag,
  Store,
  Users,
  Wrench,
} from 'lucide-vue-next'
import './admin.css'
import { adminDemoMode } from '../api/admin'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
function logout() {
  auth.logout()
  void router.replace('/admin/login')
}
const sections = [
  {
    title: '经营',
    links: [
      { path: '/admin', label: '经营总览', icon: LayoutDashboard, exact: true },
      { path: '/admin/orders', label: '订单管理', icon: ShoppingBag, count: '12' },
      { path: '/admin/products', label: '商品管理', icon: Boxes },
      { path: '/admin/inventory', label: '库存管理', icon: Archive },
    ],
  },
  {
    title: '服务',
    links: [
      { path: '/admin/work-orders', label: '服务工单', icon: Wrench, count: '5' },
      { path: '/admin/aftersales', label: '售后管理', icon: ClipboardList },
      { path: '/admin/coupons', label: '优惠券', icon: BadgePercent },
    ],
  },
  {
    title: '组织与系统',
    links: [
      { path: '/admin/stores', label: '门店管理', icon: Store },
      { path: '/admin/employees', label: '员工管理', icon: Users },
      { path: '/admin/roles', label: '角色权限', icon: ShieldCheck },
      { path: '/admin/logs', label: '操作日志', icon: ScrollText },
      { path: '/admin/config', label: '系统配置', icon: Settings2 },
    ],
  },
]
const pageName = computed(
  () =>
    sections.flatMap((section) => section.links).find((link) => link.path === route.path)?.label ??
    '业务管理',
)
</script>

<template>
  <div class="admin-shell">
    <RouterView v-if="route.path === '/admin/login'" />
    <template v-else>
      <aside class="admin-sidebar">
        <RouterLink to="/admin" class="admin-brand"
          ><span class="admin-brand-mark"><Flame :size="20" /></span
          ><span>中南燃服<small>运营管理平台</small></span></RouterLink
        >
        <div class="admin-workspace">
          <span class="workspace-icon"><Building2 :size="17" /></span
          ><span><strong>总部运营中心</strong><small>全域数据</small></span
          ><ChevronDown :size="15" />
        </div>
        <nav class="admin-nav" aria-label="管理导航">
          <section v-for="section in sections" :key="section.title" class="admin-nav-section">
            <p>{{ section.title }}</p>
            <RouterLink
              v-for="link in section.links"
              :key="link.path"
              :to="link.path"
              :aria-label="link.label"
              :title="link.label"
              class="admin-nav-link"
              :class="{
                active: link.exact ? route.path === link.path : route.path.startsWith(link.path),
              }"
            >
              <component :is="link.icon" :size="17" :stroke-width="1.8" /><span>{{
                link.label
              }}</span
              ><b v-if="adminDemoMode && link.count">{{ link.count }}</b>
            </RouterLink>
          </section>
        </nav>
        <div class="admin-sidebar-foot">
          <span class="online-dot"></span>{{ adminDemoMode ? '本地演示模式' : '接口模式' }}
          <span>v0.1</span>
        </div>
      </aside>
      <main class="admin-main">
        <header class="admin-topbar">
          <div class="admin-breadcrumb">
            <span>运营管理</span><span>/</span><strong>{{ pageName }}</strong>
          </div>
          <div class="admin-top-actions">
            <span class="demo-indicator"
              ><Activity :size="14" /> {{ adminDemoMode ? '演示环境' : '管理平台' }}</span
            ><span class="admin-user"
              ><span class="avatar">{{ adminDemoMode ? '演' : '管' }}</span
              ><span
                ><strong>{{ adminDemoMode ? '演示管理员' : '管理员' }}</strong
                ><small>{{ adminDemoMode ? '示例身份' : '已登录' }}</small></span
              ></span
            ><button v-if="!adminDemoMode" class="text-action" @click="logout">退出登录</button>
          </div>
        </header>
        <div class="admin-content"><RouterView /></div>
      </main>
    </template>
  </div>
</template>
