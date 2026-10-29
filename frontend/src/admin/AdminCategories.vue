<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Plus, RefreshCw, ChevronRight, ChevronDown } from 'lucide-vue-next'
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
        <span class="page-kicker">CATEGORIES</span>
        <h1>分类管理</h1>
        <p>维护分类层级、展示顺序和启用状态，排序值越小越靠前。</p>
      </div>
      <button class="primary-button" :disabled="busy || loading || !ready" @click="open()">
        <Plus :size="16" />新增根分类
      </button>
    </header>
    <p v-if="error" class="demo-banner" role="alert">
      {{ error }} <button class="text-action" :disabled="busy" @click="load">重新加载</button>
    </p>
    <p v-if="notice" class="demo-banner" role="status">{{ notice }}</p>
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
    <section class="panel">
      <div class="category-toolbar">
        <label
          >搜索分类<input v-model="keyword" type="search" placeholder="分类名称 / 编号"
        /></label>
        <label
          >分类状态<select v-model="status">
            <option value="">全部状态</option>
            <option value="active">启用</option>
            <option value="inactive">停用</option>
          </select></label
        >
        <button class="secondary-button" :disabled="loading || busy" @click="load">
          <RefreshCw :size="15" />刷新
        </button>
        <button class="text-action" @click="collapsed = new Set()">展开全部</button>
        <button
          class="text-action"
          @click="collapsed = new Set(rows.filter((r) => r.childCount > 0).map((r) => r.id))"
        >
          收起全部
        </button>
      </div>
      <p class="category-tip">
        共 {{ rows.length }} 个分类 ·
        {{
          rows.filter((r) => r.status === 'active').length
        }}
        个启用。搜索保留匹配项的上级路径；商品数仅统计直接关联且未删除的商品。
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
              <td colspan="7">正在加载分类…</td>
            </tr>
            <tr v-else-if="!ready">
              <td colspan="7">分类加载失败，请重试。</td>
            </tr>
            <tr v-else-if="!displayRows.length">
              <td colspan="7">
                {{ rows.length ? '没有符合条件的分类' : '暂无分类，请先新增根分类' }}
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
                    :aria-expanded="!!(keyword || status) || !collapsed.has(row.id)"
                    :aria-label="`展开或收起${row.name}`"
                    @click="toggle(row.id)"
                  >
                    <ChevronRight
                      v-if="collapsed.has(row.id) && !keyword && !status"
                      :size="16"
                    /><ChevronDown v-else :size="16" /></button
                  ><span v-else class="toggle-spacer" /><strong>{{ row.name }}</strong>
                </div>
                <small :title="row.path"
                  >{{ row.path }}{{ row.context ? '（上级路径）' : '' }}</small
                >
              </td>
              <td>{{ row.id }}</td>
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
</style>
