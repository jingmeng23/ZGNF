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
  <header class="topbar"><div><span class="eyebrow">WARM HOME, BETTER LIFE</span><h1>让安心，走进每一个家<span class="heading-dot">.</span></h1></div><span class="location"><MapPin :size="16" /> 服务覆盖范围以当地营业厅为准</span></header>
  <form class="searchbar" role="search" @submit.prevent="find"><Search :size="19" /><input v-model="search" aria-label="搜索商品" placeholder="搜索燃气灶、热水器、安全配件…" /><button type="submit">搜索</button></form>
  <MallFloors />
  <section class="hero"><div class="hero-copy"><span class="hero-label"><span></span> 品质燃气 · 智慧生活</span><h2>家的温度，<br />从一份安心开始。</h2><p>精选品质燃气好物，搭配专业到家服务。<br />让每一次开火，都多一份安心。</p><RouterLink to="/catalog" class="button">探索安心好物 <ArrowRight :size="17" /></RouterLink><div class="hero-foot"><span>QUALITY</span> 品质之选 / 温暖相伴</div></div><div class="hero-visual"><div class="orbit"></div><span class="visual-spark"><Sparkles :size="22" /></span><div class="hero-stove"><ApplianceArt /></div><div class="floating-label"><ShieldCheck :size="24" /><span><strong>守护家的每一刻</strong><small>安全 · 品质 · 专业</small></span></div><span class="visual-caption">DESIGNED FOR YOUR HOME</span></div></section>
  <div class="trust-strip"><span><BadgeCheck :size="18" /> 严选品质好物</span><span><ShieldCheck :size="18" /> 专业安全保障</span><span><Truck :size="18" /> 贴心配送到家</span><span><Leaf :size="18" /> 温暖绿色生活</span></div>
  <section><div class="section-heading"><div><span class="eyebrow">AT YOUR SERVICE</span><h2>专业服务，安心到家</h2></div><RouterLink to="/services">全部服务 <ArrowRight :size="16" /></RouterLink></div><div class="service-grid"><RouterLink v-for="service in services" :key="service.name" :to="{ path: '/services', query: { type: service.query } }" class="service-card"><span class="service-icon"><component :is="service.icon" :size="25" /></span><div><h3>{{ service.name }}</h3><p>{{ service.desc }}</p></div><ArrowRight :size="16" class="service-arrow" /></RouterLink></div></section>
  <section><div class="section-heading"><div><span class="eyebrow">CURATED FOR YOU</span><h2>为家精选的安心好物 <span class="soft-tag">品质推荐</span></h2></div><RouterLink to="/catalog">逛逛商城 <ArrowRight :size="16" /></RouterLink></div><ProductGrid /></section>
  <div class="warm-note"><span>把安全留给我们，把温暖留给生活。</span><small>{{ demoMode ? '当前展示演示商品；实际商品与服务以上线信息为准。' : '用心服务每一个温暖的家。' }}</small></div>
</template>
