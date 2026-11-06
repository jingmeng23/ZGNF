<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElDialog } from 'element-plus'
import 'element-plus/es/components/dialog/style/css'
import RegionSelector from '../components/RegionSelector.vue'
import AddressMap, { type MapSelection } from '../components/AddressMap.vue'
import { addressesApi, type AddressInput, type Region } from '../api/addresses'
import { ApiError } from '../api/http'
import type { UserAddress } from '../api/types'
import { parseAddress, PARSE_THRESHOLD, uploadToOcr } from '../platform/addressAssist'
const list = ref<UserAddress[]>([]), id = ref<string | null>(null), editing = ref(false), busy = ref(false), message = ref(''), notice = ref('')
const text = ref(''), duplicate = ref(false), path = ref<string[]>([])
const mapRef = ref<InstanceType<typeof AddressMap>>()
const coordinateSystem = ref('GCJ-02')
const selectedStreet = ref('')
const cache = new Map<string, Region[]>()
let session = 0, selection = 0
const blank = (): AddressInput => ({ receiver: '', phone: '', province: '', city: '', district: '', detail: '', fullAddress: '', adcode: null, isDefault: false, provinceCode: null, cityCode: null, districtCode: null, latitude: null, longitude: null })
const form = reactive<AddressInput>(blank())
async function regions(parent?: string) {
  const key = parent ?? ''
  if (!cache.has(key)) cache.set(key, await addressesApi.regions(parent))
  return cache.get(key)!
}
async function streetFor(districtCode: string, detail: string) {
  return (await regions(districtCode)).filter(r => detail.includes(r.name))
    .sort((a, b) => b.name.length - a.name.length)[0]
}
async function load() { list.value = await addressesApi.list() }
async function action(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; message.value = ''
  try { await task() } catch (e) { message.value = (e as Error).message } finally { busy.value = false }
}
async function edit(address?: UserAddress) {
  selectedStreet.value = ''
  coordinateSystem.value = address?.coordinateSystem ?? 'WGS84'
  notice.value = ''; duplicate.value = false; text.value = ''; path.value = []; session++; selection++
  Object.assign(form, blank(), address ? {
    receiver: address.receiver, phone: address.phone, province: address.province, city: address.city, district: address.district,
    detail: address.detail, isDefault: address.isDefault, provinceCode: address.provinceCode ?? null, cityCode: address.cityCode ?? null,
    districtCode: address.districtCode ?? null, longitude: address.longitude ?? null, latitude: address.latitude ?? null,
    adcode: address.adcode ?? address.districtCode ?? null,
  } : {})
  id.value = address?.id ?? null; editing.value = true
  path.value = [form.provinceCode, form.cityCode, form.districtCode].filter(Boolean) as string[]
  if (form.districtCode) {
    const current = session, token = selection
    try {
      const street = await streetFor(form.districtCode, form.detail)
      if (street && current === session && token === selection) { path.value.push(street.code); selectedStreet.value = street.name }
    } catch { /* Existing three-level addresses remain editable without street data. */ }
  }
}
function changeRegion(_value: (string | number)[], selected: Region[]) {
  selection++; duplicate.value = false
  const [p, c, d, street] = selected
  if (selectedStreet.value && selectedStreet.value !== street?.name && form.detail.startsWith(selectedStreet.value)) {
    form.detail = form.detail.slice(selectedStreet.value.length)
  }
  selectedStreet.value = street?.name ?? ''
  Object.assign(form, {
    provinceCode: p?.code ?? null, cityCode: c?.code ?? null, districtCode: d?.code ?? null,
    province: p?.name ?? '', city: c?.name ?? '', district: d?.name ?? '',
    latitude: null, longitude: null, adcode: d?.code ?? null,
  })
  if (street && !form.detail.includes(street.name)) form.detail = street.name + form.detail
  if (c) mapRef.value?.flyTo((p?.name ?? '') + c.name)
}
async function resolveRegion(code: string, provinceName: string, cityName: string, districtName: string) {
  const provinces = await regions()
  const p = provinces.find(r => r.code === code.slice(0, 2) + '0000') ?? provinces.find(r => r.name === provinceName)
  if (!p) return
  const cities = await regions(p.code)
  // Municipalities may return city=[] and use synthetic 市辖区 in the dictionary.
  const preferred = cities.find(r => r.code === code.slice(0, 4) + '00') ?? cities.find(r => r.name === cityName)
  for (const c of preferred ? [preferred, ...cities.filter(r => r !== preferred)] : cities) {
    const d = (await regions(c.code)).find(r => r.code === code || r.name === districtName)
    if (d) return { provinceCode: p.code, cityCode: c.code, districtCode: d.code, province: p.name, city: c.name, district: d.name }
  }
}
async function recognize(value = text.value) {
  const current = session, token = ++selection
  notice.value = '正在识别收货信息…'
  try {
    const { result, confidence } = parseAddress(value)
    if (!result || confidence < PARSE_THRESHOLD) { notice.value = '识别置信度较低，请检查并手动填写，已填内容未修改。'; return }
    const region = await resolveRegion(result.code, result.province, result.city, result.area)
    if (current !== session || token !== selection) return
    if (!region) { notice.value = '识别地区与当前区划不匹配，请手动选择，已填内容未修改。'; return }
    const street = await streetFor(region.districtCode, result.details)
    if (current !== session || token !== selection) return
    Object.assign(form, region, { receiver: result.name, phone: result.mobile, detail: result.details, latitude: null, longitude: null, adcode: region.districtCode })
    selectedStreet.value = street?.name ?? ''
    path.value = [region.provinceCode, region.cityCode, region.districtCode, ...(street ? [street.code] : [])]
    mapRef.value?.flyTo(region.province + region.city)
    notice.value = '已识别并填入，请核对后保存。'; duplicate.value = false
  } catch (error) {
    if (current !== session || token !== selection) return
    notice.value = error instanceof ApiError
      ? `地区数据加载失败：${error.message}。已填内容未修改，请重试。`
      : '识别失败，请刷新页面后重试，已填内容未修改。'
  }
}
function paste(event: ClipboardEvent) {
  const image = Array.from(event.clipboardData?.items ?? []).find(item => item.type.startsWith('image/'))?.getAsFile()
  if (image) { event.preventDefault(); notice.value = '图片识别功能开发中'; void uploadToOcr(image); return }
  const content = event.clipboardData?.getData('text/plain')
  if (content?.trim()) { event.preventDefault(); text.value = content.slice(0, 2000); void recognize(text.value) }
}
async function selected(value: MapSelection) {
  const current = session, token = ++selection
  try {
    const region = await resolveRegion(value.adcode, value.province, value.city, value.district)
    if (current !== session || token !== selection) return
    if (!region) { notice.value = '地图位置不在当前可选区划内，请手动选择地区。'; return }
    const street = await streetFor(region.districtCode, value.detail)
    if (current !== session || token !== selection) return
    Object.assign(form, region, { latitude: value.latitude, longitude: value.longitude, detail: value.detail, adcode: value.adcode })
    selectedStreet.value = street?.name ?? ''
    coordinateSystem.value = 'GCJ-02'
    path.value = [region.provinceCode, region.cityCode, region.districtCode, ...(street ? [street.code] : [])]
    duplicate.value = false; notice.value = '已根据地图位置更新地址。'
  } catch { if (current === session) notice.value = '地区同步失败，请手动填写。' }
}
function close() { session++; selection++; mapRef.value?.destroy() }
async function opened() {
  const current = session
  await mapRef.value?.initialize()
  if (current === session && editing.value && form.latitude == null && form.city) mapRef.value?.flyTo(form.province + form.city)
}
async function save(allowDuplicate = false) {
  await action(async () => {
    form.receiver = form.receiver.trim(); form.phone = form.phone.trim(); form.detail = form.detail.trim()
    if (form.receiver.length < 2 || form.receiver.length > 20) { notice.value = '收件人须为 2–20 字。'; return }
    if (!/^1[3-9]\d{9}$/.test(form.phone)) { notice.value = '请输入正确的 11 位手机号。'; return }
    if (form.detail.length < 5) { notice.value = '详细地址不少于 5 字。'; return }
    if (!form.provinceCode || !form.cityCode || !form.districtCode) { notice.value = '请选择完整的省市区。'; return }
    const prefix = [...new Set([form.province, form.city, form.district])].filter(v => v !== '市辖区' && v !== '县').join('')
    form.fullAddress = form.detail.startsWith(form.province) ? form.detail : prefix + form.detail
    try { await addressesApi.save(id.value, form, allowDuplicate); editing.value = false; await load(); message.value = '地址已保存。' }
    catch (e) { if (e instanceof ApiError && e.code === 409002) duplicate.value = true; else throw e }
  })
}
async function remove(address: UserAddress) { if (window.confirm('确定删除该收货地址？')) await action(async () => { await addressesApi.remove(address.id); await load() }) }
onMounted(() => action(load))
</script>
<template>
  <header class="page-heading"><RouterLink to="/account">‹ 返回我的</RouterLink><h1>我的地址</h1><p>管理您的配送地址，让每一份安心准时送达。</p></header>
  <p v-if="message" role="status" class="inline-notice">{{ message }}</p>
  <button class="button" :disabled="busy" @click="edit()">新增收货地址</button>
  <p v-if="!list.length && !busy" class="empty">还没有收货地址，添加第一个地址吧。</p>
  <div class="address-list"><article v-for="address in list" :key="address.id" class="panel">
    <h2>{{ address.receiver }} <span>{{ address.phone }}</span><small v-if="address.isDefault">默认地址</small></h2>
    <p>{{ address.province }} {{ address.city }} {{ address.district }} {{ address.detail }}</p>
    <p v-if="address.needsRegionCompletion" class="warning">{{ address.regionMessage }}，请编辑补全。</p>
    <div class="actions"><button :disabled="busy" @click="edit(address)">编辑</button><button :disabled="busy || address.isDefault" @click="action(async () => { await addressesApi.setDefault(address.id); await load() })">设为默认</button><button :disabled="busy" @click="remove(address)">删除</button></div>
  </article></div>
  <ElDialog v-model="editing" :title="id ? '编辑收货地址' : '新增收货地址'" width="min(760px, 94vw)" destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @opened="opened" @close="close">
    <form class="address-editor" @submit.prevent="save()" @input="duplicate = false; selection++">
      <label>粘贴收货信息<textarea v-model="text" maxlength="2000" placeholder="粘贴姓名、手机号和完整地址，自动识别填写" @paste="paste" /></label>
      <button type="button" :disabled="!text.trim()" @click="recognize()">识别文本</button>
      <p v-if="notice" role="status" class="warning">{{ notice }}</p>
      <p v-if="message" role="alert" class="warning">{{ message }}</p>
      <div class="contact"><label>收件人<input v-model="form.receiver" required minlength="2" maxlength="20" autocomplete="name" placeholder="2–20 字" /></label><label>手机号<input v-model="form.phone" required pattern="1[3-9][0-9]{9}" maxlength="11" inputmode="tel" autocomplete="tel" placeholder="11 位手机号" /></label></div>
      <div class="region-field"><span>省 / 市 / 区 / 街道</span><RegionSelector v-model="path" :load-regions="regions" :disabled="busy" @change="changeRegion" /></div>
      <label>详细地址<textarea v-model="form.detail" required minlength="5" maxlength="255" autocomplete="street-address" placeholder="街道、门牌号等，不少于 5 字" /></label>
      <AddressMap ref="mapRef" :latitude="form.latitude" :longitude="form.longitude" :coordinate-system="coordinateSystem" @select="selected" />
      <small v-if="form.longitude != null">已选坐标：{{ form.longitude }}, {{ form.latitude }}（{{ coordinateSystem }}）</small>
      <label class="check"><input v-model="form.isDefault" type="checkbox" />设为默认地址</label>
      <section v-if="duplicate" class="warning" role="alert">已有相同地址，是否仍然保存？<button type="button" :disabled="busy" @click="save(true)">仍然保存</button></section>
      <div class="actions"><button type="button" :disabled="busy" @click="editing = false">取消</button><button type="submit" class="button" :disabled="busy || duplicate">{{ busy ? '保存中…' : '保存地址' }}</button></div>
    </form>
  </ElDialog>
</template>
<style scoped>
.address-list { display:grid; gap:16px; margin:24px 0 }.address-list article { padding:24px }.address-list h2 { display:flex; gap:16px; align-items:center; flex-wrap:wrap; font-size:18px }.address-list h2 span { font-weight:400 }.address-list small { background:#e5f4e6; color:#267344; padding:4px 10px; border-radius:6px }.address-editor { display:grid; gap:16px }.address-editor label { display:grid; gap:8px; color:#334439 }.region-field { display:grid; gap:8px; color:#334439 }.contact { display:grid; grid-template-columns:1fr 1fr; gap:16px }.address-editor input:not([type=checkbox]),textarea { width:100%; border:1px solid #ccd5cf; border-radius:8px; padding:10px 12px; background:white; font:inherit }textarea { min-height:76px; resize:vertical }.check { display:flex!important; align-items:center }.actions { display:flex; gap:16px; align-items:center }.warning { color:#9d591b; background:#fff7e8; padding:10px; border-radius:6px }.empty { padding:40px 0; color:#758179 }button { cursor:pointer }button:disabled { opacity:.5; cursor:default }@media(max-width:520px) { .contact { grid-template-columns:1fr } }
</style>
