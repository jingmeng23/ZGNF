<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { mallApi } from '../api/mall'
import type { Floor } from '../api/mall'
import { demoMode } from '../api/catalog'
import ProductImage from './ProductImage.vue'
const floors = ref<Floor[]>([]),
  error = ref('')
async function load() {
  if (demoMode) return
  error.value = ''
  try {
    floors.value = await mallApi.floors()
  } catch (e) {
    error.value = (e as Error).message
  }
}
onMounted(load)
</script>
<template>
  <p v-if="error" role="alert">首页活动加载失败：{{ error }} <button @click="load">重试</button></p>
  <section v-for="floor in floors" :key="floor.id" class="mall-floor" :aria-label="floor.title">
    <h2>{{ floor.title }}</h2>
    <div class="floor-items">
      <article v-for="(item, index) in floor.items" :key="index">
        <RouterLink
          v-if="item.productId || item.path"
          :to="item.productId ? `/products/${item.productId}` : item.path!"
        >
          <ProductImage
            v-if="item.imageUrl"
            :src="item.imageUrl"
            :alt="item.title"
            :kind="item.productId || floor.id"
          />
          <h3>{{ item.title }}</h3> </RouterLink
        ><template v-else
          ><ProductImage
            v-if="item.imageUrl"
            :src="item.imageUrl"
            :alt="item.title"
            :kind="floor.id"
          />
          <h3>{{ item.title }}</h3></template
        >
      </article>
    </div>
  </section>
</template>
<style scoped>
.mall-floor {
  margin: 2rem 0;
}
.floor-items {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}
article {
  border: 1px solid #eee;
  border-radius: 12px;
  overflow: hidden;
  padding: 1rem;
}
article :deep(img) {
  width: 100%;
  max-height: 240px;
  object-fit: contain;
}
</style>
