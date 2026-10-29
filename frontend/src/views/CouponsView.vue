<script setup lang="ts">
import { ref, watch } from 'vue'
import { couponsApi, type Coupon, type UserCoupon } from '../api/coupons'
const tab = ref('available'), status = ref(''), keyword = ref(''), page = ref(1), pages = ref(0)
const available = ref<Coupon[]>([]), mine = ref<UserCoupon[]>([]), loading = ref(false), busy = ref(false), message = ref('')
const labels: Record<string, string> = { unused: '未使用', locked: '订单占用中', used: '已使用', expired: '已过期' }
let sequence = 0
async function load() {
  const version = ++sequence; loading.value = true; message.value = ''
  try {
    if (tab.value === 'available') { const r = await couponsApi.available(page.value, keyword.value); if (version !== sequence) return; available.value = r.list; pages.value = r.pages; if (page.value > Math.max(1, r.pages)) page.value = Math.max(1, r.pages) }
    else { const r = await couponsApi.mine(page.value, status.value); if (version !== sequence) return; mine.value = r.list; pages.value = r.pages }
  } catch (e) { if (version === sequence) message.value = (e as Error).message } finally { if (version === sequence) loading.value = false }
}
async function claim(coupon: Coupon) {
  if (busy.value) return; busy.value = true
  try { await couponsApi.claim(coupon.id); await load(); message.value = '领取成功，可在“我的优惠券”中查看。重复领取不会重复发券。' }
  catch (e) { message.value = (e as Error).message } finally { busy.value = false }
}
watch([tab, status], () => { page.value = 1 })
watch([tab, status, page], load)
void load()
</script>
<template>
  <header class="page-heading"><h1>优惠券</h1><p>每人每券限领一次，优惠金额和使用条件以服务端结算为准。</p></header>
  <div class="coupon-actions"><button :aria-pressed="tab === 'available'" @click="tab = 'available'">领券中心</button><button :aria-pressed="tab === 'mine'" @click="tab = 'mine'">我的优惠券</button>
    <form v-if="tab === 'available'" @submit.prevent="page = 1; load()"><input v-model="keyword" placeholder="搜索优惠券" maxlength="100" /><button>搜索</button></form>
    <select v-else v-model="status" aria-label="优惠券状态"><option value="">全部状态</option><option v-for="(name, value) in labels" :value="value">{{ name }}</option></select>
  </div>
  <p v-if="message" role="status">{{ message }}</p><p v-if="loading">正在加载…</p>
  <div v-else class="coupon-grid">
    <template v-if="tab === 'available'"><article v-for="c in available" :key="c.id" class="panel"><h2>{{ c.name }}</h2><strong>¥{{ Number(c.amount).toFixed(2) }}</strong><p>满 ¥{{ c.minimumAmount }} 可用，订单商品金额须大于券面额</p><p>{{ c.startTime.replace('T', ' ') }} 至 {{ c.endTime.replace('T', ' ') }}</p><p>剩余 {{ c.remainingQuantity }} 张</p><button :disabled="busy || c.remainingQuantity <= 0" @click="claim(c)">领取</button></article><p v-if="!available.length">暂无可领取优惠券</p></template>
    <template v-else><article v-for="c in mine" :key="c.id" class="panel"><h2>{{ c.name }}</h2><strong>¥{{ Number(c.amount).toFixed(2) }}</strong><p>满 ¥{{ c.minimumAmount }} 可用</p><p>{{ c.startTime.replace('T', ' ') }} 至 {{ c.expireTime.replace('T', ' ') }}</p><p>{{ labels[c.status] || c.status }}</p><RouterLink v-if="c.status === 'unused'" to="/catalog">去选购</RouterLink></article><p v-if="!mine.length">暂无优惠券</p></template>
  </div>
  <div class="coupon-actions"><button :disabled="loading || page <= 1" @click="page--">上一页</button><span>{{ page }} / {{ Math.max(1, pages) }}</span><button :disabled="loading || page >= pages" @click="page++">下一页</button></div>
</template>
<style scoped>.coupon-actions{display:flex;gap:12px;flex-wrap:wrap;margin:20px 0}.coupon-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:20px}.coupon-grid strong{font-size:28px;color:#b44d26}.coupon-grid p{overflow-wrap:anywhere}button,input,select{padding:10px}button[aria-pressed=true]{background:#243c32;color:white}</style>
