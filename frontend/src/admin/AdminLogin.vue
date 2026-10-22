<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Flame, ShieldCheck } from 'lucide-vue-next'
import { authApi } from '../api/auth'
import { ApiError } from '../api/http'
import { adminDemoMode } from '../api/admin'
import { session } from '../platform/session'
import { hasAdminAccess } from './access'

const router = useRouter()
const route = useRoute()
const phone = ref('')
const code = ref('')
const busy = ref(false)
const sending = ref(false)
const remaining = ref(0)
const error = ref('')
const notice = ref('')
const validPhone = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
let timer: ReturnType<typeof setInterval> | undefined
function showError(cause: unknown) {
  error.value =
    cause instanceof ApiError && cause.traceId
      ? `${cause.message}（请求编号：${cause.traceId}）`
      : cause instanceof Error
        ? cause.message
        : '请求失败，请稍后再试'
}
async function sendCode() {
  if (!validPhone.value || sending.value || remaining.value) return
  sending.value = true
  error.value = ''
  notice.value = ''
  try {
    await authApi.sendCode(phone.value)
    notice.value = '验证码任务已提交，短信投递需后端配置短信服务。'
    remaining.value = 60
    timer = setInterval(() => {
      if (--remaining.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (cause) {
    showError(cause)
  } finally {
    sending.value = false
  }
}
async function login() {
  if (busy.value || !validPhone.value || !/^\d{6}$/.test(code.value)) return
  busy.value = true
  error.value = ''
  try {
    const result = await authApi.login(phone.value, code.value)
    if (!hasAdminAccess(result.accessToken)) {
      error.value =
        '此账号没有管理员权限。当前后端短信登录仅签发用户角色，请先完成员工授权与管理员令牌签发。'
      return
    }
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
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <section class="admin-login">
    <div class="admin-login-card">
      <span class="admin-brand-mark"><Flame :size="22" /></span>
      <span class="page-kicker">ZHONGNAN GAS · ADMIN</span>
      <h1>登录运营管理平台</h1>
      <p>使用已授权的员工账号登录。</p>
      <form @submit.prevent="login">
        <label for="admin-phone">手机号码</label>
        <input
          id="admin-phone"
          v-model="phone"
          type="tel"
          autocomplete="tel"
          maxlength="11"
          required
          pattern="1[3-9][0-9]{9}"
          placeholder="请输入员工手机号"
        />
        <label for="admin-code">短信验证码</label>
        <div class="admin-code-field">
          <input
            id="admin-code"
            v-model="code"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            required
            pattern="[0-9]{6}"
            placeholder="请输入 6 位验证码"
          />
          <button
            type="button"
            :disabled="!validPhone || sending || remaining > 0"
            @click="sendCode"
          >
            {{ remaining ? `${remaining}s` : sending ? '提交中…' : '获取验证码' }}
          </button>
        </div>
        <p v-if="notice" role="status">{{ notice }}</p>
        <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
        <button
          class="primary-button"
          :disabled="busy || !validPhone || !/^\d{6}$/.test(code)"
          type="submit"
        >
          {{ busy ? '正在验证…' : '登录管理平台' }}
        </button>
      </form>
      <p class="admin-login-note"><ShieldCheck :size="15" />管理权限由后端验证</p>
      <RouterLink v-if="adminDemoMode" to="/admin" class="text-action">进入演示工作台 →</RouterLink>
      <small v-else>当前后端尚未实现管理员角色签发，普通用户账号无法登录管理端。</small>
    </div>
  </section>
</template>
