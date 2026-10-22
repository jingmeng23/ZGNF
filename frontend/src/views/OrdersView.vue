<script setup lang="ts">
import { Package } from 'lucide-vue-next'
</script>
<template><header class="page-heading"><span class="eyebrow">MY ORDERS</span><h1>我的订单</h1></header><div class="empty-state"><Package :size="48" :stroke-width="1.2" /><h2>订单服务即将开放</h2><p>订单查询将在后端业务接口实现后接入。</p><RouterLink to="/catalog" class="button">继续逛逛</RouterLink></div></template>
