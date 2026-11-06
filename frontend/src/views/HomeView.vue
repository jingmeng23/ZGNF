<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { MapPin, Search, ArrowRight, ShieldCheck, Wrench, ClipboardCheck, Headset, Truck, BadgeCheck, Leaf, Sparkles } from 'lucide-vue-next'
import ProductGrid from '../components/ProductGrid.vue'
import ApplianceArt from '../components/ApplianceArt.vue'
import { demoMode } from '../api/catalog'
import MallFloors from '../components/MallFloors.vue'
const router = useRouter()
const search = ref('')
function find() { void router.push({ path: '/catalog', query: { q: search.value } }) }
const services = [ { name: '燃气安装', desc: '专业安装，一步到位', icon: Wrench, query: '安装' }, { name: '安全检查', desc: '细致检查，安心用气', icon: ShieldCheck, query: '安检' }, { name: '维修服务', desc: '及时响应，省心解决', icon: ClipboardCheck, query: '维修' }, { name: '服务咨询', desc: '贴心解答，全程陪伴', icon: Headset, query: '咨询' } ]
</script>

<template>
  <header class="topbar home-topbar"><div><span class="eyebrow">WARM HOME, BETTER LIFE</span><h1>让安心，走进每一个家<span class="heading-dot">.</span></h1></div><span class="location"><MapPin :size="16" /> 服务覆盖范围以当地营业厅为准</span></header>
  <form class="searchbar" role="search" @submit.prevent="find"><Search :size="19" /><input v-model="search" aria-label="搜索商品" placeholder="搜索燃气灶、热水器、安全配件…" /><button type="submit">搜索</button></form>
  <MallFloors />
  <section class="hero"><div class="hero-copy"><span class="hero-label"><span></span> 品质燃气 · 智慧生活</span><h2>家的温度，<br /><em>从一份安心开始。</em></h2><p>精选品质燃气好物，搭配专业到家服务。<br />让每一次开火，都多一份安心。</p><RouterLink to="/catalog" class="button hero-button">探索安心好物 <ArrowRight :size="17" /></RouterLink><div class="hero-trust"><span>国家认证</span><span>百万家庭选择</span><span>免费上门安装</span></div><div class="hero-foot"><span>QUALITY</span> 品质之选 / 温暖相伴</div></div><div class="hero-visual"><div class="visual-grid"></div><div class="orbit"></div><div class="scene-photo" role="img" aria-label="温馨厨房烹饪场景"></div><div class="scene-arc"></div><div class="floating-label"><ShieldCheck :size="21" /><span><strong>守护家的每一刻</strong><small>安全 · 品质 · 专业</small></span></div><span class="visual-caption">WARM KITCHEN, SAFE HOME</span></div></section>
  <div class="trust-strip"><span><BadgeCheck :size="18" /> 严选品质好物</span><span><ShieldCheck :size="18" /> 专业安全保障</span><span><Truck :size="18" /> 贴心配送到家</span><span><Leaf :size="18" /> 温暖绿色生活</span></div>
  <section><div class="section-heading"><div><span class="eyebrow">AT YOUR SERVICE</span><h2>专业服务，安心到家</h2></div><RouterLink to="/services">全部服务 <ArrowRight :size="16" /></RouterLink></div><div class="service-grid"><RouterLink v-for="service in services" :key="service.name" :to="{ path: '/services', query: { type: service.query } }" class="service-card"><span class="service-icon"><component :is="service.icon" :size="25" /></span><div><h3>{{ service.name }}</h3><p>{{ service.desc }}</p></div><ArrowRight :size="16" class="service-arrow" /></RouterLink></div></section>
  <section class="recommendation-section"><div class="section-heading"><div><span class="eyebrow">CURATED FOR YOU</span><h2>为家精选的安心好物 <span class="soft-tag">品质推荐</span></h2></div><RouterLink to="/catalog">逛逛商城 <ArrowRight :size="16" /></RouterLink></div><div class="recommendation-scroller"><ProductGrid /></div></section>
  <div class="warm-note"><span>把安全留给我们，把温暖留给生活。</span><small>{{ demoMode ? '当前展示演示商品；实际商品与服务以上线信息为准。' : '用心服务每一个温暖的家。' }}</small></div>
</template>

<style scoped>
.home-topbar { margin-bottom: 18px; }
.home-topbar .eyebrow { color: #23839b; font-weight: 700; letter-spacing: .12em; }
.home-topbar h1 { color: #173e35; font-weight: 800; }
.heading-dot { color: #e5a33e; }
.home-topbar .location { color: #70847a; }
.home-topbar .location svg { color: #23839b; }
.hero { border: 1px solid #d7e8df; background: #e5f1e8; box-shadow: 0 12px 30px #173e3512; }
.hero-copy { position: relative; overflow: hidden; padding: 48px; }
.hero-copy::before { position: absolute; inset: 0; opacity: .32; background-image: linear-gradient(#9bc7bc 1px, transparent 1px), linear-gradient(90deg, #9bc7bc 1px, transparent 1px); background-size: 34px 34px; mask-image: linear-gradient(90deg, #000, transparent 78%); content: ''; }
.hero-copy > * { position: relative; }
.hero-copy h2 { margin: 20px 0 0; color: #173e35; font-size: clamp(36px, 4vw, 54px); font-weight: 800; line-height: 1.15; }
.hero-copy h2 em { color: #167d98; font-style: normal; }
.hero-copy > p:not(.hero-subtitle) { color: #5f766b; }
.hero-button { display: inline-flex; align-items: center; gap: 8px; background: #167d98; box-shadow: 0 7px 16px #167d9838; }
.hero-button:hover, .hero-button:focus-visible { background: #e39b35; }
.hero-visual { position: relative; min-height: 335px; aspect-ratio: 1 / 1; background: #cfe5d7; }
.visual-grid { position: absolute; inset: 0; opacity: .22; background: radial-gradient(circle at 25% 20%, #fff 0 1px, transparent 2px); background-size: 20px 20px; }
.hero-visual::before { position: absolute; top: 18%; left: 12%; width: 76%; height: 76%; border: 1px solid #fff9; border-radius: 50%; content: ''; }
.scene-photo { position: absolute; z-index: 1; top: 50%; left: 50%; width: calc(100% - 12px); aspect-ratio: 1; overflow: hidden; border-radius: 50%; background: #d6c5ad url('https://images.unsplash.com/photo-1556911220-bff31c812dba?auto=format&fit=crop&w=1100&q=85') center/cover; box-shadow: 0 20px 32px #173e3530; transform: translate(-50%, -50%); }
.scene-photo::after { position: absolute; inset: 0; background: linear-gradient(135deg, #fff8 0%, transparent 45%, #173e3533 100%); content: ''; }
.floating-label { position: absolute; z-index: 2; top: 13%; right: 8%; display: flex; align-items: flex-start; gap: 8px; padding: 0; border: 0; color: #fff; background: transparent; box-shadow: none; text-shadow: 0 2px 10px #163a30a8; }
.floating-label > svg { margin-top: 2px; color: #f1bb61; filter: drop-shadow(0 1px 3px #173e3566); }
.floating-label strong { display: block; color: #fff; font-family: 'Noto Serif SC', 'Songti SC', serif; font-size: 16px; font-weight: 600; letter-spacing: .12em; line-height: 1.5; }
.floating-label small { display: block; margin-top: 5px; color: #fff; font-family: 'Noto Serif SC', 'Songti SC', serif; font-size: 11px; letter-spacing: .2em; line-height: 1.5; }
.visual-caption { position: absolute; z-index: 3; top: 50%; left: 50%; width: 82%; color: #fff; font-family: 'Bodoni 72', 'Didot', 'Times New Roman', serif; font-size: clamp(18px, 2.2vw, 29px); font-style: italic; font-weight: 700; letter-spacing: .18em; line-height: 1.25; text-align: center; text-shadow: 0 3px 14px #173e35c7, 0 0 2px #173e35; transform: translate(-50%, -50%) rotate(-8deg); }
.hero-trust { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 20px; }
.hero-trust span { padding: 5px 9px; border: 1px solid #b7d5c7; border-radius: 999px; color: #3c7061; background: #ffffffa8; font-size: 11px; }
.trust-strip { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin: 16px 0 32px; }
.trust-strip span { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 40px; border: 1px solid #d8e8df; border-radius: 999px; color: #446b5d; background: #eff7f0; font-size: 12px; }
.trust-strip span svg { color: #e29b38; }
.service-grid { gap: 15px; }
.service-card { position: relative; border: 0; box-shadow: 0 6px 18px #173e3514; transition: transform .2s ease, box-shadow .2s ease; }
.service-card:hover { transform: translateY(-5px); box-shadow: 0 15px 28px #173e3522; }
.service-icon { display: grid; width: 44px; height: 44px; place-items: center; border-radius: 12px; color: #167d98; background: #e2f2f2; }
.service-card:nth-child(even) .service-icon { color: #d58a28; background: #fff0d8; }
.service-arrow { display: grid; width: 28px; height: 28px; padding: 6px; place-items: center; border-radius: 50%; color: #d58a28; background: #fff4e1; transition: transform .2s ease, color .2s ease, background .2s ease; }
.service-card:hover .service-arrow { transform: translateX(4px); color: #fff; background: #e29b38; }
.recommendation-section { margin-top: 34px; }
.recommendation-scroller { overflow-x: auto; padding: 4px 2px 15px; scrollbar-color: #b5cfc3 transparent; }
.recommendation-scroller :deep(.product-grid) { display: flex; gap: 16px; width: max-content; }
.recommendation-scroller :deep(.product-card) { width: 245px; }
.recommendation-scroller :deep(.product-image) { height: 190px; }
@media (max-width: 620px) {
  .hero-copy { padding: 32px 24px; }
  .hero-copy h2 { font-size: 36px; }
  .hero-copy .hero-subtitle { font-size: 17px; }
  .floating-label { top: 14%; right: 9%; }
  .floating-label strong { font-size: 13px; }
  .floating-label small { font-size: 9px; }
  .trust-strip { grid-template-columns: repeat(2, 1fr); gap: 8px; margin-bottom: 25px; }
  .trust-strip span { font-size: 10px; }
  .recommendation-scroller :deep(.product-card) { width: 210px; }
}
</style>
