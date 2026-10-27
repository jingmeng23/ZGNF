<script setup lang="ts">
import { computed, ref } from 'vue'
import { project, unproject } from '../platform/addressMap'
const props = defineProps<{ latitude?: number | null; longitude?: number | null }>()
const emit = defineEmits<{ select: [point: { latitude: number; longitude: number }] }>()
const latitude = ref(props.latitude ?? 35), longitude = ref(props.longitude ?? 105), zoom = ref(props.latitude != null ? 15 : 4)
const error = ref(''), locating = ref(false), tileError = ref(false)
const center = computed(() => project(latitude.value, longitude.value, zoom.value))
const tiles = computed(() => {
  const list = [], count = 2 ** zoom.value
  for (let row = -1; row <= 1; row++) for (let col = -1; col <= 1; col++) {
    const x = Math.floor(center.value.x / 256) + col, y = Math.floor(center.value.y / 256) + row
    if (y >= 0 && y < count) list.push({ key: `${zoom.value}-${x}-${y}`, url: `https://tile.openstreetmap.org/${zoom.value}/${((x % count) + count) % count}/${y}.png`, left: x * 256 - center.value.x + 256, top: y * 256 - center.value.y + 160 })
  }
  return list
})
function pick(event: MouseEvent) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const point = unproject(center.value.x + event.clientX - rect.left - rect.width / 2, center.value.y + event.clientY - rect.top - 160, zoom.value)
  latitude.value = point.latitude; longitude.value = point.longitude
}
function pan(x: number, y: number) { const point = unproject(center.value.x + x, center.value.y + y, zoom.value); latitude.value = point.latitude; longitude.value = point.longitude }
function locate() {
  error.value = ''
  if (!navigator.geolocation) { error.value = '当前环境不支持定位，请手动选点'; return }
  locating.value = true
  navigator.geolocation.getCurrentPosition(position => { latitude.value = position.coords.latitude; longitude.value = position.coords.longitude; zoom.value = 16; locating.value = false }, () => { error.value = '未取得定位授权或定位失败，请手动选点'; locating.value = false }, { timeout: 10000, maximumAge: 0, enableHighAccuracy: false })
}
</script>
<template>
  <section class="address-map">
    <p>点击地图选点。定位仅在点击下方按钮并获得浏览器授权后进行。</p>
    <div class="map-controls"><button type="button" @click="locate" :disabled="locating">{{ locating ? '定位中…' : '授权获取当前位置' }}</button><button type="button" @click="zoom = Math.min(18, zoom + 1)">放大</button><button type="button" @click="zoom = Math.max(2, zoom - 1)">缩小</button><button type="button" @click="pan(-180, 0)">西</button><button type="button" @click="pan(180, 0)">东</button><button type="button" @click="pan(0, -120)">北</button><button type="button" @click="pan(0, 120)">南</button></div>
    <div class="map-viewport" @click="pick" aria-label="收货地址选点地图"><div class="map-canvas"><img v-for="tile in tiles" :key="tile.key" :src="tile.url" :style="{ left: `${tile.left}px`, top: `${tile.top}px` }" alt="" draggable="false" @error="tileError = true" /><span class="map-pin">📍</span></div></div>
    <small>地图数据 © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap contributors</a> · 坐标 WGS84</small>
    <p v-if="tileError" role="status">地图加载失败，可重试或返回手动填写地址。</p><p v-if="error" role="status">{{ error }}</p>
    <p>纬度 {{ latitude.toFixed(7) }}，经度 {{ longitude.toFixed(7) }}</p><button type="button" @click="emit('select', { latitude: Number(latitude.toFixed(7)), longitude: Number(longitude.toFixed(7)) })">确认使用此坐标</button>
  </section>
</template>
<style scoped>
.map-controls { display:flex; flex-wrap:wrap; gap:.4rem; margin: .7rem 0 }.map-viewport { height:320px; width:100%; max-width:512px; overflow:hidden; position:relative; background:#e7eddf; cursor:crosshair }.map-canvas { position:relative; width:512px; height:320px; left:50%; transform:translateX(-50%) }.map-canvas img { position:absolute; width:256px; height:256px; max-width:none }.map-pin { position:absolute; left:256px; top:160px; transform:translate(-50%,-100%); pointer-events:none; font-size:30px }.address-map small { display:block }
</style>
