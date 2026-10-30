<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Flame, ShieldCheck } from 'lucide-vue-next'
import { adminAuthApi } from '../api/adminAuth'
import { ApiError } from '../api/http'
import { adminDemoMode } from '../api/admin'
import { session } from '../platform/session'

const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
function showError(cause: unknown) {
  error.value =
    cause instanceof ApiError && cause.traceId
      ? `${cause.message}（请求编号：${cause.traceId}）`
      : cause instanceof Error
        ? cause.message
        : '请求失败，请稍后再试'
}
async function login() {
  if (busy.value || !username.value.trim() || !password.value) return
  busy.value = true
  error.value = ''
  try {
    const result = await adminAuthApi.login(username.value.trim(), password.value)
    session.write(result.accessToken, result.expiresIn)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/admin'
    await router.replace(
      /^\/admin(?:\/|$)/.test(redirect) && !redirect.startsWith('/admin/login')
        ? redirect
        : '/admin',
    )
  } catch (cause) {
    showError(cause)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="admin-login">
    <div class="admin-login-card">
      <span class="admin-brand-mark"><Flame :size="22" /></span>
      <span class="page-kicker">ZHONGNAN GAS · ADMIN</span>
      <h1>登录运营管理平台</h1>
      <p>使用已授权的员工账号登录。</p>
      <form @submit.prevent="login">
        <label for="admin-username">管理员账号</label>
        <input
          id="admin-username"
          v-model="username"
          type="text"
          autocomplete="username"
          maxlength="64"
          required
          placeholder="请输入管理员账号"
        />
        <label for="admin-password">密码</label>
        <input
          id="admin-password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          maxlength="72"
          required
          placeholder="请输入密码"
        />
        <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
        <button class="primary-button" :disabled="busy || !username.trim() || !password" type="submit">
          {{ busy ? '正在登录…' : '登录管理平台' }}
        </button>
      </form>
      <p class="admin-login-note"><ShieldCheck :size="15" />管理权限由后端验证</p>
      <RouterLink v-if="adminDemoMode" to="/admin" class="text-action">进入演示工作台 →</RouterLink>
    </div>
  </section>
</template>
