<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, useId } from 'vue'
import { addressesApi, type Region } from '../api/addresses'
export type RegionCode = string | number
const props = withDefaults(defineProps<{
  modelValue?: RegionCode[]
  loadRegions?: (parent?: string) => Promise<Region[]>
  disabled?: boolean
}>(), { modelValue: () => [], loadRegions: addressesApi.regions })
const emit = defineEmits<{
  'update:modelValue': [value: RegionCode[]]
  change: [value: RegionCode[], regions: Region[]]
}>()
const root = ref<HTMLElement>(), open = ref(false), panelId = useId()
const headings = ['省份', '城市', '区县', '街道']
const columns = ref<Region[][]>([[], [], [], []])
const chosen = ref<Region[]>([]), loading = ref([false, false, false, false]), errors = ref(['', '', '', ''])
const label = computed(() => chosen.value.map(r => r.name).join('/'))
let revision = 0
const pending = new Map<string, Promise<Region[]>>()
function fetchRegions(parent?: string) {
  const key = parent ?? ''
  if (!pending.has(key)) {
    const promise = Promise.resolve().then(() => props.loadRegions(parent)).catch(error => { pending.delete(key); throw error })
    pending.set(key, promise)
  }
  return pending.get(key)!
}
async function loadColumn(level: number, token: number) {
  const parent = level ? chosen.value[level - 1]?.code : undefined
  if (level && !parent) return
  loading.value[level] = true; errors.value[level] = ''
  try {
    const rows = await fetchRegions(parent)
    if (token === revision) columns.value[level] = rows
  } catch { if (token === revision) errors.value[level] = '加载失败，点击重试' }
  finally { if (token === revision) loading.value[level] = false }
}
async function restore(values: RegionCode[]) {
  const token = ++revision
  chosen.value = []; columns.value = [[], [], [], []]; loading.value = [false, false, false, false]; errors.value = ['', '', '', '']
  for (let level = 0; level < 4; level++) {
    await loadColumn(level, token)
    if (token !== revision) return
    const match = columns.value[level]!.find(r => r.code === String(values[level]))
    if (!match) return
    chosen.value.push(match)
  }
}
async function choose(level: number, item: Region) {
  if (chosen.value[level]?.code === item.code) return
  const token = ++revision
  chosen.value = [...chosen.value.slice(0, level), item]
  for (let i = level + 1; i < 4; i++) { columns.value[i] = []; loading.value[i] = false; errors.value[i] = '' }
  const values = chosen.value.map(r => {
    const original = props.modelValue.find(value => String(value) === r.code)
    return original ?? (typeof props.modelValue[0] === 'number' ? Number(r.code) : r.code)
  })
  emit('update:modelValue', values)
  emit('change', values, [...chosen.value])
  if (level < 3) await loadColumn(level + 1, token)
  else open.value = false
}
function outside(event: PointerEvent) { if (!root.value?.contains(event.target as Node)) open.value = false }
watch(() => props.modelValue.slice(), (values, previous) => {
  if (!previous || values.map(String).join('/') !== chosen.value.map(r => r.code).join('/')) void restore(values)
}, { immediate: true })
onMounted(() => document.addEventListener('pointerdown', outside))
onBeforeUnmount(() => { revision++; document.removeEventListener('pointerdown', outside) })
</script>
<template>
  <div ref="root" class="region-selector" @keydown.esc.stop="open = false">
    <button type="button" class="region-trigger" :disabled="disabled" aria-haspopup="dialog" :aria-expanded="open" :aria-controls="panelId" @click="open = !open" @keydown.down.prevent="open = true">
      <span :class="{ placeholder: !label }">{{ label || '请选择省 / 市 / 区 / 街道' }}</span><span class="arrow" :class="{ expanded: open }" aria-hidden="true">⌄</span>
    </button>
    <section v-if="open" :id="panelId" class="region-panel" role="dialog" aria-label="选择收货地区">
      <header><span>选择收货地区</span><button type="button" class="close" aria-label="关闭地区选择面板" @click="open = false">×</button></header>
      <div class="region-columns">
        <section v-for="(title, level) in headings" :key="title" class="region-column" :aria-label="title" :aria-busy="loading[level]">
          <h3>{{ title }}</h3><div class="region-items">
            <p v-if="loading[level]" role="status">加载中…</p>
            <button v-else-if="errors[level]" type="button" class="retry" @click="loadColumn(level, revision)">{{ errors[level] }}</button>
            <template v-else>
              <button v-for="item in columns[level]" :key="item.code" type="button" class="region-item" :class="{ selected: chosen[level]?.code === item.code }" :aria-pressed="chosen[level]?.code === item.code" @click="choose(level, item)">{{ item.name }}</button>
              <p v-if="!columns[level]?.length">{{ level === 0 || chosen[level - 1] ? '暂无地区数据' : '请选择' }}</p>
            </template>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>
<style scoped>
.region-selector { position:relative; width:100%; min-width:0; font-size:14px; color:#222 }
.region-trigger { width:100%; display:flex; align-items:center; justify-content:space-between; gap:12px; min-height:42px; padding:10px 12px; border:1px solid #ccd5cf; border-radius:8px; background:#fff; color:#222; font:inherit; text-align:left; cursor:pointer }
.region-trigger:focus-visible { outline:2px solid #cb3037; outline-offset:2px }.region-trigger:disabled { cursor:default; opacity:.6 }.placeholder { color:#909399 }.arrow { font-size:20px; transition:transform .15s }.expanded { transform:rotate(180deg) }
.region-panel { position:absolute; z-index:30; top:calc(100% + 8px); left:0; width:100%; min-width:0; border:1px solid #e2e4e7; border-radius:10px; box-shadow:0 8px 28px #0000001c; background:white; overflow:hidden }
header { display:flex; align-items:center; justify-content:space-between; padding:12px 16px; border-bottom:1px solid #eee; font-weight:600 }.close { border:0; background:transparent; padding:0 5px; font-size:24px; color:#666; cursor:pointer }
.region-columns { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)) }.region-column { min-width:0; border-right:1px solid #eee }.region-column:last-child { border-right:0 }h3 { margin:0; padding:12px; font-size:14px; font-weight:600; background:#fafafa }.region-items { height:240px; overflow-y:auto; overscroll-behavior:contain; padding:6px }.region-items p { padding:8px 6px; color:#999; font-size:13px }.region-item,.retry { display:block; width:100%; padding:9px 6px; border:0; border-radius:4px; background:white; color:#222; font:inherit; text-align:left; cursor:pointer; overflow-wrap:anywhere }.region-item:hover { background:#fff3f3 }.region-item.selected { color:#d52c36; background:#fff3f3; font-weight:600 }.retry { color:#b54e23 }.region-item:focus-visible { outline:2px solid #d52c36; outline-offset:-2px }
@media(max-width:520px) { header { padding:8px 10px }h3 { padding:10px 6px }.region-items { padding:3px }.region-item { padding:9px 3px; font-size:12px } }
</style>
