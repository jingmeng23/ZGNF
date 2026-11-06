<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'
import { municipalityCity } from '../platform/addressAssist'
const props = defineProps<{ latitude?: number | null; longitude?: number | null; coordinateSystem?: string }>()
export interface MapSelection { latitude: number; longitude: number; detail: string; adcode: string; province: string; city: string; district: string }
const emit = defineEmits<{ select: [value: MapSelection] }>()
const container = ref<HTMLDivElement>(), error = ref(''), locating = ref(false)
let sdk: any, map: any, marker: any, geocoder: any
let generation = 0, request = 0
const defaultCenter = [104.7784, 29.3392]
function destroy() { generation++; request++; map?.destroy(); map = marker = geocoder = sdk = undefined; locating.value = false }
async function initialize() {
  destroy(); error.value = ''; const current = generation
  let timeout: ReturnType<typeof setTimeout> | undefined
  try {
    if (!import.meta.env.VITE_AMAP_KEY?.trim()) {
      error.value = '地图未配置，请联系管理员；仍可手动填写并保存地址。'
      return
    }
    ;(window as any)._AMapSecurityConfig = { securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE }
    const loaded = await Promise.race([AMapLoader.load({ key: import.meta.env.VITE_AMAP_KEY.trim(), version: '2.0', plugins: ['AMap.Geocoder'] }), new Promise<never>((_, reject) => { timeout = setTimeout(() => reject(new Error('地图加载超时')), 12000) })])
    if (current !== generation || !container.value) return
    sdk = loaded
    const existing = props.longitude != null && props.latitude != null
    let center = existing ? [props.longitude, props.latitude] : defaultCenter
    if (existing && props.coordinateSystem === 'WGS84') {
      center = await new Promise<any>((resolve, reject) => sdk.convertFrom(center, 'gps', (status: string, result: any) => status === 'complete' ? resolve(result.locations[0]) : reject(new Error('坐标转换失败'))))
      if (current !== generation) return
    }
    map = new sdk.Map(container.value, { zoom: existing ? 16 : 12, center })
    geocoder = new sdk.Geocoder()
    marker = new sdk.Marker({ position: center, draggable: true, map })
    map.on('click', (event: any) => pick(event.lnglat))
    marker.on('dragend', (event: any) => pick(event.lnglat))
    map.resize()
  } catch { if (current === generation) error.value = '地图加载失败，请手动填写地址，仍可正常保存。' }
  finally { if (timeout) clearTimeout(timeout) }
}
function pick(point: any) {
  if (!map) return
  const token = ++request, current = generation
  const longitude = point.getLng(), latitude = point.getLat()
  marker.setPosition([longitude, latitude])
  geocoder.getAddress([longitude, latitude], (status: string, result: any) => {
    if (current !== generation || token !== request) return
    if (status !== 'complete' || !result.regeocode) { error.value = '地址识别失败，请重试或手动填写。'; return }
    error.value = ''
    const a = result.regeocode, c = a.addressComponent
    emit('select', { longitude, latitude, detail: a.formattedAddress, adcode: c.adcode, province: c.province, city: municipalityCity(c), district: c.district })
  })
}
function flyTo(city: string) {
  if (!map || !city) return
  const token = ++request, current = generation
  geocoder.getLocation(city, (status: string, result: any) => {
    if (current !== generation || token !== request) return
    if (status === 'complete' && result.geocodes?.length) {
      const point = result.geocodes[0].location
      map.setZoomAndCenter(12, point, false, 600); marker.setPosition(point)
    } else error.value = '城市定位失败，可手动选择地图位置。'
  })
}
function locate() {
  if (!map) return
  if (!navigator.geolocation) { error.value = '浏览器不支持定位，可在默认城市手动选点。'; return }
  const current = generation; locating.value = true
  navigator.geolocation.getCurrentPosition(position => {
    if (current !== generation || !map) return
    locating.value = false
    sdk.convertFrom([position.coords.longitude, position.coords.latitude], 'gps', (status: string, result: any) => {
      if (current !== generation || !map) return
      if (status === 'complete') { map.setZoomAndCenter(16, result.locations[0]); pick(result.locations[0]) }
      else error.value = '定位转换失败，请手动选点。'
    })
  }, () => {
    if (current !== generation || !map) return
    locating.value = false; error.value = '未获得定位授权或定位失败，可在默认城市手动选点。'
  }, { timeout: 10000, maximumAge: 0 })
}
defineExpose({ initialize, destroy, flyTo })
onBeforeUnmount(destroy)
</script>
<template>
  <section><div ref="container" class="map" aria-label="点击地图或拖动标记选择收货地址" />
    <p v-if="error" role="status">{{ error }}</p>
    <button type="button" :disabled="locating" @click="locate">{{ locating ? '定位中…' : '定位到我的位置' }}</button>
    <small>点击地图或拖动标记自动填写地址；定位需浏览器授权。</small>
  </section>
</template>
<style scoped>.map { height:300px; width:100%; background:#edf2ec; border-radius:8px }p { color:#a75317 }small { display:block; margin-top:8px; color:#666 }</style>
