<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowRight,
  BadgeCheck,
  Bookmark,
  Clock3,
  Footprints,
  MapPin,
  TicketPercent,
  PackageCheck,
  RotateCcw,
  Ruler,
  Sparkles,
  Truck,
  UserRound,
} from 'lucide-vue-next'
import { authApi } from '../api/auth'
import { userOrdersApi, type OrderRecord } from '../api/orders'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const profile = ref<{ userId?: string; nickname?: string | null; avatar?: string | null } | null>(auth.user)
const orders = ref<OrderRecord[]>([])
const orderError = ref('')
const name = computed(() => profile.value?.nickname?.trim() || `jd_${profile.value?.userId || 'mufuo5j9970jdu'}`)
const pendingPayment = computed(() => orders.value.filter(order => order.status === 'pending_payment').length)
const pendingDelivery = computed(() => orders.value.filter(order => ['paid', 'processing'].includes(order.status)).length)
const pendingReview = computed(() => orders.value.filter(order => order.status === 'completed').length)

async function load() {
  if (!auth.authenticated) return
  orderError.value = ''
  try {
    const [user, result] = await Promise.all([
      authApi.me(),
      userOrdersApi.list({ page: 1, size: 100, orderNo: '', status: '' }),
    ])
    profile.value = user
    auth.user = user
    orders.value = result.list
  } catch (error) {
    orderError.value = (error as Error).message
  }
}

onMounted(load)
watch(() => auth.authenticated, load)

const orderLinks = [
  { label: '待付款', icon: Clock3, status: 'pending_payment', count: pendingPayment },
  { label: '待收货', icon: Truck, status: 'processing', count: pendingDelivery },
  { label: '待评价', icon: BadgeCheck, status: 'completed', count: pendingReview },
  { label: '退款/售后', icon: RotateCcw, status: '', count: null },
]
</script>

<template>
  <main class="my-page">
    <section class="profile-panel" aria-label="用户信息">
      <div class="profile-main">
        <div class="avatar" aria-hidden="true">
          <img v-if="profile?.avatar" :src="profile.avatar" alt="" />
          <UserRound v-else :size="34" :stroke-width="1.5" />
        </div>
        <div class="profile-copy">
          <h1>{{ auth.authenticated ? name : '欢迎来到自贡能发' }}</h1>
          <div class="profile-links">
            <RouterLink to="/account" class="member-link"><Sparkles :size="15" />铜牌会员</RouterLink>
            <RouterLink to="/account"><Ruler :size="15" />我的尺码</RouterLink>
          </div>
          <RouterLink to="/account" class="plus-banner">
            <span class="plus-label">PLUS 会员</span>
            <span>权益重磅升级</span>
            <ArrowRight :size="16" />
          </RouterLink>
        </div>
        <RouterLink v-if="!auth.authenticated" to="/login" class="login-link">登录 / 注册</RouterLink>
        <button v-else class="logout-link" type="button" @click="auth.logout">退出</button>
      </div>
    </section>

    <section class="orders-panel" aria-labelledby="orders-heading">
      <RouterLink id="orders-heading" to="/orders" class="section-title">
        <h2>我的订单</h2><ArrowRight :size="18" />
      </RouterLink>
      <div class="order-shortcuts">
        <RouterLink
          v-for="item in orderLinks"
          :key="item.label"
          :to="item.status ? { path: '/orders', query: { status: item.status } } : '/aftersales'"
          class="order-shortcut"
        >
          <span class="order-icon">
            <component :is="item.icon" :size="25" :stroke-width="1.65" />
            <b v-if="item.count?.value" class="count-badge">{{ item.count.value }}</b>
          </span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </div>
      <p v-if="orderError" class="order-error" role="status">暂时无法读取订单信息</p>
      <div class="logistics-empty">
        <PackageCheck :size="25" :stroke-width="1.5" />
        <p>当前暂无物流信息</p>
        <RouterLink to="/orders">去查看全部订单 <ArrowRight :size="14" /></RouterLink>
      </div>
    </section>

    <nav class="account-menu" aria-label="个人服务">
      <RouterLink to="/addresses" class="menu-link">
        <MapPin :size="20" />
        <span>我的地址</span>
        <ArrowRight :size="16" />
      </RouterLink>
      <RouterLink to="/coupons" class="menu-link">
        <TicketPercent :size="20" />
        <span>优惠券</span>
        <ArrowRight :size="16" />
      </RouterLink>
      <RouterLink to="/favorites" class="menu-link">
        <Bookmark :size="20" />
        <span>我的收藏</span>
        <ArrowRight :size="16" />
      </RouterLink>
      <RouterLink to="/history" class="menu-link">
        <Footprints :size="20" />
        <span>我的足迹</span>
        <ArrowRight :size="16" />
      </RouterLink>
    </nav>
  </main>
</template>

<style scoped>
.my-page {
  width: min(100%, 920px);
  min-height: calc(100vh - 68px);
  margin: -34px auto;
  padding: 56px 28px 72px;
  color: #252525;
}

.profile-panel,
.orders-panel {
  background: #fff;
}

.profile-panel {
  padding: 30px 34px;
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 22px;
}

.avatar {
  display: grid;
  flex: 0 0 76px;
  width: 76px;
  aspect-ratio: 1;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  color: #939393;
  background: #f0f0f0;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-copy {
  flex: 1;
  min-width: 0;
}

.profile-copy h1 {
  overflow-wrap: anywhere;
  margin: 0 0 10px;
  font-size: 21px;
  font-weight: 700;
  line-height: 1.35;
}

.profile-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-bottom: 15px;
}

.profile-links a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #656565;
  font-size: 13px;
}

.profile-links .member-link svg {
  color: #e59a4a;
}

.plus-banner {
  display: flex;
  width: min(100%, 365px);
  min-height: 39px;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  color: #e9d6ae;
  background: #292929;
  font-size: 12px;
}

.plus-label {
  color: #f4d99f;
  font-size: 13px;
  font-weight: 700;
}

.plus-banner svg {
  margin-left: auto;
}

.login-link,
.logout-link {
  flex: 0 0 auto;
  padding: 8px 12px;
  border: 1px solid #dedede;
  color: #4b4b4b;
  background: #fff;
  font-size: 13px;
}

.orders-panel {
  margin-top: 18px;
  padding: 25px 34px 0;
}

.section-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.section-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.order-shortcuts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 29px 0 27px;
}

.order-shortcut {
  display: grid;
  justify-items: center;
  gap: 10px;
  color: #4b4b4b;
  font-size: 13px;
}

.order-icon {
  position: relative;
  display: grid;
  width: 48px;
  aspect-ratio: 1;
  place-items: center;
  color: #454545;
}

.count-badge {
  position: absolute;
  top: -3px;
  right: -4px;
  display: grid;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  place-items: center;
  border-radius: 10px;
  color: #fff;
  background: #e4393c;
  font-size: 10px;
  font-weight: 500;
}

.logistics-empty {
  display: grid;
  min-height: 157px;
  justify-items: center;
  align-content: center;
  border-top: 1px solid #f0f0f0;
  color: #a1a1a1;
}

.logistics-empty p {
  margin: 8px 0 10px;
  color: #777;
  font-size: 13px;
}

.logistics-empty a {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #555;
  font-size: 12px;
}

.order-error {
  margin: 0 0 12px;
  color: #a44;
  text-align: center;
  font-size: 12px;
}

.account-menu {
  margin-top: 10px;
  padding: 0 24px;
  background: #fff;
}

.menu-link {
  display: flex;
  min-height: 55px;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #f0f0f0;
  color: #444;
  font-size: 14px;
}

.menu-link:last-child {
  border-bottom: 0;
}

.menu-link > svg:first-child {
  color: #777;
}

.menu-link > svg:last-child {
  margin-left: auto;
  color: #aaa;
}

@media (max-width: 620px) {
  .my-page {
    min-height: calc(100vh - 64px);
    margin: -20px -16px;
    padding: 28px 0 34px;
    background: #f5f5f5;
  }

  .profile-panel {
    padding: 23px 18px;
  }

  .profile-main {
    align-items: flex-start;
    gap: 13px;
  }

  .avatar {
    flex-basis: 58px;
    width: 58px;
  }

  .profile-copy h1 {
    font-size: 17px;
  }

  .profile-links {
    gap: 8px 12px;
  }

  .profile-links a {
    font-size: 12px;
  }

  .login-link,
  .logout-link {
    margin-left: auto;
    padding: 6px 8px;
    font-size: 11px;
  }

  .orders-panel {
    margin-top: 10px;
    padding: 21px 18px 0;
  }

  .account-menu {
    padding: 0 18px;
  }

  .order-shortcuts {
    padding: 24px 0;
  }

  .order-shortcut {
    gap: 7px;
    font-size: 12px;
  }

  .order-icon {
    width: 42px;
  }

  .logistics-empty {
    min-height: 144px;
  }
}
</style>
