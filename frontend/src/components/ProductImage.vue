<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ApplianceArt from './ApplianceArt.vue'
const props = defineProps<{ src?: string | null; alt: string; kind: string }>()
const failed = ref(false)
const safe = computed(() => !!props.src && /^(https?:\/\/|\/(?!\/))/i.test(props.src!))
watch(() => props.src, () => { failed.value = false })
</script>
<template><img v-if="safe && !failed" :src="src!" :alt="alt" loading="lazy" class="catalog-image" @error="failed = true" /><ApplianceArt v-else :kind="kind" /></template>
