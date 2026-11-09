<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Check, Plus, Search, ShieldCheck, SlidersHorizontal } from 'lucide-vue-next'
import { rolesApi, type PermissionRecord, type RoleRecord } from '../api/roles'
import { can, moduleNames, refreshAdminProfile } from './permissions'
import { computed } from 'vue'
const writable = computed(() => can('role:write'))
const editor = ref<HTMLElement | null>(null)
const permissionSearch = ref('')
const editablePermissions = computed(() => writable.value && !selected.value?.system)
const groups = computed(() => {
  const result: Record<string, PermissionRecord[]> = {}
  for (const p of permissions.value) {
    if (
      !`${p.name} ${p.permissionKey} ${moduleNames[p.permissionKey.split(':')[0]] || ''}`
        .toLowerCase()
        .includes(permissionSearch.value.trim().toLowerCase())
    )
      continue
    const key = p.permissionKey.split(':')[0]
    ;(result[key] ||= []).push(p)
  }
  return result
})
function toggle(items: PermissionRecord[], checked: boolean) {
  const next = new Set(ids.value)
  items.forEach((p) => (checked ? next.add(p.id) : next.delete(p.id)))
  ids.value = [...next]
}
const rows = ref<RoleRecord[]>([]),
  permissions = ref<PermissionRecord[]>([]),
  selected = ref<RoleRecord | null>(null),
  mode = ref<'create' | 'edit' | 'permissions' | ''>(''),
  busy = ref(false),
  loading = ref(false),
  error = ref(''),
  total = ref(0),
  pages = ref(1)
const q = reactive({ page: 1, size: 10, keyword: '', status: '' }),
  form = reactive({ name: '', roleKey: '', status: 'active' as 'active' | 'inactive' }),
  ids = ref<string[]>([])
const explain = (e: unknown) => (e instanceof Error ? e.message : '操作失败，请重试')
async function load() {
  loading.value = true
  error.value = ''
  try {
    const r = await rolesApi.list(q)
    rows.value = r.list
    total.value = r.total
    pages.value = Math.max(1, r.pages)
  } catch (e) {
    error.value = explain(e)
  } finally {
    loading.value = false
  }
}
function close() {
  mode.value = ''
  selected.value = null
  ids.value = []
  permissionSearch.value = ''
}
async function focusEditor() {
  await nextTick()
  editor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  editor.value
    ?.querySelector<HTMLInputElement>('input:not(:disabled)')
    ?.focus({ preventScroll: true })
}
function searchRoles() {
  q.page = 1
  void load()
}
function changePage(offset: number) {
  q.page += offset
  void load()
}
function create() {
  close()
  Object.assign(form, { name: '', roleKey: '', status: 'active' })
  mode.value = 'create'
  error.value = ''
  void focusEditor()
}
async function open(row: RoleRecord, m: 'edit' | 'permissions') {
  busy.value = true
  try {
    const r = await rolesApi.detail(row.id)
    selected.value = r
    Object.assign(form, { name: r.name, roleKey: r.roleKey, status: r.status })
    ids.value = [...r.permissionIds]
    mode.value = m
    permissionSearch.value = ''
    void focusEditor()
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function save() {
  if (busy.value || !writable.value) return
  busy.value = true
  error.value = ''
  try {
    if (mode.value === 'permissions' && selected.value)
      await rolesApi.assignPermissions(selected.value.id, ids.value)
    else if (mode.value === 'create') await rolesApi.create({ ...form, permissionIds: ids.value })
    else if (selected.value) await rolesApi.update(selected.value.id, form)
    close()
    await refreshAdminProfile()
    await load()
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
async function remove(row: RoleRecord) {
  if (!writable.value || row.system || row.members > 0 || !confirm(`确认删除角色“${row.name}”？`))
    return
  busy.value = true
  try {
    await rolesApi.remove(row.id)
    await load()
  } catch (e) {
    error.value = explain(e)
  } finally {
    busy.value = false
  }
}
onMounted(async () => {
  try {
    permissions.value = await rolesApi.permissions()
    await load()
  } catch (e) {
    error.value = explain(e)
  }
})
</script>
<template>
  <div class="admin-page">
    <header class="page-heading">
      <div>
        <span class="page-kicker">SECURITY</span>
        <h1>角色权限</h1>
        <p>维护角色、权限范围和员工授权边界。</p>
      </div>
      <button v-if="writable" class="primary-button" :disabled="busy" @click="create">
        <Plus :size="16" /> 新增角色
      </button>
    </header>
    <p v-if="error" class="demo-banner" role="alert">{{ error }}</p>
    <section v-if="!mode" class="panel list-panel">
      <form class="role-tools" @submit.prevent="searchRoles">
        <input v-model="q.keyword" placeholder="角色名称或标识" /><select v-model="q.status">
          <option value="">全部状态</option>
          <option value="active">启用</option>
          <option value="inactive">停用</option></select
        ><button class="secondary-button" :disabled="busy">查询</button>
      </form>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>角色</th>
              <th>标识</th>
              <th>权限数</th>
              <th>员工数</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.id">
              <td>{{ r.name }}{{ r.system ? '（内置）' : '' }}</td>
              <td>{{ r.roleKey }}</td>
              <td>{{ r.permissionIds.length }}</td>
              <td>{{ r.members }}</td>
              <td>{{ r.status === 'active' ? '启用' : '停用' }}</td>
              <td>
                <button
                  v-if="writable"
                  class="text-action"
                  :disabled="busy"
                  @click="open(r, 'edit')"
                >
                  编辑</button
                ><button class="text-action" @click="open(r, 'permissions')">权限</button
                ><button
                  v-if="writable"
                  class="text-action danger"
                  :disabled="busy || r.system || r.members > 0"
                  @click="remove(r)"
                >
                  删除
                </button>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6" class="empty-state">{{ loading ? '正在加载…' : '暂无角色' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span>共 {{ total }} 条</span
        ><button :disabled="q.page <= 1" @click="changePage(-1)">上一页</button
        ><span>{{ q.page }} / {{ pages }}</span
        ><button :disabled="q.page >= pages" @click="changePage(1)">下一页</button>
      </div>
    </section>
    <section v-if="mode" ref="editor" class="role-editor">
      <header class="editor-heading">
        <button type="button" class="back-button" :disabled="busy" @click="close">
          <ArrowLeft :size="17" />返回角色列表
        </button>
        <div class="editor-title">
          <span class="editor-icon"><ShieldCheck :size="25" /></span>
          <div>
            <h2>
              {{ mode === 'create' ? '创建新角色' : mode === 'edit' ? '编辑角色' : '配置角色权限' }}
            </h2>
            <p>
              {{
                selected
                  ? selected.name + ' · ' + selected.roleKey
                  : '为团队建立清晰的职责，让每位成员拥有合适的权限。'
              }}
            </p>
          </div>
          <span class="editor-tag">{{ mode === 'create' ? '新角色' : '角色管理' }}</span>
        </div>
      </header>
      <form @submit.prevent="save">
        <section v-if="mode !== 'permissions'" class="editor-card">
          <div class="section-title">
            <span class="section-number">01</span>
            <div>
              <h3>基本信息</h3>
              <p>使用简洁明确的名称，方便后续为员工分配角色。</p>
            </div>
          </div>
          <div class="basic-fields">
            <label class="form-field"
              ><span>角色名称 <b>*</b></span
              ><input
                v-model.trim="form.name"
                :disabled="busy"
                required
                maxlength="100"
                placeholder="例如：库存管理员"
              /><small>建议使用岗位或职责名称，最多 100 个字符。</small></label
            >
            <label class="form-field"
              ><span>角色标识 <b>*</b></span
              ><input
                v-model="form.roleKey"
                :disabled="mode === 'edit' || busy"
                required
                pattern="[a-z][a-z0-9_]{1,63}"
                placeholder="例如：inventory_manager"
                spellcheck="false"
                autocapitalize="none"
              /><small>2–64 位小写字母、数字或下划线，以字母开头，创建后不可修改。</small></label
            >
          </div>
          <div class="status-row">
            <div>
              <strong>启用角色</strong>
              <p>
                {{
                  form.status === 'active'
                    ? '启用后，可在员工管理中分配此角色。'
                    : '暂不启用，保留配置以便后续使用。'
                }}
              </p>
            </div>
            <button
              class="status-switch"
              :class="{ on: form.status === 'active' }"
              type="button"
              role="switch"
              aria-label="启用角色"
              :aria-checked="form.status === 'active'"
              :disabled="busy || selected?.system"
              @click="form.status = form.status === 'active' ? 'inactive' : 'active'"
            >
              <span /></button
            ><span class="status-label">{{ form.status === 'active' ? '已启用' : '已停用' }}</span>
          </div>
        </section>
        <section
          v-if="mode === 'permissions' || mode === 'create'"
          class="editor-card permissions-card"
        >
          <div class="section-title">
            <span class="section-number">{{ mode === 'create' ? '02' : '01' }}</span>
            <div>
              <h3>功能权限</h3>
              <p>按业务模块选择访问范围，也可以稍后再配置。</p>
            </div>
            <span class="selection-badge"
              >已选 <strong>{{ ids.length }}</strong> / {{ permissions.length }}</span
            >
          </div>
          <div class="permission-toolbar">
            <label class="permission-search"
              ><Search :size="16" /><input
                v-model="permissionSearch"
                placeholder="搜索模块或权限名称"
                aria-label="搜索权限"
            /></label>
            <div class="selection-actions">
              <button
                type="button"
                :disabled="busy || !editablePermissions"
                @click="toggle(Object.values(groups).flat(), true)"
              >
                全选{{ permissionSearch ? '结果' : '' }}</button
              ><span>·</span
              ><button
                type="button"
                :disabled="busy || !editablePermissions"
                @click="toggle(Object.values(groups).flat(), false)"
              >
                取消选择
              </button>
            </div>
          </div>
          <p v-if="selected?.system" class="permission-note">内置管理员权限受系统保护。</p>
          <div class="permission-modules">
            <fieldset
              v-for="(items, key) in groups"
              :key="key"
              :disabled="busy || !writable || selected?.system"
            >
              <legend class="sr-only">{{ moduleNames[key] || key }}</legend>
              <label class="module-heading"
                ><input
                  type="checkbox"
                  :checked="items.every((p) => ids.includes(p.id))"
                  :indeterminate="
                    items.some((p) => ids.includes(p.id)) && !items.every((p) => ids.includes(p.id))
                  "
                  @change="toggle(items, ($event.target as HTMLInputElement).checked)"
                /><span class="module-icon"><SlidersHorizontal :size="15" /></span
                ><strong>{{ moduleNames[key] || key }}</strong
                ><small
                  >{{ items.filter((p) => ids.includes(p.id)).length }} / {{ items.length }}</small
                ></label
              >
              <div class="permission-grid">
                <label
                  v-for="p in items"
                  :key="p.id"
                  class="permission-option"
                  :class="{ chosen: ids.includes(p.id) }"
                  ><input v-model="ids" type="checkbox" :value="p.id" /><span
                    >{{ p.name }}<small>{{ p.permissionKey }}</small></span
                  ></label
                >
              </div>
            </fieldset>
          </div>
          <div v-if="!Object.keys(groups).length" class="permission-empty">
            <Search :size="24" />
            <p>未找到相关权限，试试其他关键词。</p>
          </div>
          <p class="permission-note">
            <ShieldCheck :size="15" />员工拥有多个有效角色时，所选权限将合并生效。
          </p>
        </section>
        <footer class="editor-footer">
          <span class="footer-hint">{{
            ids.length ? `已配置 ${ids.length} 项功能权限` : '按需选择权限，创建后仍可调整'
          }}</span>
          <div class="footer-actions">
            <button type="button" class="secondary-button" :disabled="busy" @click="close">
              取消
            </button>
            <button
              v-if="writable && !(mode === 'permissions' && selected?.system)"
              class="primary-button"
              :disabled="busy"
            >
              <Check v-if="!busy" :size="16" />{{
                busy ? '正在保存…' : mode === 'create' ? '创建角色' : '保存更改'
              }}
            </button>
          </div>
        </footer>
      </form>
    </section>
  </div>
</template>
<style scoped>
.role-tools {
  display: flex;
  gap: 12px;
  padding: 20px;
  flex-wrap: wrap;
}
.role-tools input,
.role-tools select,
.role-editor input,
.role-editor select {
  padding: 10px;
  border: 1px solid #dce4de;
  border-radius: 6px;
}
.role-editor {
  margin: 8px auto 24px;
  max-width: 1080px;
  scroll-margin-top: 24px;
}
.role-editor form {
  display: grid;
  gap: 22px;
}
.editor-heading {
  margin-bottom: 24px;
}
.back-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  color: #6d8074;
  padding: 6px 0;
  margin-bottom: 18px;
  cursor: pointer;
}
.back-button:hover {
  color: #257251;
}
.editor-title {
  display: flex;
  align-items: center;
  gap: 16px;
}
.editor-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  color: #3d7b5c;
  background: linear-gradient(145deg, #e4f1e8, #f0f5eb);
  border: 1px solid #dae9df;
  flex-shrink: 0;
}
.editor-title h2 {
  margin: 0;
  font-size: 23px;
  letter-spacing: 0.02em;
  color: #253e30;
}
.editor-title p {
  margin: 8px 0 0;
  color: #829086;
  font-size: 13px;
  line-height: 1.7;
}
.editor-tag {
  margin-left: auto;
  background: #ecf3ed;
  color: #698573;
  padding: 7px 13px;
  border-radius: 20px;
  white-space: nowrap;
  font-size: 12px;
}
.editor-card {
  padding: 28px 30px;
  border: 1px solid #e5ece6;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 5px 22px #294b3410;
}
.section-title {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 24px;
}
.section-number {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  background: #eef5ef;
  color: #5a8669;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.section-title h3 {
  font-size: 16px;
  margin: 2px 0 7px;
  color: #304c3b;
}
.section-title p {
  margin: 0;
  color: #8b978e;
  font-size: 12px;
  line-height: 1.7;
}
.basic-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.form-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
  color: #526759;
  font-size: 13px;
}
.form-field b {
  color: #bf8073;
  font-weight: 400;
}
.form-field small {
  font-size: 11px;
  line-height: 1.7;
  color: #909c93;
}
.role-editor .form-field input {
  width: 100%;
  box-sizing: border-box;
  min-height: 44px;
  border: 1px solid #e0e8e1;
  background: #fbfcfa;
  border-radius: 9px;
  padding: 11px 13px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
  color: #304b3b;
}
.role-editor input::placeholder {
  color: #a1ada3;
}
.role-editor input:focus-visible {
  outline: 0;
  border-color: #83af93;
  box-shadow: 0 0 0 3px #e9f3ec;
}
.role-editor input:disabled {
  background: #f2f5f1;
  color: #859389;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 12px;
  border-top: 1px solid #edf1eb;
  margin-top: 24px;
  padding-top: 22px;
}
.status-row > div {
  flex: 1;
}
.status-row strong {
  font-size: 13px;
  color: #4e6656;
}
.status-row p {
  font-size: 12px;
  color: #929e95;
  line-height: 1.6;
  margin: 6px 0 0;
}
.status-switch {
  height: 24px;
  width: 43px;
  border: 0;
  border-radius: 20px;
  background: #c7d2c9;
  padding: 3px;
  cursor: pointer;
  transition: background 0.2s;
  flex-shrink: 0;
}
.status-switch span {
  display: block;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 4px #27462e22;
  transition: transform 0.2s;
}
.status-switch.on {
  background: #6b9b7a;
}
.status-switch.on span {
  transform: translateX(19px);
}
.status-label {
  font-size: 12px;
  color: #6c8975;
}
.selection-badge {
  margin-left: auto;
  white-space: nowrap;
  padding: 7px 12px;
  border-radius: 20px;
  background: #eff6ef;
  color: #829486;
  font-size: 12px;
}
.selection-badge strong {
  color: #3d7b55;
}
.permission-toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 20px;
}
.permission-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  background: #f8faf7;
  border: 1px solid #e5ebe2;
  border-radius: 9px;
  max-width: 340px;
  flex: 1;
  color: #90a18f;
}
.role-editor .permission-search input {
  padding: 11px 0;
  background: transparent;
  border: 0;
  min-width: 0;
  width: 100%;
  font-size: 12px;
}
.permission-search:focus-within {
  border-color: #83af93;
  box-shadow: 0 0 0 3px #e9f3ec;
}
.role-editor .permission-search input:focus-visible {
  box-shadow: none;
}
.selection-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  color: #c4cfc3;
  white-space: nowrap;
}
.selection-actions button {
  background: transparent;
  border: 0;
  color: #73917b;
  font-size: 12px;
  padding: 6px 0;
  cursor: pointer;
}
.selection-actions button:hover {
  color: #257251;
}
.permission-modules {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.role-editor .permission-modules fieldset {
  padding: 0;
  margin: 0;
  min-width: 0;
  border: 1px solid #e7ede5;
  border-radius: 12px;
  background: #fdfefc;
  overflow: hidden;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
.module-heading {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 13px 15px;
  background: #f3f7f0;
  border-bottom: 1px solid #eaf0e5;
  cursor: pointer;
}
.module-icon {
  display: flex;
  color: #849d78;
}
.module-heading strong {
  font-size: 13px;
  color: #576e4e;
  font-weight: 600;
}
.module-heading small {
  margin-left: auto;
  font-size: 11px;
  color: #94a18c;
}
.role-editor input[type='checkbox'] {
  width: 15px;
  height: 15px;
  accent-color: #6e9971;
  flex-shrink: 0;
  margin: 0;
  cursor: pointer;
}
.role-editor .permission-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 5px;
  padding: 10px;
}
.role-editor .permission-option {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 10px 8px;
  border-radius: 7px;
  cursor: pointer;
  transition: background 0.15s;
  min-width: 0;
}
.permission-option:hover {
  background: #f1f6ed;
}
.permission-option.chosen {
  background: #f1f6ee;
}
.permission-option > span {
  font-size: 12px;
  color: #61705a;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.permission-option small {
  display: block;
  font-size: 10px;
  color: #a0aa96;
  margin-top: 4px;
}
.permission-note {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  line-height: 1.7;
  color: #96a18e;
  margin: 18px 0 0;
}
.permission-empty {
  text-align: center;
  color: #9aa68e;
  padding: 28px;
  font-size: 12px;
}
.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border: 1px solid #e4ece3;
  border-radius: 14px;
  background: #f9fbf7;
  padding: 18px 24px;
}
.footer-hint {
  font-size: 12px;
  color: #96a18f;
}
.footer-actions {
  display: flex;
  gap: 12px;
}
.footer-actions button,
.page-heading > .primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border-radius: 9px;
  min-height: 40px;
}
.footer-actions .primary-button {
  background: #598969;
  box-shadow: 0 3px 8px #5989691a;
}
.footer-actions .primary-button:hover {
  background: #477756;
}
.role-editor button:focus-visible {
  outline: 2px solid #669276;
  outline-offset: 3px;
}
.role-editor button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
@media (max-width: 800px) {
  .permission-modules {
    grid-template-columns: 1fr;
  }
  .editor-card {
    padding: 22px;
  }
  .basic-fields {
    gap: 18px;
  }
  .editor-title h2 {
    font-size: 20px;
  }
}
@media (max-width: 540px) {
  .basic-fields {
    grid-template-columns: 1fr;
  }
  .editor-card {
    padding: 20px 16px;
    border-radius: 14px;
  }
  .editor-tag {
    display: none;
  }
  .editor-icon {
    height: 46px;
    width: 46px;
    border-radius: 14px;
  }
  .editor-title {
    gap: 12px;
  }
  .selection-badge {
    padding: 6px 8px;
  }
  .section-title {
    gap: 9px;
  }
  .section-title p {
    max-width: 180px;
  }
  .permission-toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }
  .permission-search {
    max-width: none;
    flex: auto;
  }
  .selection-actions {
    margin-left: 0;
    justify-content: flex-end;
  }
  .editor-footer {
    flex-direction: column;
    align-items: stretch;
    padding: 16px;
  }
  .footer-actions {
    justify-content: flex-end;
  }
  .footer-hint {
    font-size: 11px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .role-editor * {
    transition: none !important;
    scroll-behavior: auto !important;
  }
}
.danger {
  color: #b94343;
}
</style>
