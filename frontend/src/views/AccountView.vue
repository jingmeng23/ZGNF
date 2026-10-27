<script setup lang="ts">
import { UserRound, Package, ShieldCheck, ArrowRight } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore()
</script>
<template>
  <header class="page-heading">
    <span class="eyebrow">MY WARM HOME</span>
    <h1>个人中心</h1>
    <p>你的安心生活，在这里连接。</p>
  </header>
  <section class="account-banner">
    <span class="avatar"><UserRound :size="35" /></span>
    <div>
      <h2>{{ auth.authenticated ? '欢迎回家' : '欢迎来到自贡能发' }}</h2>
      <p>{{ auth.authenticated ? '已登录，可以查看个人服务。' : '登录后，开启你的安心服务。' }}</p>
    </div>
    <RouterLink v-if="!auth.authenticated" to="/login" class="button">登录 / 注册</RouterLink
    ><button v-else class="button secondary" @click="auth.logout">退出登录</button>
  </section>
  <div class="panel account-links">
    <RouterLink to="/addresses"><span>收货地址<small>管理地址与地图选点</small></span><ArrowRight :size="18" /></RouterLink>
    <RouterLink to="/favorites"
      ><span>我的收藏<small>收藏的安心好物</small></span></RouterLink
    ><RouterLink to="/history"
      ><span>浏览足迹<small>最近看过的商品</small></span></RouterLink
    ><RouterLink to="/orders"
      ><Package :size="22" /><span>我的订单<small>查看订单与服务进度</small></span
      ><ArrowRight :size="18" /></RouterLink
    ><RouterLink to="/services"
      ><ShieldCheck :size="22" /><span>服务中心<small>用气服务与安全知识</small></span
      ><ArrowRight :size="18"
    /></RouterLink>
  </div>
</template>
