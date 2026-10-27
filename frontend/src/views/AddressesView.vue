<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AddressMap from '../components/AddressMap.vue'
import { addressesApi, type AddressDraft, type AddressInput, type Region } from '../api/addresses'
import { ApiError } from '../api/http'
import type { UserAddress } from '../api/types'
const list = ref<UserAddress[]>([]), id = ref<string | null>(null), editing = ref(false), busy = ref(false), message = ref('')
const provinces = ref<Region[]>([]), cities = ref<Region[]>([]), districts = ref<Region[]>([])
const text = ref(''), draft = ref<AddressDraft | null>(null), confirmed = ref(false), duplicate = ref(false), mapOpen = ref(false)
const blank = (): AddressInput => ({ receiver: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false, label: null, provinceCode: null, cityCode: null, districtCode: null, latitude: null, longitude: null })
const form = reactive<AddressInput>(blank())
async function load() { list.value = await addressesApi.list() }
async function action(task: () => Promise<void>) { if (busy.value) return; busy.value = true; message.value = ''; try { await task() } catch (e) { message.value = (e as Error).message } finally { busy.value = false } }
async function edit(address?: UserAddress) {
  await action(async () => {
    Object.assign(form, blank(), address ? { ...address, label: address.label ?? null, provinceCode: address.provinceCode ?? null, cityCode: address.cityCode ?? null, districtCode: address.districtCode ?? null, latitude: address.latitude ?? null, longitude: address.longitude ?? null } : {})
    id.value = address?.id ?? null; draft.value = null; duplicate.value = false; mapOpen.value = false; text.value = ''; editing.value = true
    provinces.value = await addressesApi.regions(); cities.value = form.provinceCode ? await addressesApi.regions(form.provinceCode) : []; districts.value = form.cityCode ? await addressesApi.regions(form.cityCode) : []
  })
}
async function changeProvince() {
  form.province = provinces.value.find(r => r.code === form.provinceCode)?.name ?? ''; form.cityCode = null; form.districtCode = null; form.city = ''; form.district = ''; districts.value = []
  await action(async () => { cities.value = form.provinceCode ? await addressesApi.regions(form.provinceCode) : [] })
}
async function changeCity() {
  form.city = cities.value.find(r => r.code === form.cityCode)?.name ?? ''; form.districtCode = null; form.district = ''
  await action(async () => { districts.value = form.cityCode ? await addressesApi.regions(form.cityCode) : [] })
}
function changeDistrict() { form.district = districts.value.find(r => r.code === form.districtCode)?.name ?? '' }
async function parse() { await action(async () => { draft.value = await addressesApi.parse(text.value); confirmed.value = false }) }
async function applyDraft() {
  if (!draft.value || !confirmed.value) return
  await action(async () => {
    const value = draft.value!
    for (const field of ['receiver', 'phone', 'province', 'city', 'district', 'provinceCode', 'cityCode', 'districtCode'] as const) if (value[field]) form[field] = value[field]!
    if (value.detail) form.detail = value.detail
    cities.value = form.provinceCode ? await addressesApi.regions(form.provinceCode) : []; districts.value = form.cityCode ? await addressesApi.regions(form.cityCode) : []
    draft.value = null; duplicate.value = false
  })
}
async function save(allowDuplicate = false) {
  await action(async () => {
    if (draft.value) { message.value = '请先确认或放弃识别结果'; return }
    if (!form.provinceCode || !form.cityCode || !form.districtCode) { message.value = '请先选择当前有效的省市区；地区列表为空时请联系平台补充区划数据'; return }
    try { await addressesApi.save(id.value, { ...form, allowDuplicate }); editing.value = false; duplicate.value = false; await load(); message.value = '地址已保存' }
    catch (e) { if (e instanceof ApiError && e.code === 409002) duplicate.value = true; else throw e }
  })
}
function selected(point: { latitude: number; longitude: number }) { form.latitude = point.latitude; form.longitude = point.longitude; mapOpen.value = false; duplicate.value = false }
async function reverse() { await action(async () => { if (form.latitude == null || form.longitude == null) return; draft.value = await addressesApi.reverse(form.latitude, form.longitude); confirmed.value = false }) }
async function remove(address: UserAddress) { if (window.confirm('删除该地址？已提交订单的地址不会改变。')) await action(async () => { await addressesApi.remove(address.id); await load() }) }
onMounted(() => action(load))
</script>
<template>
  <header class="page-heading"><h1>收货地址</h1><p>编辑地址簿不会修改历史订单。已支付订单变更收货地址需通过订单服务单独处理。</p></header>
  <p v-if="message" role="status" class="inline-notice">{{ message }}</p>
  <button type="button" class="button" :disabled="busy" @click="edit()">添加收货地址</button>
  <div class="address-list"><article class="panel" v-for="address in list" :key="address.id"><h2>{{ address.receiver }} · {{ address.phone }} <small>{{ address.label }} {{ address.isDefault ? '默认' : '' }}</small></h2><p>{{ address.province }}{{ address.city }}{{ address.district }}{{ address.detail }}</p><p v-if="address.needsRegionCompletion" class="error-message">{{ address.regionMessage }}，请编辑补全后再下单。</p><button type="button" :disabled="busy" @click="edit(address)">编辑</button> <button type="button" :disabled="busy || address.isDefault" @click="action(async () => { await addressesApi.setDefault(address.id); await load() })">设为默认</button> <button type="button" :disabled="busy" @click="remove(address)">删除</button></article></div>
  <form v-if="editing" class="panel address-editor" @submit.prevent="save()" @input="duplicate = false">
    <h2>{{ id ? '编辑地址' : '新增地址' }}</h2>
    <label>粘贴收货信息<textarea v-model="text" maxlength="2000" placeholder="张三 13800138000 北京市朝阳区…" /></label><button type="button" :disabled="busy || !text.trim()" @click="parse">识别为待确认草稿</button>
    <section v-if="draft" class="draft" aria-label="待确认识别结果"><h3>请核对识别结果</h3><p>{{ draft.receiver || '姓名待补充' }} {{ draft.phone || '电话待补充' }}</p><p>{{ draft.province }}{{ draft.city }}{{ draft.district }}{{ draft.detail }}</p><p v-for="warning in draft.warnings" :key="warning">{{ warning }}</p><label><input v-model="confirmed" type="checkbox" /> 我已核对，应用后继续编辑</label><button type="button" :disabled="busy || !confirmed" @click="applyDraft">确认填入表单</button><button type="button" @click="draft = null">不使用识别结果</button></section>
    <label>收货人<input v-model="form.receiver" maxlength="64" required autocomplete="name" /></label><label>手机<input v-model="form.phone" inputmode="tel" pattern="1[3-9][0-9]{9}" maxlength="11" required autocomplete="tel" /></label>
    <p v-if="form.province && !provinces.some(r => r.code === form.provinceCode)">原地址地区：{{ form.province }}{{ form.city }}{{ form.district }}。请选择更新后的地区。</p>
    <label>省份<select v-model="form.provinceCode" required @change="changeProvince"><option :value="null" disabled>请选择</option><option v-for="r in provinces" :key="r.code" :value="r.code">{{ r.name }}</option></select></label>
    <label>城市<select v-model="form.cityCode" required :disabled="!form.provinceCode || busy" @change="changeCity"><option :value="null" disabled>请选择</option><option v-for="r in cities" :key="r.code" :value="r.code">{{ r.name }}</option></select></label>
    <label>区县<select v-model="form.districtCode" required :disabled="!form.cityCode || busy" @change="changeDistrict"><option :value="null" disabled>请选择</option><option v-for="r in districts" :key="r.code" :value="r.code">{{ r.name }}</option></select></label>
    <label>详细地址<textarea v-model="form.detail" maxlength="255" required autocomplete="street-address" /></label><label>地址标签<input v-model="form.label" list="address-labels" maxlength="20" placeholder="选择或输入自定义标签" /><datalist id="address-labels"><option value="家" /><option value="公司" /><option value="学校" /></datalist></label>
    <p>地图加载后，地图服务会接收查看的地图区域。定位仅在授权后获取；附近地址识别会将选点坐标发送给地图地址服务。</p><button type="button" @click="mapOpen = !mapOpen">{{ mapOpen ? '关闭地图' : '同意并打开选点地图' }}</button><AddressMap v-if="mapOpen" :latitude="form.latitude" :longitude="form.longitude" @select="selected" />
    <p v-if="form.latitude != null">已确认选点：{{ form.latitude }}，{{ form.longitude }}（WGS84） <button type="button" :disabled="busy" @click="reverse">同意发送坐标，识别附近地址</button> <button type="button" @click="form.latitude = null; form.longitude = null">清除选点</button></p>
    <label><input type="checkbox" v-model="form.isDefault" /> 设为默认地址</label>
    <section v-if="duplicate" class="draft" role="alert"><p>已有相同收货地址，仍要保存吗？</p><button type="button" :disabled="busy" @click="save(true)">仍然保存</button><button type="button" @click="duplicate = false">返回检查</button></section>
    <div><button type="submit" class="button" :disabled="busy || duplicate || !!draft">保存地址</button><button type="button" :disabled="busy" @click="editing = false">取消</button></div>
  </form>
</template>
<style scoped>
.address-list { display:grid; gap:1rem; margin:1rem 0 }.address-list article,.address-editor { padding:1.4rem }.address-list h2 { font-size:1.1rem }.address-editor { display:grid; gap:.8rem; max-width:760px }.address-editor label { display:grid; gap:.4rem }.address-editor label:has(input[type=checkbox]) { display:flex; align-items:center }.address-editor input:not([type=checkbox]),.address-editor textarea,.address-editor select { border:1px solid #b8c6bb; border-radius:6px; padding:.7rem; width:100%; background:white }.address-editor textarea { min-height:75px }.draft { background:#f5f1dd; padding:1rem; border-radius:8px }button { cursor:pointer }button:disabled { cursor:default; opacity:.6 }
</style>
