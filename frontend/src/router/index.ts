import { createRouter, createWebHashHistory } from 'vue-router'
import { session } from '../platform/session'
import { adminDemoMode } from '../api/admin'
import { hasAdminAccess } from '../admin/access'
import { modules } from '../admin/adminData'
import { loginDisabled } from '../platform/testMode'

export const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/admin/employees', component: () => import('../admin/AdminEmployees.vue'), meta: { title: '员工管理' } },
    { path: '/admin/users', component: () => import('../admin/AdminUsers.vue'), meta: { title: '用户管理' } },
    { path: '/admin/roles', component: () => import('../admin/AdminRoles.vue'), meta: { title: '角色权限' } },
    {
      path: '/aftersales',
      component: () => import('../views/AftersalesView.vue'),
      meta: { title: '我的售后', requiresAuth: true },
    },
    { path: '/work-orders', component: () => import('../views/WorkOrdersView.vue'), meta: { title: '我的工单', requiresAuth: true } },
    { path: '/admin/work-orders', component: () => import('../admin/AdminWorkOrders.vue'), meta: { title: '工单管理' } },
    {
      path: '/admin/aftersales',
      component: () => import('../admin/AdminAftersales.vue'),
      meta: { title: '售后管理' },
    },
    {
      path: '/admin/categories',
      component: () => import('../admin/AdminCategories.vue'),
      meta: { title: '分类管理' },
    },
    {
      path: '/admin/orders',
      component: () => import('../admin/AdminOrders.vue'),
      meta: { title: '订单管理' },
    },
    {
      path: '/admin/products',
      component: () => import('../admin/AdminProducts.vue'),
      meta: { title: '商品管理' },
    },
    {
      path: '/admin/inventory',
      component: () => import('../admin/AdminInventory.vue'),
      meta: { title: '库存管理' },
    },
    {
      path: '/coupons',
      component: () => import('../views/CouponsView.vue'),
      meta: { title: '优惠券', requiresAuth: true },
    },
    {
      path: '/admin/coupons',
      component: () => import('../admin/AdminCoupons.vue'),
      meta: { title: '优惠券管理' },
    },
    {
      path: '/addresses',
      component: () => import('../views/AddressesView.vue'),
      meta: { title: '我的地址', requiresAuth: true },
    },
    {
      path: '/favorites',
      component: () => import('../views/MallPersonalView.vue'),
      meta: { title: '我的收藏', requiresAuth: true },
    },
    {
      path: '/history',
      component: () => import('../views/MallPersonalView.vue'),
      meta: { title: '浏览足迹', requiresAuth: true },
    },
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
      path: '/products/:id',
      component: () => import('../views/ProductDetailView.vue'),
      meta: { title: '商品详情' },
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
      path: '/register',
      component: () => import('../views/RegisterView.vue'),
      meta: { title: '用户注册' },
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('../views/NotFoundView.vue'),
      meta: { title: '页面未找到' },
    },
  ],
})
router.beforeEach((to) => {
  if (loginDisabled && to.path === '/admin/login') return '/admin'
  if (loginDisabled && to.path === '/login') return '/account'
  if (to.path.startsWith('/admin') && to.path !== '/admin/login') {
    if (to.params.module && !Object.hasOwn(modules, String(to.params.module))) return '/admin'
    if (!loginDisabled && !adminDemoMode && !hasAdminAccess())
      return { path: '/admin/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAuth && !session.read())
    return { path: '/login', query: { redirect: to.fullPath } }
  document.title = `${modules[String(to.params.module)]?.title || String(to.meta.title || '智慧商城')} · 自贡能发`
})
