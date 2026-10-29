<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { employeesApi, type EmployeeInput, type EmployeeRecord, type EmployeeOptions, type OrganizationOption } from '../api/employees'

const rows = ref<EmployeeRecord[]>([]), options = ref<EmployeeOptions | null>(null)
const query = reactive({ page: 1, size: 10, keyword: '', status: '', storeId: '', roleId: '' })
const total = ref(0), pages = ref(1), loading = ref(false), busy = ref(false)
const error = ref(''), message = ref(''), selected = ref<EmployeeRecord | null>(null)
const mode = ref<'create' | 'edit' | 'detail' | 'roles' | 'password' | ''>('')
const editor = ref<HTMLElement>(), password = ref(''), roleIds = ref<string[]>([])
const blank = (): EmployeeInput => ({ username: '', name: '', phone: '', storeId: null, departmentId: null, positionId: null, status: 'active' })
const form = reactive<EmployeeInput>(blank())
const title = computed(() => ({ create: '新增员工', edit: '编辑员工', detail: '员工详情', roles: '分配角色', password: '重置密码', '': '' })[mode.value])
let version = 0
const explain = (e: unknown) => e instanceof Error ? e.message : '操作失败，请重试'
function label(list: OrganizationOption[] | undefined, id: string | null) { return id ? list?.find(o => o.id === id)?.name || `已失效组织（${id}）` : '未设置' }
async function load() {
  const current = ++version
  loading.value = true
  try {
    const result = await employeesApi.list({ ...query, keyword: query.keyword.trim() })
    if (current !== version) return
    rows.value = result.list; total.value = result.total; pages.value = Math.max(1, result.pages)
    if (query.page > pages.value) { query.page = pages.value; await load() }
  } catch (e) { if (current === version) error.value = explain(e) }
  finally { if (current === version) loading.value = false }
}
function search() { if (busy.value || loading.value) return; query.page = 1; error.value = ''; void load() }
function page(delta: number) { if (busy.value || loading.value) return; query.page = Math.max(1, Math.min(pages.value, query.page + delta)); void load() }
function close() { mode.value = ''; selected.value = null; password.value = ''; roleIds.value = [] }
async function reveal() { await nextTick(); editor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }
function create() { if (busy.value) return; close(); Object.assign(form, blank()); mode.value = 'create'; error.value = ''; void reveal() }
async function open(row: EmployeeRecord, target: typeof mode.value) {
  if (busy.value) return
  busy.value = true; error.value = ''; close()
  try {
    const result = await employeesApi.detail(row.id)
    selected.value = result
    Object.assign(form, { username: result.username, name: result.name || '', phone: result.phone || '', storeId: result.storeId, departmentId: result.departmentId, positionId: result.positionId, status: result.status })
    roleIds.value = [...result.roleIds]; mode.value = target; await reveal()
  } catch (e) { error.value = explain(e) } finally { busy.value = false }
}
async function run(operation: () => Promise<unknown>, success: string) {
  if (busy.value) return
  busy.value = true; error.value = ''; message.value = ''
  try { await operation(); close(); message.value = success; await load() }
  catch (e) { error.value = explain(e) }
  finally { busy.value = false }
}
function validPassword() {
  if (password.value.length < 12 || new TextEncoder().encode(password.value).length > 72) { error.value = '密码至少12个字符，最多72个 UTF-8 字节'; return false }
  return true
}
function save() {
  if (busy.value) return
  if (mode.value === 'password') { if (selected.value && validPassword()) void run(() => employeesApi.resetPassword(selected.value!.id, password.value), '密码已重置'); return }
  if (mode.value === 'roles') { if (selected.value) void run(() => employeesApi.roles(selected.value!.id, [...roleIds.value]), '角色已更新，授权即时生效'); return }
  if (!/^[a-zA-Z0-9_.-]{3,64}$/.test(form.username) || !form.name.trim() || !/^1[3-9][0-9]{9}$/.test(form.phone)) { error.value = '请填写有效账号、姓名和11位手机号'; return }
  const data = { ...form, name: form.name.trim() }
  if (mode.value === 'create') { if (validPassword()) void run(() => employeesApi.createEmployee(data, password.value), '员工已创建；请按需分配角色后登录管理台') }
  else if (selected.value) void run(() => employeesApi.update(selected.value!.id, data), '员工资料已保存')
}
function toggle(row: EmployeeRecord) {
  const status = row.status === 'active' ? 'inactive' : 'active'
  if (window.confirm(`确认${status === 'active' ? '启用' : '停用'}员工「${row.name || row.username}」？`)) void run(() => employeesApi.status(row.id, status), '员工状态已更新')
}
function remove(row: EmployeeRecord) {
  if (window.confirm(`确认删除员工「${row.name || row.username}」？账号将无法登录，历史业务记录保留。`)) void run(() => employeesApi.deleteEmployee(row.id), '员工已删除')
}
async function initialize() {
  loading.value = true; error.value = ''
  const optionResult = await Promise.allSettled([employeesApi.options(), load()])
  if (optionResult[0].status === 'fulfilled') options.value = optionResult[0].value
  else error.value = explain(optionResult[0].reason)
  loading.value = false
}
onMounted(initialize)
onUnmounted(() => { version++; password.value = '' })
</script>

<template>
  <div class="admin-page">
    <header class="page-heading"><div><span class="page-kicker">ORGANIZATION</span><h1>员工管理</h1><p>维护员工档案、组织归属、登录状态和角色授权。</p></div><button class="primary-button" :disabled="busy || loading || options?.canWrite === false" :title="options?.canWrite === false ? '当前账号没有 employee:write 权限' : ''" @click="create">新增员工</button></header>
    <p v-if="error" class="demo-banner" role="alert">{{ error }} <button class="text-action" :disabled="busy || loading" @click="initialize">重新加载</button></p>
    <p v-if="message" class="demo-banner" role="status">{{ message }}</p>
    <section class="panel list-panel" :aria-busy="loading">
      <form class="employee-tools" @submit.prevent="search">
        <label>账号 / 姓名<input v-model="query.keyword" maxlength="100" placeholder="搜索账号或姓名" /></label>
        <label>状态<select v-model="query.status"><option value="">全部状态</option><option value="active">启用</option><option value="inactive">停用</option></select></label>
        <label>所属门店<select v-model="query.storeId"><option value="">全部门店</option><option v-for="o in options?.stores" :key="o.id" :value="o.id">{{ o.name }}{{ o.status !== 'active' ? '（停用）' : '' }}</option></select></label>
        <label>角色<select v-model="query.roleId"><option value="">全部角色</option><option v-for="o in options?.roles" :key="o.id" :value="o.id">{{ o.name }}</option></select></label>
        <button class="secondary-button" :disabled="busy || loading || !options">查询</button>
      </form>
      <div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>账号 / 姓名</th><th>手机号</th><th>门店 / 部门 / 岗位</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="row in rows" :key="row.id"><td>{{ row.username }}<small>{{ row.name || '未设置姓名' }}</small></td><td>{{ row.phone || '未设置' }}</td><td>{{ label(options?.stores,row.storeId) }}<small>{{ label(options?.departments,row.departmentId) }} / {{ label(options?.positions,row.positionId) }}</small></td><td>{{ row.roleIds.map(id => label(options?.roles,id)).join('、') || '未分配' }}</td><td><span class="status-tag" :class="row.status === 'active' ? 'enabled' : 'disabled'">{{ row.status === 'active' ? '启用' : '停用' }}</span></td>
          <td><div class="row-actions"><button class="text-action" :disabled="busy" @click="open(row,'detail')">详情</button><button class="text-action" :disabled="busy || options?.canWrite === false" @click="open(row,'edit')">编辑</button><button class="text-action" :disabled="busy || options?.canWrite === false" @click="toggle(row)">{{ row.status === 'active' ? '停用' : '启用' }}</button><button class="text-action danger" :disabled="busy || options?.canWrite === false || row.id === options?.currentEmployeeId" @click="remove(row)">删除</button><button class="text-action" :disabled="busy || options?.canManageRoles === false" @click="open(row,'roles')">角色</button><button class="text-action" :disabled="busy || options?.canManageRoles === false" @click="open(row,'password')">重置密码</button></div></td>
        </tr><tr v-if="!rows.length"><td colspan="6" class="empty-state">{{ loading ? '正在加载…' : error ? '加载失败，请重试' : '暂无匹配的员工' }}</td></tr></tbody></table></div>
      <div class="pagination"><span>共 {{ total }} 条</span><div><select v-model.number="query.size" aria-label="每页条数" :disabled="busy || loading" @change="search"><option :value="10">10条 / 页</option><option :value="20">20条 / 页</option><option :value="50">50条 / 页</option></select><button :disabled="busy || loading || query.page <= 1" @click="page(-1)">上一页</button><span>{{ query.page }} / {{ pages }}</span><button :disabled="busy || loading || query.page >= pages" @click="page(1)">下一页</button></div></div>
    </section>
    <section v-if="mode" ref="editor" class="panel employee-editor">
      <div class="panel-heading"><h2>{{ title }}{{ selected ? ` · ${selected.username}` : '' }}</h2><button class="text-action" :disabled="busy" @click="close">关闭</button></div>
      <template v-if="mode === 'detail' && selected"><dl class="detail-grid"><div><dt>员工ID</dt><dd>{{ selected.id }}</dd></div><div><dt>姓名 / 账号</dt><dd>{{ selected.name || '—' }} / {{ selected.username }}</dd></div><div><dt>手机号</dt><dd>{{ selected.phone || '—' }}</dd></div><div><dt>状态</dt><dd>{{ selected.status === 'active' ? '启用' : '停用' }}</dd></div><div><dt>门店 / 部门 / 岗位</dt><dd>{{ label(options?.stores,selected.storeId) }} / {{ label(options?.departments,selected.departmentId) }} / {{ label(options?.positions,selected.positionId) }}</dd></div><div><dt>角色</dt><dd>{{ selected.roleIds.map(id => label(options?.roles,id)).join('、') || '未分配' }}</dd></div><div><dt>创建时间</dt><dd>{{ selected.createTime }}</dd></div><div><dt>更新时间</dt><dd>{{ selected.updateTime }}</dd></div></dl></template>
      <form v-else @submit.prevent="save"><fieldset :disabled="busy" class="employee-fields">
        <template v-if="mode === 'edit' || mode === 'create'">
          <label>登录账号<input v-model="form.username" required minlength="3" maxlength="64" pattern="[a-zA-Z0-9_.\-]{3,64}" autocomplete="off" /><small>3–64位字母、数字、下划线、点或短横线</small></label>
          <label>姓名<input v-model="form.name" required maxlength="100" /></label><label>手机号<input v-model="form.phone" required pattern="1[3-9][0-9]{9}" maxlength="11" inputmode="tel" /></label>
          <label>状态<select v-model="form.status"><option value="active">启用</option><option value="inactive">停用</option></select></label>
          <label>门店<select v-model="form.storeId"><option :value="null">未设置</option><option v-for="o in options?.stores" :key="o.id" :value="o.id" :disabled="o.status !== 'active'">{{ o.name }}{{ o.status !== 'active' ? '（停用）' : '' }}</option><option v-if="form.storeId && !options?.stores.some(o => o.id === form.storeId)" :value="form.storeId" disabled>原门店已删除，请重新选择</option></select></label>
          <label>部门<select v-model="form.departmentId"><option :value="null">未设置</option><option v-for="o in options?.departments" :key="o.id" :value="o.id" :disabled="o.status !== 'active'">{{ o.name }}{{ o.status !== 'active' ? '（停用）' : '' }}</option></select></label>
          <label>岗位<select v-model="form.positionId"><option :value="null">未设置</option><option v-for="o in options?.positions" :key="o.id" :value="o.id" :disabled="o.status !== 'active'">{{ o.name }}{{ o.status !== 'active' ? '（停用）' : '' }}</option></select></label>
        </template>
        <label v-if="mode === 'create' || mode === 'password'">{{ mode === 'create' ? '初始密码' : '新密码' }}<input v-model="password" type="password" required minlength="12" maxlength="72" autocomplete="new-password" /><small>至少12个字符，最多72个 UTF-8 字节</small></label>
        <div v-if="mode === 'roles'" class="role-picker"><p>未分配有效角色的员工无法登录管理台；保留最后一个管理员。</p><label v-for="o in options?.roles" :key="o.id" class="checkbox-label"><input v-model="roleIds" type="checkbox" :value="o.id" :disabled="o.status !== 'active' && !roleIds.includes(o.id)" />{{ o.name }}{{ o.status !== 'active' ? '（停用，请取消勾选）' : '' }}</label><p v-if="!options?.roles.length">暂无可分配角色，请先维护角色。</p></div>
      </fieldset><button class="primary-button" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button></form>
      <p class="employee-tip">停用、删除、组织及角色调整受最后一个管理员和未完结工单校验保护；失败时不会保存变更。</p>
    </section>
  </div>
</template>
<style scoped>
.employee-tools,.row-actions{display:flex;gap:12px;flex-wrap:wrap;align-items:end}.employee-tools{padding:20px}.employee-tools label,.employee-fields label{display:grid;gap:7px;font-size:13px}.employee-tools input,.employee-tools select,.employee-fields input,.employee-fields select{border:1px solid #dce4de;border-radius:6px;padding:10px;font:inherit;min-width:0;background:white;box-sizing:border-box;width:100%}.employee-editor{padding:24px;margin-top:24px;scroll-margin-top:20px}.employee-fields{border:0;padding:0;margin:18px 0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.row-actions{min-width:260px;align-items:center}.danger{color:#b94343}.enabled{color:#26724f;background:#eaf4ed}.disabled{color:#777;background:#eee}small{display:block;color:#75837b;margin-top:5px}.detail-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.detail-grid dd{margin:8px 0;overflow-wrap:anywhere}.detail-grid dt,.employee-tip{color:#75837b;font-size:13px}.role-picker{grid-column:1/-1}.employee-fields .checkbox-label{display:flex;align-items:center;margin:12px 0}.checkbox-label input{width:auto}@media(max-width:700px){.employee-fields,.detail-grid{grid-template-columns:1fr}.employee-editor{padding:16px}}
</style>
