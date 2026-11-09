<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  Plus,
  RefreshCw,
  ChevronRight,
  ChevronDown,
  FolderTree,
  Folder,
  Search,
  CheckCircle2,
  CirclePause,
  Layers3,
} from 'lucide-vue-next'
import {
  categoriesApi,
  categoryRows,
  descendants,
  type AdminCategory,
  type CategoryInput,
} from '../api/categories'
import { ApiError } from '../api/http'

const rows = ref<AdminCategory[]>([])
const loading = ref(false),
  busy = ref(false),
  ready = ref(false)
const error = ref(''),
  notice = ref(''),
  keyword = ref(''),
  status = ref('')
const collapsed = ref(new Set<string>())
const mode = ref<'create' | 'edit' | 'detail' | null>(null),
  selectedId = ref<string | null>(null)
const editor = ref<HTMLElement>()
const form = reactive<CategoryInput>({ name: '', parentId: '0', sort: 0, status: 'active' })
const displayRows = computed(() =>
  categoryRows(rows.value, keyword.value, status.value, collapsed.value),
)
const allRows = computed(() => categoryRows(rows.value))
const excluded = computed(() =>
  selectedId.value ? descendants(rows.value, selectedId.value) : new Set<string>(),
)
const parentOptions = computed(() => allRows.value.filter((row) => !excluded.value.has(row.id)))
const readonly = computed(() => mode.value === 'detail')
const activeCount = computed(() => rows.value.filter((row) => row.status === 'active').length)
const rootCount = computed(() => rows.value.filter((row) => row.parentId === '0').length)
const filtering = computed(() => !!(keyword.value.trim() || status.value))
const matchedCount = computed(() => displayRows.value.filter((row) => !row.context).length)
function resetFilters() {
  keyword.value = ''
  status.value = ''
}
let version = 0
function explain(e: unknown) {
  return e instanceof ApiError && e.traceId
    ? `${e.message}（请求编号：${e.traceId}）`
    : e instanceof Error
      ? e.message
      : '操作失败，请重试'
}
async function load() {
  const current = ++version
  loading.value = true
  error.value = ''
  try {
    const result = await categoriesApi.list()
    if (current !== version) return
    rows.value = result
    ready.value = true
  } catch (e) {
    if (current === version) {
      error.value = explain(e)
      ready.value = false
    }
  } finally {
    if (current === version) loading.value = false
  }
}
function toggle(id: string) {
  const next = new Set(collapsed.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsed.value = next
}
async function open(row?: AdminCategory, detail = false, parentId = '0') {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const current = row ? await categoriesApi.detail(row.id) : null
    selectedId.value = current?.id || null
    Object.assign(
      form,
      current
        ? {
            name: current.name,
            parentId: current.parentId,
            sort: current.sort,
            status: current.status,
          }
        : { name: '', parentId, sort: 0, status: 'active' },
    )
    mode.value = detail ? 'detail' : current ? 'edit' : 'create'
    await nextTick()
    editor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function save() {
  if (busy.value || readonly.value || !mode.value) return
  if (
    !form.name.trim() ||
    form.name.trim().length > 64 ||
    !Number.isInteger(form.sort) ||
    form.sort < 0 ||
    form.sort > 2147483647
  ) {
    error.value = '请填写 1–64 字的分类名称，排序须为 0–2147483647 的整数'
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await categoriesApi.save(selectedId.value, { ...form, name: form.name.trim() })
    mode.value = null
    await load()
    notice.value = '分类已保存'
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function changeStatus(row: AdminCategory) {
  const next = row.status === 'active' ? 'inactive' : 'active'
  if (
    busy.value ||
    !window.confirm(
      `确认${next === 'active' ? '启用' : '停用'}“${row.name}”？停用前需要先停用子分类并下架关联商品。`,
    )
  )
    return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await categoriesApi.status(row.id, next)
    mode.value = null
    await load()
    notice.value = next === 'active' ? '分类已启用' : '分类已停用'
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function remove(row: AdminCategory) {
  if (
    busy.value ||
    row.childCount ||
    row.productCount ||
    !window.confirm(`确认删除“${row.name}”？此操作不可在页面恢复。`)
  )
    return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await categoriesApi.remove(row.id)
    mode.value = null
    await load()
    notice.value = '分类已删除'
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
onMounted(load)
onUnmounted(() => {
  version++
})
</script>

<template>
  <div class="admin-page categories-page">
    <header class="page-heading">
      <div>
        <span class="page-kicker">商品目录 · CATEGORIES</span>
        <h1>分类管理</h1>
        <p>让商品井然有序，让每一个分类都清晰易找。</p>
      </div>
      <button class="primary-button" :disabled="busy || loading || !ready" @click="open()">
        <Plus :size="16" />新增根分类
      </button>
    </header>
    <div class="category-overview" aria-label="分类概览">
      <article>
        <span class="overview-icon"><FolderTree :size="22" /></span>
        <div>
          <span>全部分类</span><strong>{{ ready ? rows.length : '—' }}<small>个分类</small></strong>
        </div>
      </article>
      <article>
        <span class="overview-icon blue"><Layers3 :size="22" /></span>
        <div>
          <span>一级分类</span><strong>{{ ready ? rootCount : '—' }}<small>个主目录</small></strong>
        </div>
      </article>
      <article>
        <span class="overview-icon"><CheckCircle2 :size="22" /></span>
        <div>
          <span>已启用</span><strong>{{ ready ? activeCount : '—' }}<small>个分类</small></strong>
        </div>
      </article>
      <article>
        <span class="overview-icon amber"><CirclePause :size="22" /></span>
        <div>
          <span>已停用</span
          ><strong>{{ ready ? rows.length - activeCount : '—' }}<small>个分类</small></strong>
        </div>
      </article>
    </div>
    <p v-if="error" class="category-feedback is-error" role="alert">
      {{ error }} <button class="text-action" :disabled="busy" @click="load">重新加载</button>
    </p>
    <p v-if="notice" class="category-feedback" role="status">{{ notice }}</p>
    <section
      v-if="mode"
      ref="editor"
      class="panel category-editor"
      aria-labelledby="category-editor-title"
    >
      <div class="panel-heading">
        <h2 id="category-editor-title">
          {{ readonly ? '分类详情' : mode === 'edit' ? '编辑分类' : '新增分类' }}
        </h2>
        <button class="text-action" :disabled="busy" @click="mode = null">关闭</button>
      </div>
      <p>
        启用分类要求所有上级已启用；不能将分类移动到自身或后代分类下。调整父级会连同整棵子树移动。
      </p>
      <form class="category-form" @submit.prevent="save">
        <fieldset :disabled="busy || readonly">
          <label
            >分类名称<input
              v-model="form.name"
              required
              maxlength="64"
              placeholder="请输入分类名称"
          /></label>
          <label
            >上级分类<select v-model="form.parentId" required>
              <option value="0">无（根分类）</option>
              <option v-for="parent in parentOptions" :key="parent.id" :value="parent.id">
                {{ parent.path }}{{ parent.status === 'inactive' ? '（已停用）' : '' }}
              </option>
            </select></label
          >
          <label
            >排序值<input
              v-model.number="form.sort"
              type="number"
              required
              min="0"
              max="2147483647"
              step="1"
          /></label>
          <label
            >状态<select v-model="form.status">
              <option value="active">启用</option>
              <option value="inactive">停用</option>
            </select></label
          >
        </fieldset>
        <div class="form-actions">
          <button v-if="!readonly" type="submit" class="primary-button" :disabled="busy || !ready">
            {{ busy ? '保存中…' : '保存分类' }}</button
          ><button type="button" class="secondary-button" :disabled="busy" @click="mode = null">
            {{ readonly ? '关闭详情' : '取消' }}
          </button>
        </div>
      </form>
    </section>
    <section class="panel category-list" aria-labelledby="category-list-title" :aria-busy="loading">
      <div class="list-heading">
        <div>
          <h2 id="category-list-title">
            <FolderTree :size="20" />分类目录 <span class="count-pill">{{ rows.length }}</span>
          </h2>
          <p>按层级管理分类，点击箭头展开下级目录</p>
        </div>
        <span class="list-heading-note">清晰分类，有序经营</span>
      </div>
      <div class="category-toolbar">
        <label class="search-field"
          ><span>搜索分类</span>
          <div>
            <Search :size="17" /><input
              v-model="keyword"
              type="search"
              placeholder="输入分类名称 / 编号"
              aria-label="搜索分类名称或编号"
            /></div
        ></label>
        <label class="status-field"
          >分类状态<select v-model="status">
            <option value="">全部状态</option>
            <option value="active">启用</option>
            <option value="inactive">停用</option>
          </select></label
        >
        <button class="secondary-button" :disabled="loading || busy" @click="load">
          <RefreshCw :size="15" :class="{ spinning: loading }" />{{ loading ? '刷新中' : '刷新' }}
        </button>
        <button class="text-action" :disabled="filtering" @click="collapsed = new Set()">
          展开全部
        </button>
        <button
          class="text-action"
          :disabled="filtering"
          @click="collapsed = new Set(rows.filter((r) => r.childCount > 0).map((r) => r.id))"
        >
          收起全部
        </button>
      </div>
      <p class="category-tip">
        <span v-if="filtering"
          >找到 {{ matchedCount }} 个匹配分类，保留上级路径
          <button class="text-action" @click="resetFilters">清除筛选</button></span
        >
        <span v-else>共 {{ rows.length }} 个分类 · 排序值越小越靠前，商品数仅统计直接关联商品</span>
      </p>
      <div class="table-scroll">
        <table class="admin-table">
          <thead>
            <tr>
              <th>分类名称 / 层级</th>
              <th>编号</th>
              <th>排序</th>
              <th>状态</th>
              <th>子分类</th>
              <th>直接关联商品</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="7">
                <div class="empty-state">
                  <RefreshCw class="spinning" :size="28" /><strong>正在加载分类</strong
                  ><span>正在整理商品目录，请稍候…</span>
                </div>
              </td>
            </tr>
            <tr v-else-if="!ready">
              <td colspan="7">
                <div class="empty-state">
                  <FolderTree :size="32" /><strong>分类加载失败</strong
                  ><button class="secondary-button" @click="load">重新加载</button>
                </div>
              </td>
            </tr>
            <tr v-else-if="!displayRows.length">
              <td colspan="7">
                <div class="empty-state">
                  <FolderTree :size="36" /><strong>{{
                    rows.length ? '没有找到相关分类' : '从第一个分类开始'
                  }}</strong
                  ><span>{{
                    rows.length
                      ? '试试其他关键词，或清除筛选条件'
                      : '建立清晰的分类目录，方便管理和查找商品'
                  }}</span
                  ><button v-if="rows.length" class="secondary-button" @click="resetFilters">
                    清除筛选</button
                  ><button v-else class="primary-button" :disabled="busy" @click="open()">
                    <Plus :size="16" />新增根分类
                  </button>
                </div>
              </td>
            </tr>
            <tr
              v-for="row in loading || !ready ? [] : displayRows"
              :key="row.id"
              :class="{ context: row.context }"
            >
              <td>
                <div
                  class="category-name"
                  :style="{ paddingLeft: `${Math.min(row.depth, 12) * 20}px` }"
                >
                  <button
                    v-if="row.childCount"
                    class="text-action toggle"
                    :disabled="filtering"
                    :aria-expanded="!!(keyword || status) || !collapsed.has(row.id)"
                    :aria-label="`展开或收起${row.name}`"
                    @click="toggle(row.id)"
                  >
                    <ChevronRight
                      v-if="collapsed.has(row.id) && !keyword && !status"
                      :size="16"
                    /><ChevronDown v-else :size="16" /></button
                  ><span v-else class="toggle-spacer" /><span
                    class="folder-mark"
                    :class="{ root: row.depth === 0 }"
                    ><Folder :size="17" /></span
                  ><button class="category-name-link" :disabled="busy" @click="open(row, true)">
                    {{ row.name }}</button
                  ><span v-if="row.depth === 0" class="root-label">一级</span>
                </div>
                <small :title="row.path"
                  >{{ row.path }}{{ row.context ? '（上级路径）' : '' }}</small
                >
              </td>
              <td class="category-id">#{{ row.id }}</td>
              <td>{{ row.sort }}</td>
              <td>
                <span
                  class="status-badge"
                  :class="row.status === 'active' ? 'enabled' : 'disabled'"
                  >{{ row.status === 'active' ? '启用' : '停用' }}</span
                >
              </td>
              <td>{{ row.childCount }}</td>
              <td>
                <RouterLink :to="{ path: '/admin/products', query: { categoryId: row.id } }"
                  >{{ row.productCount }} 件 · 查看</RouterLink
                >
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-action" :disabled="busy" @click="open(row, true)">详情</button
                  ><button class="text-action" :disabled="busy" @click="open(row)">编辑</button
                  ><button
                    class="text-action"
                    :disabled="busy"
                    @click="open(undefined, false, row.id)"
                  >
                    新增子分类</button
                  ><button class="text-action" :disabled="busy" @click="changeStatus(row)">
                    {{ row.status === 'active' ? '停用' : '启用' }}</button
                  ><button
                    class="text-action danger"
                    :disabled="busy || row.childCount > 0 || row.productCount > 0"
                    :title="
                      row.childCount || row.productCount
                        ? '有子分类或关联商品，不能删除'
                        : '删除分类'
                    "
                    @click="remove(row)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.category-editor {
  padding: 24px;
  margin-bottom: 24px;
  scroll-margin-top: 20px;
}
.category-editor p,
.category-tip {
  color: #75837b;
  font-size: 13px;
}
.category-form fieldset {
  border: 0;
  padding: 0;
  margin: 20px 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
.category-form label,
.category-toolbar label {
  display: grid;
  gap: 8px;
  font-size: 13px;
}
.category-form input,
.category-form select,
.category-toolbar input,
.category-toolbar select {
  border: 1px solid #dce4de;
  background: white;
  border-radius: 6px;
  padding: 10px;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
}
.form-actions,
.category-toolbar,
.row-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.category-toolbar {
  padding: 20px;
  align-items: end;
}
.category-tip {
  padding: 0 20px;
}
.table-scroll {
  overflow-x: auto;
}
.category-name {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.toggle,
.toggle-spacer {
  width: 20px;
  flex: 0 0 20px;
}
td small {
  display: block;
  color: #8a958e;
  margin-top: 5px;
  max-width: 360px;
  white-space: normal;
  overflow-wrap: anywhere;
}
.context {
  background: #f7f9f7;
}
.row-actions {
  min-width: 290px;
}
.danger {
  color: #b94343;
}
.enabled {
  color: #26724f;
  background: #eaf4ed;
}
.disabled {
  color: #777;
  background: #eee;
}
@media (max-width: 700px) {
  .category-form fieldset {
    grid-template-columns: 1fr;
  }
  .category-editor {
    padding: 16px;
  }
}

.categories-page {
  --category-line: #e6ece8;
}
.categories-page .page-heading {
  margin-bottom: 24px;
}
.category-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 26px;
}
.category-overview article {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 23px 20px;
  border: 1px solid var(--category-line);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 3px 12px #203e2d03;
}
.overview-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 14px;
  color: #287554;
  background: #edf6ef;
}
.overview-icon.blue {
  color: #517aaa;
  background: #edf3fb;
}
.overview-icon.amber {
  color: #b18a42;
  background: #fbf5e9;
}
.category-overview article div > span {
  color: #738079;
  font-size: 12px;
}
.category-overview strong {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 7px;
  color: #233e30;
  font-size: 29px;
  line-height: 1.2;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
.category-overview small {
  color: #94a097;
  font-size: 11px;
  font-weight: 400;
}
.category-list {
  border-radius: 12px;
  overflow: hidden;
}
.list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 23px 24px;
  border-bottom: 1px solid var(--category-line);
}
.list-heading h2 {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 16px;
  margin: 0;
}
.list-heading h2 > svg {
  color: #367b59;
}
.list-heading p {
  margin: 7px 0 0;
  color: #8a958e;
  font-size: 12px;
}
.list-heading-note {
  font-size: 12px;
  color: #8a9b8f;
}
.count-pill {
  padding: 3px 9px;
  border-radius: 20px;
  font-size: 11px;
  color: #568266;
  background: #edf5ef;
}
.category-toolbar {
  padding: 20px 24px 12px;
  gap: 16px;
}
.search-field {
  flex: 1;
  max-width: 380px;
  min-width: 200px;
}
.search-field > div {
  display: flex;
  align-items: center;
  border: 1px solid #dce4de;
  border-radius: 8px;
  background: #fff;
  padding-left: 12px;
  color: #8a978e;
}
.search-field > div:focus-within {
  border-color: #408b63;
  box-shadow: 0 0 0 3px #31805912;
}
.search-field input {
  border: 0;
  background: transparent;
  outline: none;
}
.status-field {
  min-width: 150px;
}
.category-toolbar select {
  border-radius: 8px;
}
.category-tip {
  padding: 0 24px 16px;
  margin: 4px 0 0;
  font-size: 12px;
}
.category-tip .text-action {
  margin-left: 10px;
}
.category-list .admin-table {
  min-width: 1050px;
}
.category-list .admin-table th {
  padding-top: 15px;
  padding-bottom: 15px;
  background: #f6f9f7;
  color: #7b8980;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.category-list .admin-table td {
  padding-top: 19px;
  padding-bottom: 19px;
  border-bottom: 1px solid #eff3f0;
}
.category-list .admin-table tbody tr:hover {
  background: #f8fbf9;
}
.category-name {
  gap: 8px;
}
.folder-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: #90a195;
  background: #f2f5f2;
  flex-shrink: 0;
}
.folder-mark.root {
  color: #448461;
  background: #ecf5ee;
}
.category-name-link {
  border: 0;
  background: none;
  padding: 0;
  color: #2a4032;
  font: inherit;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  max-width: 260px;
  overflow-wrap: anywhere;
  white-space: normal;
}
.category-name-link:hover {
  color: #267b4d;
  text-decoration: underline;
  text-underline-offset: 4px;
}
.root-label {
  padding: 2px 5px;
  color: #8b9b8e;
  border: 1px solid #e2eae3;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 400;
}
.category-id {
  font-size: 12px;
  color: #8b988f;
  font-variant-numeric: tabular-nums;
}
.category-list td small {
  font-size: 11px;
  color: #99a39c;
  margin-left: 28px;
  margin-top: 7px;
}
.category-list .status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 9px;
  border-radius: 6px;
  white-space: nowrap;
  font-size: 11px;
}
.status-badge::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.category-list .row-actions {
  gap: 12px;
  min-width: 270px;
  flex-wrap: nowrap;
}
.category-list .row-actions button {
  white-space: nowrap;
  font-size: 12px;
}
.categories-page button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.categories-page button:focus-visible,
.categories-page input:focus-visible,
.categories-page select:focus-visible {
  outline: 2px solid #438763;
  outline-offset: 3px;
}
.category-feedback {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid #d9eadd;
  border-radius: 8px;
  color: #30754b;
  background: #eff8f1;
  overflow-wrap: anywhere;
}
.category-feedback.is-error {
  border-color: #f1d9d5;
  background: #fff3f1;
  color: #ad5148;
}
.category-editor {
  border-radius: 12px;
  border-top: 3px solid #408761;
  box-shadow: 0 6px 20px #203e2d06;
}
.category-editor .panel-heading {
  margin-bottom: 12px;
}
.category-editor > p {
  line-height: 1.8;
  padding: 12px 14px;
  border-radius: 6px;
  background: #f6f9f7;
}
.category-form fieldset {
  gap: 22px;
}
.category-form input,
.category-form select {
  border-radius: 8px;
  min-height: 42px;
}
.form-actions {
  padding-top: 18px;
  border-top: 1px solid var(--category-line);
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 55px 20px;
  color: #9aaa9e;
}
.empty-state strong {
  font-size: 15px;
  color: #51695a;
}
.empty-state span {
  font-size: 12px;
}
.empty-state button {
  margin-top: 8px;
}
.spinning {
  animation: category-spin 1s linear infinite;
}
@keyframes category-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 1100px) {
  .category-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .category-overview {
    gap: 10px;
    margin-bottom: 18px;
  }
  .category-overview article {
    padding: 16px 12px;
    gap: 10px;
  }
  .overview-icon {
    width: 36px;
    height: 36px;
    border-radius: 10px;
  }
  .category-overview strong {
    font-size: 24px;
  }
  .category-overview small {
    font-size: 10px;
  }
  .list-heading {
    padding: 18px 16px;
  }
  .list-heading-note {
    display: none;
  }
  .category-toolbar {
    padding: 16px;
    gap: 14px;
  }
  .search-field {
    max-width: none;
    flex-basis: 100%;
  }
  .status-field {
    flex: 1;
  }
  .category-tip {
    padding: 0 16px 16px;
    line-height: 1.8;
  }
}
@media (prefers-reduced-motion: reduce) {
  .spinning {
    animation: none;
  }
}
</style>
