<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { usersApi, type ManagedUser } from '../api/users'

const rows = ref<ManagedUser[]>([]), selected = ref<ManagedUser | null>(null)
const query = reactive({ page: 1, size: 10, keyword: '', status: '' })
const total = ref(0), pages = ref(1), loading = ref(false), busy = ref(false), error = ref(''), notice = ref('')
const explain = (e: unknown) => e instanceof Error ? e.message : '操作失败，请重试'
async function load() {
  loading.value = true; error.value = ''
  try { const result = await usersApi.list(query); rows.value = result.list; total.value = result.total; pages.value = Math.max(1, result.pages) }
  catch (e) { error.value = explain(e) } finally { loading.value = false }
}
async function detail(row: ManagedUser) { busy.value = true; error.value = ''; try { selected.value = await usersApi.detail(row.id) } catch (e) { error.value = explain(e) } finally { busy.value = false } }
async function changeStatus(row: ManagedUser) {
  const status = row.status === 'active' ? 'inactive' : 'active'
  if (!window.confirm(`确认${status === 'active' ? '启用' : '停用'}用户 ${row.nickname || row.id}？`)) return
  busy.value = true; error.value = ''
  try { await usersApi.status(row.id, status); notice.value = '用户状态已更新'; await load(); if (selected.value?.id === row.id) selected.value = await usersApi.detail(row.id) }
  catch (e) { error.value = explain(e) } finally { busy.value = false }
}
async function remove(row: ManagedUser) {
  if (!window.confirm(`确认删除用户 ${row.nickname || row.id}？该操作会保留关联业务记录。`)) return
  busy.value = true; error.value = ''
  try { await usersApi.remove(row.id); notice.value = '用户已删除'; if (selected.value?.id === row.id) selected.value = null; await load() }
  catch (e) { error.value = explain(e) } finally { busy.value = false }
}
function search() { query.page = 1; void load() }
onMounted(load)
</script>

<template>
  <div class="admin-page">
    <header class="page-heading"><div><span class="page-kicker">CUSTOMERS</span><h1>用户管理</h1><p>查询用户资料、会员信息和账户状态。</p></div></header>
    <p v-if="error" class="demo-banner" role="alert">{{ error }}</p><p v-if="notice" class="demo-banner" role="status">{{ notice }}</p>
    <section class="panel list-panel" :aria-busy="loading">
      <form class="user-tools" @submit.prevent="search"><label>用户 / 手机尾号<input v-model="query.keyword" maxlength="100" placeholder="输入昵称、ID 或手机号尾号" /></label><label>账户状态<select v-model="query.status"><option value="">全部状态</option><option value="active">正常</option><option value="inactive">停用</option><option value="cancelled">已注销</option></select></label><button class="secondary-button" :disabled="loading || busy">查询</button></form>
      <div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>用户</th><th>手机号</th><th>会员等级</th><th>积分</th><th>状态</th><th>注册时间</th><th>操作</th></tr></thead><tbody>
        <tr v-for="row in rows" :key="row.id"><td>{{ row.nickname || '未设置昵称' }}<small>ID {{ row.id }}</small></td><td>{{ row.phoneLast4 ? `**** ${row.phoneLast4}` : '未绑定' }}</td><td>{{ row.memberLevel || '普通会员' }}</td><td>{{ row.points ?? 0 }}</td><td><span class="status-tag" :class="row.status === 'active' ? 'enabled' : 'disabled'">{{ row.status === 'active' ? '正常' : row.status === 'inactive' ? '停用' : '已注销' }}</span></td><td>{{ row.createTime || '—' }}</td><td><div class="user-actions"><button class="text-action" :disabled="busy" @click="detail(row)">详情</button><button v-if="row.status !== 'cancelled'" class="text-action" :disabled="busy" @click="changeStatus(row)">{{ row.status === 'active' ? '停用' : '启用' }}</button><button class="text-action danger" :disabled="busy" @click="remove(row)">删除</button></div></td></tr>
        <tr v-if="!rows.length"><td colspan="7" class="empty-state">{{ loading ? '正在加载…' : error ? '加载失败，请重试' : '暂无用户' }}</td></tr>
      </tbody></table></div>
      <div class="pagination"><span>共 {{ total }} 位用户</span><div><select v-model.number="query.size" aria-label="每页条数" :disabled="busy || loading" @change="search"><option :value="10">10 条 / 页</option><option :value="20">20 条 / 页</option><option :value="50">50 条 / 页</option></select><button :disabled="busy || loading || query.page <= 1" @click="query.page--;load()">上一页</button><span>{{ query.page }} / {{ pages }}</span><button :disabled="busy || loading || query.page >= pages" @click="query.page++;load()">下一页</button></div></div>
    </section>
    <section v-if="selected" class="panel user-detail"><div class="detail-heading"><h2>用户详情</h2><button class="text-action" @click="selected = null">关闭</button></div><dl><div><dt>用户 ID</dt><dd>{{ selected.id }}</dd></div><div><dt>昵称</dt><dd>{{ selected.nickname || '未设置' }}</dd></div><div><dt>手机号</dt><dd>{{ selected.phoneLast4 ? `**** ${selected.phoneLast4}` : '未绑定' }}</dd></div><div><dt>邮箱</dt><dd>{{ selected.email || '未设置' }}</dd></div><div><dt>性别 / 生日</dt><dd>{{ selected.gender || '未设置' }} / {{ selected.birthday || '未设置' }}</dd></div><div><dt>会员等级 / 积分</dt><dd>{{ selected.memberLevel }} / {{ selected.points }}</dd></div><div><dt>账户状态</dt><dd>{{ selected.status }}</dd></div><div><dt>注册时间</dt><dd>{{ selected.createTime }}</dd></div></dl></section>
  </div>
</template>

<style scoped>
.user-tools,.user-actions,.detail-heading{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.user-tools{padding:20px}.user-tools label{display:grid;gap:7px}.user-tools input,.user-tools select,.pagination select{border:1px solid #dce4de;border-radius:6px;padding:10px;font:inherit;background:#fff}.user-actions{min-width:150px}.user-detail{padding:24px;margin-top:24px}.detail-heading{justify-content:space-between}.detail-heading h2{font-size:18px;margin:0}.user-detail dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.user-detail dt{color:#75837b;font-size:12px}.user-detail dd{margin:6px 0 0;overflow-wrap:anywhere}.danger{color:#b94343}.enabled{color:#26724f;background:#eaf4ed}.disabled{color:#777;background:#eee}small{display:block;color:#75837b;margin-top:5px}@media(max-width:700px){.user-detail{padding:16px}.user-detail dl{grid-template-columns:1fr}}
</style>
