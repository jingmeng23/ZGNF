<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ArrowRight, Building2, Check, LockKeyhole, UserRound, UserRoundPlus, X } from 'lucide-vue-next'
import { employeesApi, type EmployeeInput, type EmployeeRecord, type EmployeeOptions, type OrganizationOption } from '../api/employees'

const rows = ref<EmployeeRecord[]>([]), options = ref<EmployeeOptions | null>(null)
const query = reactive({ page: 1, size: 10, keyword: '', status: '', roleId: '' })
const total = ref(0), pages = ref(1), loading = ref(false), busy = ref(false)
const error = ref(''), message = ref(''), selected = ref<EmployeeRecord | null>(null)
const mode = ref<'create' | 'edit' | 'detail' | 'roles' | 'password' | ''>('')
const editor = ref<HTMLElement>(), password = ref(''), roleIds = ref<string[]>([])
const blank = (): EmployeeInput => ({ username: '', name: '', phone: '', departmentId: null, positionId: null, status: 'active' })
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
    Object.assign(form, { username: result.username, name: result.name || '', phone: result.phone || '', departmentId: result.departmentId, positionId: result.positionId, status: result.status })
    if (result.phoneUnavailable && target === 'edit') {
      form.phone = ''
      message.value = '历史手机号无法解密，请重新填写手机号后保存。'
    }
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
  <div class="admin-page employee-page">
    <header class="page-heading"><div><span class="page-kicker">ORGANIZATION</span><h1>员工管理</h1><p>维护员工档案、组织归属、登录状态和角色授权。</p></div><button class="primary-button" :disabled="busy || loading || options?.canWrite === false" :title="options?.canWrite === false ? '当前账号没有 employee:write 权限' : ''" @click="create">新增员工</button></header>
    <p v-if="error" class="demo-banner" role="alert">{{ error }} <button class="text-action" :disabled="busy || loading" @click="initialize">重新加载</button></p>
    <p v-if="message" class="demo-banner" role="status">{{ message }}</p>
    <section class="panel list-panel" :aria-busy="loading">
      <form class="employee-tools" @submit.prevent="search">
        <label>账号 / 姓名<input v-model="query.keyword" maxlength="100" placeholder="搜索账号或姓名" /></label>
        <label>状态<select v-model="query.status"><option value="">全部状态</option><option value="active">启用</option><option value="inactive">停用</option></select></label>
        <label>角色<select v-model="query.roleId"><option value="">全部角色</option><option v-for="o in options?.roles" :key="o.id" :value="o.id">{{ o.name }}</option></select></label>
        <button class="secondary-button" :disabled="busy || loading || !options">查询</button>
      </form>
      <div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>账号 / 姓名</th><th>手机号</th><th>部门 / 岗位</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="row in rows" :key="row.id"><td>{{ row.username }}<small>{{ row.name || '未设置姓名' }}</small></td><td>{{ row.phone || '未设置' }}</td><td><small>{{ label(options?.departments,row.departmentId) }} / {{ label(options?.positions,row.positionId) }}</small></td><td>{{ row.roleIds.map(id => label(options?.roles,id)).join('、') || '未分配' }}</td><td><span class="status-tag" :class="row.status === 'active' ? 'enabled' : 'disabled'">{{ row.status === 'active' ? '启用' : '停用' }}</span></td>
          <td><div class="row-actions"><button class="text-action" :disabled="busy" @click="open(row,'detail')">详情</button><button class="text-action" :disabled="busy || options?.canWrite === false" @click="open(row,'edit')">编辑</button><button class="text-action" :disabled="busy || options?.canWrite === false" @click="toggle(row)">{{ row.status === 'active' ? '停用' : '启用' }}</button><button class="text-action danger" :disabled="busy || options?.canWrite === false || row.id === options?.currentEmployeeId" @click="remove(row)">删除</button><button class="text-action" :disabled="busy || options?.canManageRoles === false" @click="open(row,'roles')">角色</button><button class="text-action" :disabled="busy || options?.canManageRoles === false" @click="open(row,'password')">重置密码</button></div></td>
        </tr><tr v-if="!rows.length"><td colspan="6" class="empty-state">{{ loading ? '正在加载…' : error ? '加载失败，请重试' : '暂无匹配的员工' }}</td></tr></tbody></table></div>
      <div class="pagination"><span>共 {{ total }} 条</span><div><select v-model.number="query.size" aria-label="每页条数" :disabled="busy || loading" @change="search"><option :value="10">10条 / 页</option><option :value="20">20条 / 页</option><option :value="50">50条 / 页</option></select><button :disabled="busy || loading || query.page <= 1" @click="page(-1)">上一页</button><span>{{ query.page }} / {{ pages }}</span><button :disabled="busy || loading || query.page >= pages" @click="page(1)">下一页</button></div></div>
    </section>
    <section v-if="mode" ref="editor" class="panel employee-editor" :class="{ 'profile-editor': mode === 'create' || mode === 'edit' }" aria-labelledby="employee-editor-title">
      <div class="employee-editor-heading">
        <span class="editor-heading-icon" aria-hidden="true"><UserRoundPlus v-if="mode === 'create'" :size="23" /><UserRound v-else :size="23" /></span>
        <div><h2 id="employee-editor-title">{{ title }}{{ selected ? ` · ${selected.username}` : '' }}</h2><p>{{ mode === 'create' ? '填写员工资料，创建新的管理账号。' : mode === 'edit' ? '更新员工资料与组织信息。' : '查看和维护员工账号信息。' }}</p></div>
        <button class="editor-close" type="button" :disabled="busy" aria-label="关闭员工表单" @click="close"><X :size="19" /></button>
      </div>
      <template v-if="mode === 'detail' && selected"><dl class="detail-grid"><div><dt>员工ID</dt><dd>{{ selected.id }}</dd></div><div><dt>姓名 / 账号</dt><dd>{{ selected.name || '—' }} / {{ selected.username }}</dd></div><div><dt>手机号</dt><dd>{{ selected.phone || '—' }}</dd></div><div><dt>状态</dt><dd>{{ selected.status === 'active' ? '启用' : '停用' }}</dd></div><div><dt>部门 / 岗位</dt><dd>{{ label(options?.departments,selected.departmentId) }} / {{ label(options?.positions,selected.positionId) }}</dd></div><div><dt>角色</dt><dd>{{ selected.roleIds.map(id => label(options?.roles,id)).join('、') || '未分配' }}</dd></div><div><dt>创建时间</dt><dd>{{ selected.createTime }}</dd></div><div><dt>更新时间</dt><dd>{{ selected.updateTime }}</dd></div></dl></template>
      <form v-else-if="mode === 'create' || mode === 'edit'" class="employee-profile-form" @submit.prevent="save">
        <div class="employee-form-layout">
          <fieldset :disabled="busy" class="employee-fields profile-fields" aria-label="员工资料">
            <section class="form-section">
              <div class="form-section-heading"><span class="form-section-icon"><UserRound :size="17" /></span><div><h3>基本信息</h3><p>完善账号及联系方式</p></div><span class="required-note"><i>*</i> 为必填项</span></div>
              <div class="profile-field-grid">
                <label><span>登录账号 <i>*</i></span><input v-model="form.username" placeholder="例如：zhang.san" required minlength="3" maxlength="64" pattern="[a-zA-Z0-9_.\-]{3,64}" autocomplete="off" /><small>3–64 位字母、数字、下划线、点或短横线</small></label>
                <label><span>员工姓名 <i>*</i></span><input v-model="form.name" placeholder="请输入真实姓名" required maxlength="100" autocomplete="name" /></label>
                <label><span>手机号码 <i>*</i></span><input v-model="form.phone" placeholder="请输入 11 位手机号码" required pattern="1[3-9][0-9]{9}" maxlength="11" inputmode="tel" autocomplete="tel" /></label>
                <label><span>账号状态 <i>*</i></span><select v-model="form.status"><option value="active">启用</option><option value="inactive">停用</option></select><small>停用后，该账号将无法登录管理台</small></label>
              </div>
            </section>
            <section class="form-section">
              <div class="form-section-heading"><span class="form-section-icon"><Building2 :size="17" /></span><div><h3>组织信息</h3><p>按需设置员工的部门和岗位</p></div><span class="optional-note">选填</span></div>
              <div class="profile-field-grid">
                <label><span>所属部门</span><select v-model="form.departmentId"><option :value="null">暂不设置部门</option><option v-for="o in options?.departments" :key="o.id" :value="o.id" :disabled="o.status !== 'active'">{{ o.name }}{{ o.status !== 'active' ? '（停用）' : '' }}</option></select></label>
                <label><span>员工岗位</span><select v-model="form.positionId"><option :value="null">暂不设置岗位</option><option v-for="o in options?.positions" :key="o.id" :value="o.id" :disabled="o.status !== 'active'">{{ o.name }}{{ o.status !== 'active' ? '（停用）' : '' }}</option></select></label>
              </div>
            </section>
            <section v-if="mode === 'create'" class="form-section">
              <div class="form-section-heading"><span class="form-section-icon"><LockKeyhole :size="17" /></span><div><h3>登录设置</h3><p>设置员工首次登录使用的密码</p></div></div>
              <div class="profile-field-grid">
                <label class="password-field"><span>初始密码 <i>*</i></span><input v-model="password" placeholder="请设置至少 12 个字符的密码" type="password" required minlength="12" maxlength="72" autocomplete="new-password" /><small>至少 12 个字符，最多 72 个 UTF-8 字节</small></label>
              </div>
            </section>
          </fieldset>
          <aside class="employee-form-aside" aria-label="员工信息预览">
            <div class="employee-preview">
              <span class="preview-caption">员工档案预览</span>
              <div class="preview-avatar" aria-hidden="true">{{ form.name.trim().slice(0, 1) || '员' }}</div>
              <strong class="preview-name">{{ form.name.trim() || '新员工' }}</strong>
              <span class="preview-username">{{ form.username || '填写账号后在此显示' }}</span>
              <span class="preview-status" :class="{ inactive: form.status === 'inactive' }"><span></span>{{ form.status === 'active' ? '启用状态' : '停用状态' }}</span>
              <dl><div><dt>所属部门</dt><dd>{{ label(options?.departments, form.departmentId) }}</dd></div><div><dt>员工岗位</dt><dd>{{ label(options?.positions, form.positionId) }}</dd></div></dl>
            </div>
            <div v-if="mode === 'create'" class="employee-next-step"><span class="next-step-icon"><ArrowRight :size="17" /></span><div><h4>创建后，记得分配角色</h4><p>在员工列表中点击「角色」，完成授权后即可登录管理台。</p></div></div>
          </aside>
        </div>
        <div class="employee-form-footer"><span><Check :size="15" />{{ mode === 'create' ? '资料填写完成后，即可创建账号' : '保存后员工资料即时更新' }}</span><div><button type="button" class="secondary-button" :disabled="busy" @click="close">取消</button><button class="primary-button employee-submit" :disabled="busy"><UserRoundPlus v-if="mode === 'create'" :size="16" /><Check v-else :size="16" />{{ busy ? '保存中…' : mode === 'create' ? '创建员工' : '保存' }}</button></div></div>
      </form>
      <form v-else @submit.prevent="save"><fieldset :disabled="busy" class="employee-fields">
        <label v-if="mode === 'password'">新密码<input v-model="password" type="password" required minlength="12" maxlength="72" autocomplete="new-password" /><small>至少12个字符，最多72个 UTF-8 字节</small></label>
        <div v-if="mode === 'roles'" class="role-picker"><p>未分配有效角色的员工无法登录管理台；保留最后一个管理员。</p><label v-for="o in options?.roles" :key="o.id" class="checkbox-label"><input v-model="roleIds" type="checkbox" :value="o.id" :disabled="o.status !== 'active' && !roleIds.includes(o.id)" />{{ o.name }}{{ o.status !== 'active' ? '（停用，请取消勾选）' : '' }}</label><p v-if="!options?.roles.length">暂无可分配角色，请先维护角色。</p></div>
      </fieldset><button class="primary-button" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button></form>
      <p v-if="mode !== 'create' && mode !== 'edit'" class="employee-tip">停用、删除、组织及角色调整受最后一个管理员和未完结工单校验保护；失败时不会保存变更。</p>
    </section>
  </div>
</template>
<style scoped>
.employee-page{display:flex;flex-direction:column;gap:20px}
.employee-page>.page-heading{order:0;margin-bottom:0}
.employee-page>.demo-banner{order:1;margin:0}
.employee-page>.employee-editor{order:2}
.employee-page>.list-panel{order:3}
.employee-tools,.row-actions{display:flex;gap:12px;flex-wrap:wrap;align-items:end}
.employee-tools{padding:20px}
.employee-tools label,.employee-fields label{display:grid;gap:8px;font-size:13px;align-content:start;min-width:0}
.employee-tools input,.employee-tools select,.employee-fields input,.employee-fields select{border:1px solid #dce4de;border-radius:8px;padding:11px 12px;font:inherit;min-width:0;background:white;box-sizing:border-box;width:100%;color:#243c2e;transition:border-color .18s,box-shadow .18s}
.employee-fields input,.employee-fields select{min-height:44px}
.employee-fields input::placeholder{color:#9ba69f;font-size:12px}
.employee-fields input:focus-visible,.employee-fields select:focus-visible{outline:none;border-color:#398563;box-shadow:0 0 0 3px #25725112}
.employee-fields input:disabled,.employee-fields select:disabled{background:#f5f7f5;color:#87928a}
.employee-editor{padding:24px;scroll-margin-top:20px;overflow:hidden}
.employee-editor-heading{display:flex;align-items:center;gap:13px;padding-bottom:22px;border-bottom:1px solid #edf0ed}
.editor-heading-icon{display:grid;place-items:center;width:46px;height:46px;flex-shrink:0;color:#287553;background:#edf5ef;border:1px solid #e1eee4;border-radius:13px}
.employee-editor-heading h2{margin:0;font-size:18px;font-weight:650;letter-spacing:.2px;overflow-wrap:anywhere}
.employee-editor-heading p{margin:6px 0 0;color:#819087;font-size:12px;line-height:1.5}
.editor-close{margin-left:auto;align-self:flex-start;display:grid;place-items:center;background:#fff;color:#8a978f;border:1px solid #e8ede8;border-radius:8px;width:32px;height:32px;flex-shrink:0;cursor:pointer}
.editor-close:hover{background:#f3f7f4;color:#257251}
.editor-close:focus-visible{outline:2px solid #398563;outline-offset:3px}
.employee-fields{border:0;min-width:0;padding:0;margin:22px 0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}
.employee-form-layout{display:grid;grid-template-columns:minmax(0,1fr) 260px;gap:32px;padding:26px 0 30px}
.profile-fields{display:block;margin:0}
.form-section+.form-section{border-top:1px solid #edf0ed;margin-top:26px;padding-top:24px}
.form-section-heading{display:flex;align-items:center;gap:10px;margin-bottom:20px}
.form-section-icon{display:grid;place-items:center;width:34px;height:34px;flex-shrink:0;background:#f2f6f3;color:#57806a;border-radius:9px}
.form-section-heading h3{margin:0;font-size:14px;font-weight:600;color:#2b4234}
.form-section-heading p{margin:4px 0 0;color:#8b978e;font-size:11px;line-height:1.5}
.required-note,.optional-note{margin-left:auto;font-size:11px;color:#93a096;white-space:nowrap}
.optional-note{padding:3px 8px;border:1px solid #e8ede8;border-radius:5px}
.employee-fields i,.required-note i{color:#c57861;font-style:normal;font-size:12px;margin-left:3px}
.profile-field-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px 22px}
.profile-field-grid label>span{font-size:12px;font-weight:500;color:#526458}
.employee-fields small{margin:0;font-size:11px;line-height:1.6;color:#94a097}
.password-field{grid-column:1/-1}
.employee-form-aside{min-width:0;border-left:1px solid #edf0ed;padding-left:28px}
.employee-preview{text-align:center;padding:20px 17px 0;background:linear-gradient(160deg,#f0f7f1,#fafcf9 68%);border:1px solid #e6eee6;border-radius:12px;overflow:hidden}
.preview-caption{display:block;text-align:left;font-size:10px;letter-spacing:1px;color:#8c9c90}
.preview-avatar{display:grid;place-items:center;width:64px;height:64px;margin:24px auto 14px;background:#fff;border:1px solid #deeadf;box-shadow:0 4px 14px #234a3010;border-radius:20px;font-size:26px;color:#367955;font-weight:600}
.preview-name{display:block;font-size:17px;font-weight:600;color:#2d4836;overflow-wrap:anywhere;line-height:1.5}
.preview-username{display:block;font-size:11px;line-height:1.6;color:#8a998e;overflow-wrap:anywhere;margin:5px 0 12px}
.preview-status{display:inline-flex;align-items:center;gap:6px;padding:5px 9px;background:#e8f2e9;border-radius:20px;font-size:10px;color:#528466}
.preview-status>span{width:5px;height:5px;background:#5b9c73;border-radius:50%}
.preview-status.inactive{color:#7e8880;background:#e9eeea}
.preview-status.inactive>span{background:#9ba49d}
.employee-preview dl{padding:17px 0;margin:22px 0 0;border-top:1px dashed #dce6dc;text-align:left}
.employee-preview dl>div{display:flex;justify-content:space-between;gap:16px;font-size:11px;line-height:1.8}
.employee-preview dl>div+div{margin-top:10px}
.employee-preview dt{color:#8a988d;flex-shrink:0}
.employee-preview dd{color:#59705e;margin:0;text-align:right;overflow-wrap:anywhere;min-width:0}
.employee-next-step{display:flex;gap:9px;margin-top:18px;padding:0 3px}
.next-step-icon{display:grid;place-items:center;width:23px;height:23px;flex-shrink:0;background:#edf3ec;border-radius:7px;color:#6d8d70}
.employee-next-step h4{font-size:11px;color:#657b69;margin:3px 0 7px;font-weight:600}
.employee-next-step p{font-size:11px;color:#98a299;margin:0;line-height:1.9}
.employee-form-footer{display:flex;align-items:center;justify-content:space-between;gap:16px;border-top:1px solid #edf0ed;padding-top:20px}
.employee-form-footer>span{display:flex;align-items:center;gap:7px;color:#91a095;font-size:11px}
.employee-form-footer>span svg{color:#74a184;flex-shrink:0}
.employee-form-footer>div{display:flex;gap:10px}
.employee-submit{min-width:120px;justify-content:center}
.row-actions{min-width:260px;align-items:center}
.danger{color:#b94343}.enabled{color:#26724f;background:#eaf4ed}.disabled{color:#777;background:#eee}
small{display:block;color:#75837b;margin-top:5px}
.detail-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}
.detail-grid dd{margin:8px 0;overflow-wrap:anywhere}
.detail-grid dt,.employee-tip{color:#75837b;font-size:13px}
.role-picker{grid-column:1/-1}
.employee-fields .checkbox-label{display:flex;align-items:center;margin:12px 0}.checkbox-label input{width:auto;min-height:0}
@media(max-width:1100px){.employee-form-layout{grid-template-columns:minmax(0,1fr) 220px;gap:22px}.employee-form-aside{padding-left:20px}}
@media(max-width:800px){.employee-form-layout{grid-template-columns:minmax(0,1fr)}.employee-form-aside{display:none}.employee-form-footer>span{max-width:180px;line-height:1.6}}
@media(max-width:550px){.employee-page{gap:14px}.employee-editor{padding:18px 16px}.employee-editor-heading{gap:10px;padding-bottom:18px}.employee-editor-heading h2{font-size:16px}.editor-heading-icon{width:40px;height:40px;border-radius:11px}.employee-fields,.detail-grid,.profile-field-grid{grid-template-columns:minmax(0,1fr)}.profile-fields{display:block}.profile-field-grid{gap:18px}.employee-form-layout{padding:22px 0}.form-section-heading{margin-bottom:18px}.employee-form-footer{justify-content:flex-end}.employee-form-footer>span{display:none}.employee-form-footer>div{width:100%}.employee-form-footer button{flex:1;justify-content:center}.employee-tools label{flex:1 1 120px}.employee-editor-heading p{font-size:11px}.required-note{font-size:10px}}
@media(prefers-reduced-motion:reduce){.employee-fields input,.employee-fields select{transition:none}}
</style>
