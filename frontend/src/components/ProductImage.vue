<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ApplianceArt from './ApplianceArt.vue'
import { productImageUrl } from '../api/images'
import { ImageOff } from 'lucide-vue-next'
const props = defineProps<{
  src?: string | null
  alt: string
  kind: string
  placeholder?: boolean
  priority?: boolean
}>()
const failed = ref(false)
const source = computed(() => productImageUrl(props.src))
const safe = computed(() => !!props.src && /^(https?:\/\/|\/(?!\/))/i.test(props.src!))
watch(
  () => props.src,
  () => {
    failed.value = false
  },
)
</script>
<template>
  <img
    v-if="safe && !failed"
    :src="source"
    :alt="alt"
    :loading="priority ? 'eager' : 'lazy'"
    :fetchpriority="priority ? 'high' : 'auto'"
    decoding="async"
    class="catalog-image"
    @error="failed = true"
  /><span
    v-else-if="placeholder"
    class="image-placeholder"
    role="img"
    :aria-label="`${alt}，暂无可用图片`"
    ><ImageOff :size="32" aria-hidden="true" /><span>{{
      failed ? '图片暂时无法加载' : '暂无商品图片'
    }}</span></span
  ><ApplianceArt v-else :kind="kind" />
</template>

<style scoped>
.image-placeholder {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 100px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  color: #777;
  background: #f5f5f5;
  font-size: 12px;
}
</style>
