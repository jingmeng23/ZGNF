import { createRouter, createWebHashHistory } from 'vue-router'
import { session } from '../platform/session'
import { adminDemoMode } from '../api/admin'
import { hasAdminAccess } from '../admin/access'
import { modules } from '../admin/adminData'

export const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/admin/login',
      component: () => import('../admin/AdminLogin.vue'),
      meta: { title: '管理员登录' },
    },
    {
      path: '/admin',
      name: 'admin-home',
      component: () => import('../admin/AdminOverview.vue'),
      meta: { title: '经营总览' },
    },
    {
      path: '/admin/:module',
      name: 'admin-module',
      component: () => import('../admin/AdminModuleView.vue'),
      meta: { title: '业务管理' },
    },
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
      meta: { title: '首页' },
    },
    {
      path: '/catalog',
      component: () => import('../views/CatalogView.vue'),
      meta: { title: '好物商城' },
    },
    {
      path: '/services',
      component: () => import('../views/ServicesView.vue'),
      meta: { title: '安心服务' },
    },
    { path: '/cart', component: () => import('../views/CartView.vue'), meta: { title: '购物车' } },
    {
      path: '/account',
      component: () => import('../views/AccountView.vue'),
      meta: { title: '我的' },
    },
    {
      path: '/orders',
      component: () => import('../views/OrdersView.vue'),
      meta: { title: '我的订单', requiresAuth: true },
    },
    {
      path: '/login',
      component: () => import('../views/LoginView.vue'),
      meta: { title: '手机登录' },
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('../views/NotFoundView.vue'),
      meta: { title: '页面未找到' },
    },
  ],
})
router.beforeEach((to) => {
  if (to.path.startsWith('/admin') && to.path !== '/admin/login') {
    if (to.params.module && !Object.hasOwn(modules, String(to.params.module))) return '/admin'
    if (!adminDemoMode && !hasAdminAccess())
      return { path: '/admin/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAuth && !session.read())
    return { path: '/login', query: { redirect: to.fullPath } }
  document.title = `${modules[String(to.params.module)]?.title || String(to.meta.title || '智慧商城')} · 中南燃服`
})
