<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Flame,
  House,
  Grid2X2,
  ShieldCheck,
  ShoppingBag,
  UserRound,
  ArrowUpRight,
} from 'lucide-vue-next'
import { useCartStore } from './stores/cart'
import { useAuthStore } from './stores/auth'
import AdminApp from './admin/AdminApp.vue'
import { loginDisabled } from './platform/testMode'
const cart = useCartStore()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const isAdmin = computed(() => route.path.startsWith('/admin'))
const links = [
  { to: '/', label: '首页', icon: House },
  { to: '/catalog', label: '好物商城', icon: Grid2X2 },
  { to: '/services', label: '安心服务', icon: ShieldCheck },
  { to: '/cart', label: '购物车', icon: ShoppingBag },
  { to: '/account', label: '我的', icon: UserRound },
]
function expired() {
  if (loginDisabled) return
  auth.logout()
  const current = router.currentRoute.value
  if (current.path.startsWith('/admin'))
    void router.replace({ path: '/admin/login', query: { redirect: current.fullPath } })
  else if (current.meta.requiresAuth)
    void router.replace({ path: '/login', query: { redirect: current.fullPath } })
}
onMounted(() => window.addEventListener('auth:expired', expired))
onMounted(() => { if (auth.authenticated) void cart.refresh() })
onUnmounted(() => window.removeEventListener('auth:expired', expired))
</script>

<template>
  <AdminApp v-if="isAdmin" />
  <div v-else class="app-shell">
    <aside class="sidebar">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark"><Flame :size="26" /></span
        ><span>自贡能发<small>SMART GAS · BETTER LIFE</small></span></RouterLink
      >
      <p class="nav-caption">美好生活，从安心开始</p>
      <nav aria-label="主导航">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="nav-link"
          :exact-active-class="'active'"
          ><component :is="link.icon" :size="21" /><span>{{ link.label }}</span
          ><b v-if="link.to === '/cart' && cart.count" class="badge">{{
            cart.count
          }}</b></RouterLink
        >
      </nav>
      <div class="sidebar-note">
        <ShieldCheck :size="28" /><strong>用气有保障，生活更安心</strong>
        <p>专业服务，守护每一个家。</p>
        <RouterLink to="/services">了解安心服务 <ArrowUpRight :size="15" /></RouterLink>
      </div>
      <p class="sidebar-bottom">自贡能发 · 智慧生活服务平台<small>用心服务每一个温暖的家</small></p>
    </aside>
    <main id="main-content" class="main"><RouterView /></main>
    <nav class="bottom-nav" aria-label="移动端导航">
      <RouterLink v-for="link in links" :key="link.to" :to="link.to" exact-active-class="active"
        ><span
          ><component :is="link.icon" :size="21" /><b
            v-if="link.to === '/cart' && cart.count"
            class="badge"
            >{{ cart.count }}</b
          ></span
        >{{ link.label }}</RouterLink
      >
    </nav>
  </div>
</template>
