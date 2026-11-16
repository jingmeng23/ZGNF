<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Flame, Search, ShoppingBag } from 'lucide-vue-next'
import { useCartStore } from '../stores/cart'
import { demoMode } from '../api/catalog'
defineProps<{ compact?: boolean }>()
const router = useRouter(),
  cart = useCartStore(),
  keyword = ref('')
</script>

<template>
  <header class="shop-header">
    <RouterLink to="/catalog" class="shop-brand"
      ><Flame :size="26" aria-hidden="true" /><span
        >自贡能发<small>好物商城</small></span
      ></RouterLink
    >
    <nav class="shop-nav" aria-label="商城导航">
      <RouterLink to="/">首页</RouterLink><RouterLink to="/catalog">商城</RouterLink
      ><RouterLink to="/account">我的</RouterLink>
    </nav>
    <form
      v-if="!compact"
      class="shop-header-search"
      role="search"
      @submit.prevent="
        router.push({ path: '/catalog', query: keyword.trim() ? { q: keyword.trim() } : {} })
      "
    >
      <input
        v-model="keyword"
        aria-label="搜索商品"
        placeholder="搜索商品名称、型号"
        maxlength="100"
      />
      <button aria-label="搜索商品" type="submit"><Search :size="19" aria-hidden="true" /></button>
    </form>
    <RouterLink
      to="/cart"
      class="shop-cart"
      :aria-label="cart.loadError ? '购物车，数量暂时无法加载' : `购物车，${cart.count} 件商品`"
      :title="cart.loadError || undefined"
      ><ShoppingBag :size="20" aria-hidden="true" /><span>购物车</span
      ><b>{{ cart.loadError ? '—' : cart.count }}</b></RouterLink
    >
  </header>
  <p v-if="demoMode" class="shop-demo" role="status">
    演示模式 · 商品为示例数据，加购仅在本次预览中有效，不会创建真实交易。
  </p>
</template>

<style scoped>
.shop-header {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 0 0 22px;
  border-bottom: 1px solid #e5e5e5;
  margin-bottom: 24px;
}
.shop-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #a53e2b;
  font-size: 20px;
  font-weight: 700;
  white-space: nowrap;
}
.shop-brand small {
  display: block;
  margin-top: 2px;
  color: #686868;
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 2px;
}
.shop-nav {
  display: flex;
  gap: 22px;
  color: #555;
  font-size: 13px;
}
.shop-nav a.router-link-exact-active {
  color: #a53e2b;
  font-weight: 600;
}
.shop-header-search {
  display: flex;
  margin-left: auto;
  width: min(420px, 50%);
  height: 42px;
  border: 1px solid #ddd;
  border-radius: 6px;
  background: #fff;
}
.shop-header-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  padding: 10px 12px;
  background: transparent;
}
.shop-header-search button {
  display: grid;
  place-items: center;
  border: 0;
  width: 42px;
  color: #a53e2b;
  background: transparent;
}
.shop-cart {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  font-size: 13px;
  white-space: nowrap;
}
.shop-cart b {
  border-radius: 20px;
  background: #f5e6e1;
  color: #993b29;
  padding: 3px 8px;
  font-size: 12px;
}
.shop-demo {
  color: #8c422c;
  background: #fff4e8;
  padding: 12px;
  font-size: 13px;
  border-radius: 6px;
}
@media (max-width: 760px) {
  .shop-nav {
    display: none;
  }
}
@media (max-width: 620px) {
  .shop-header {
    gap: 14px;
    flex-wrap: wrap;
    padding-bottom: 16px;
    margin-bottom: 18px;
  }
  .shop-brand {
    font-size: 18px;
  }
  .shop-header-search {
    order: 3;
    width: 100%;
  }
}
</style>
