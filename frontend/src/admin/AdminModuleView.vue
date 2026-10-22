<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Download, Search, SlidersHorizontal, X } from 'lucide-vue-next'
import { adminApi, adminDemoMode } from '../api/admin'
import { ApiError } from '../api/http'
import { modules, type AdminRow } from './adminData'
import { toCsv } from './csv'

const route = useRoute()
const module = computed(() => modules[String(route.params.module)])
const keyword = ref('')
const filter = ref('')
const page = ref(1)
const size = ref(5)
const remoteRows = ref<AdminRow[]>([])
const remoteTotal = ref(0)
const loading = ref(false)
const error = ref('')
const selected = ref<AdminRow | null>(null)
const detailDialog = ref<HTMLDialogElement>()
let loadVersion = 0
let searchTimer: ReturnType<typeof setTimeout> | undefined

async function load() {
  const version = ++loadVersion
  remoteRows.value = []
  remoteTotal.value = 0
  error.value = ''
  if (adminDemoMode || !module.value) return
  loading.value = true
  try {
    const result = await adminApi.list(module.value.endpoint, {
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim(),
      filter: filter.value,
    })
    if (version === loadVersion) {
      remoteRows.value = result.list
      remoteTotal.value = result.total
    }
  } catch (cause) {
    if (version === loadVersion)
      error.value =
        cause instanceof ApiError && cause.traceId
          ? `${cause.message}（请求编号：${cause.traceId}）`
          : cause instanceof Error
            ? cause.message
            : '数据加载失败'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

watch(module, () => {
  keyword.value = ''
  filter.value = ''
  selected.value = null
  detailDialog.value?.close()
})
watch([module, keyword, filter, size], () => {
  page.value = 1
})
watch(
  [module, keyword, filter, page, size],
  () => {
    clearTimeout(searchTimer)
    ++loadVersion
    remoteRows.value = []
    remoteTotal.value = 0
    loading.value = !adminDemoMode
    searchTimer = setTimeout(() => {
      void load()
    }, 250)
  },
  { immediate: true },
)
onUnmounted(() => {
  clearTimeout(searchTimer)
  ++loadVersion
})

const filteredRows = computed(
  () =>
    module.value?.rows.filter((row) => {
      const query = keyword.value.trim().toLowerCase()
      return (
        (!query ||
          Object.values(row).some((value) => String(value).toLowerCase().includes(query))) &&
        (!filter.value || Object.values(row).some((value) => value === filter.value))
      )
    }) ?? [],
)
const total = computed(() => (adminDemoMode ? filteredRows.value.length : remoteTotal.value))
const visibleRows = computed(() =>
  adminDemoMode
    ? filteredRows.value.slice((page.value - 1) * size.value, page.value * size.value)
    : remoteRows.value,
)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

function exportRows() {
  if (!module.value) return
  const rows = adminDemoMode ? filteredRows.value : remoteRows.value
  const blob = new Blob(['\ufeff' + toCsv(module.value.columns, rows)], {
    type: 'text/csv;charset=utf-8',
  })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${module.value.title}${adminDemoMode ? '-演示数据' : `-第${page.value}页`}.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
}
function showDetail(row: AdminRow) {
  selected.value = row
  detailDialog.value?.showModal()
}
function formatCell(value: unknown, kind?: string) {
  if (value === undefined || value === null || value === '') return '—'
  return kind === 'money' && Number.isFinite(Number(value))
    ? `¥ ${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : String(value)
}
</script>

<template>
  <div v-if="module" class="admin-page">
    <div class="page-heading">
      <div>
        <span class="page-kicker">OPERATIONS</span>
        <h1>{{ module.title }}</h1>
        <p>{{ module.description }}</p>
      </div>
      <span class="admin-page-note">{{
        adminDemoMode ? '示例记录仅用于预览' : '数据由服务端提供'
      }}</span>
    </div>
    <div v-if="adminDemoMode" class="demo-banner">
      <span class="demo-dot"></span>演示数据 · 不会修改真实业务记录<span class="updated-at"
        >共 {{ total }} 条记录</span
      >
    </div>
    <div v-if="error" class="demo-banner" role="alert">
      {{ error }}<button class="text-action" @click="load">重新加载</button>
    </div>
    <section class="panel list-panel" :aria-busy="loading">
      <div class="list-toolbar">
        <div class="filter-control">
          <SlidersHorizontal :size="16" /><select v-model="filter" aria-label="筛选条件">
            <option value="">{{ module.filters[0] }}</option>
            <option v-for="option in module.filters.slice(1)" :key="option" :value="option">
              {{ option }}
            </option>
          </select>
        </div>
        <label class="table-search"
          ><Search :size="16" /><input
            v-model="keyword"
            type="search"
            aria-label="搜索记录"
            placeholder="搜索编号、名称或联系人"
        /></label>
        <button
          class="secondary-button"
          :disabled="loading || !!error || !total"
          @click="exportRows"
        >
          <Download :size="15" />{{ adminDemoMode ? '导出' : '导出本页' }}
        </button>
      </div>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th v-for="column in module.columns" :key="column.key" scope="col">
                {{ column.label }}
              </th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in visibleRows" :key="row.id ?? index">
              <td
                v-for="(column, columnIndex) in module.columns"
                :key="column.key"
                :class="{
                  'table-id': columnIndex === 0,
                  'muted-cell': column.kind === 'muted',
                  'amount-cell': column.kind === 'money',
                }"
              >
                <span
                  v-if="column.kind === 'status'"
                  class="status-tag"
                  :class="`status-${String(row[column.key])}`"
                  >{{ formatCell(row[column.key]) }}</span
                >
                <template v-else>{{ formatCell(row[column.key], column.kind) }}</template>
              </td>
              <td>
                <button
                  class="text-action"
                  :aria-label="`查看${row.id}详情`"
                  @click="showDetail(row)"
                >
                  查看详情
                </button>
              </td>
            </tr>
            <tr v-if="!visibleRows.length">
              <td :colspan="module.columns.length + 1" class="empty-state" role="status">
                {{
                  loading
                    ? '正在加载数据…'
                    : error
                      ? '暂时无法获取数据，请重试'
                      : '没有找到匹配的记录'
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span>共 {{ total }} 条</span>
        <div>
          <select v-model.number="size" aria-label="每页记录数">
            <option :value="5">5 条 / 页</option>
            <option :value="10">10 条 / 页</option>
            <option :value="20">20 条 / 页</option></select
          ><button :disabled="loading || page <= 1" @click="page--">上一页</button
          ><span>{{ page }} / {{ pageCount }}</span
          ><button :disabled="loading || page >= pageCount" @click="page++">下一页</button>
        </div>
      </div>
    </section>
    <dialog
      ref="detailDialog"
      class="admin-detail"
      aria-labelledby="detail-title"
      @click="
        (event) => {
          if (event.target === detailDialog) detailDialog?.close()
        }
      "
    >
      <div class="panel-heading">
        <div>
          <h2 id="detail-title">{{ module.title }}详情</h2>
          <p v-if="adminDemoMode">演示记录</p>
        </div>
        <button class="icon-button" aria-label="关闭详情" @click="detailDialog?.close()">
          <X :size="17" />
        </button>
      </div>
      <dl v-if="selected">
        <div v-for="column in module.columns" :key="column.key">
          <dt>{{ column.label }}</dt>
          <dd>{{ formatCell(selected[column.key], column.kind) }}</dd>
        </div>
      </dl>
    </dialog>
  </div>
</template>
