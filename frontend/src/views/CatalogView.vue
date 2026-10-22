<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from 'lucide-vue-next'
import ProductGrid from '../components/ProductGrid.vue'
const route = useRoute()
const search = ref(String(route.query.q || ''))
const category = ref('全部')
const categories = ['全部', '燃气灶', '热水器', '安全配件']
const query = computed(() => search.value.trim())
</script>
<template><header class="page-heading"><span class="eyebrow">GOOD THINGS FOR HOME</span><h1>好物商城</h1><p>为家的每一份温暖，选一份可靠的品质。</p></header><div class="searchbar"><Search :size="20" /><input v-model="search" aria-label="搜索商城商品" placeholder="搜索你需要的安心好物" /></div><div class="tabs" aria-label="商品分类"><button v-for="item in categories" :key="item" :class="{ selected: category === item }" :aria-pressed="category === item" @click="category = item">{{ item }}</button></div><ProductGrid :search="query" :category="category" /></template>
