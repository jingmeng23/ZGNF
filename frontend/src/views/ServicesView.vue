<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Wrench, ShieldCheck, Headset, ArrowRight } from 'lucide-vue-next'
const route = useRoute()
const router = useRouter()
const selected = ref(String(route.query.type || ''))
const services = [{ type: '安装', title: '燃气安装', desc: '燃气设备安装与连接，交给专业人员。', icon: Wrench }, { type: '安检', title: '安全检查', desc: '关注管道与设备状态，守护日常用气安全。', icon: ShieldCheck }, { type: '维修', title: '维修服务', desc: '设备异常与使用问题，由专业人员协助处理。', icon: Wrench }, { type: '咨询', title: '服务咨询', desc: '了解服务内容、办理流程与用气知识。', icon: Headset }]
</script>
<template><header class="page-heading"><span class="eyebrow">CARE FOR EVERY HOME</span><h1>安心服务</h1><p>专业相伴，让生活多一份从容。</p></header><div class="service-grid service-page"><button v-for="item in services" :key="item.type" class="service-card" :class="{ chosen: selected === item.type }" @click="selected = item.type"><span class="service-icon"><component :is="item.icon" :size="26" /></span><div><h3>{{ item.title }}</h3><p>{{ item.desc }}</p></div><ArrowRight :size="18" /></button></div><section v-if="selected && selected !== '咨询'" class="panel"><h2>{{ selected }}服务</h2><button class="button" @click="router.push({path:'/work-orders',query:{type:selected==='安装'?'installation':selected==='维修'?'repair':'inspection'}})">立即预约</button></section><section class="safety-note"><ShieldCheck :size="28" /><div><h3>日常用气小贴士</h3><p>保持通风，定期检查。发现疑似燃气泄漏时，请勿开关电器或使用明火，离开现场后联系当地燃气应急服务。</p></div></section></template>
