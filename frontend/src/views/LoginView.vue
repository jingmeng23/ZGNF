<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Flame, ArrowLeft } from 'lucide-vue-next'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { ApiError } from '../api/http'
const phone = ref(''), code = ref(''), message = ref(''), error = ref('')
const sending = ref(false), submitting = ref(false), remaining = ref(0)
const validPhone = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const auth = useAuthStore(), route = useRoute(), router = useRouter()
let timer: ReturnType<typeof setInterval> | undefined
function showError(e: unknown) { error.value = e instanceof ApiError && e.traceId ? `${e.message}（请求编号：${e.traceId}）` : (e as Error).message }
async function sendCode() {
  if (!validPhone.value || sending.value || remaining.value) return
  sending.value = true; error.value = ''; message.value = ''
  try {
    await authApi.sendCode(phone.value)
    message.value = '验证码发送任务已提交，请留意短信。'
    remaining.value = 60
    timer = setInterval(() => { if (--remaining.value <= 0) clearInterval(timer) }, 1000)
  } catch (e) { showError(e) } finally { sending.value = false }
}
async function login() {
  if (submitting.value) return
  if (!validPhone.value || !/^\d{6}$/.test(code.value)) { error.value = '请输入有效的手机号和 6 位验证码'; return }
  submitting.value = true; error.value = ''
  try {
    await auth.login(phone.value, code.value)
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/account'
    await router.replace(target.startsWith('/') && !target.startsWith('//') ? target : '/account')
  } catch (e) { showError(e) } finally { submitting.value = false }
}
onUnmounted(() => clearInterval(timer))
</script>
<template><RouterLink to="/account" class="back-link"><ArrowLeft :size="17" /> 返回个人中心</RouterLink><section class="login-panel"><span class="brand-mark"><Flame :size="28" /></span><span class="eyebrow">WELCOME HOME</span><h1>欢迎回家</h1><p>手机验证码登录，首次登录自动注册。</p><form @submit.prevent="login"><label for="phone">手机号码</label><input id="phone" v-model="phone" type="tel" inputmode="tel" autocomplete="tel" maxlength="11" placeholder="请输入手机号码" required pattern="1[3-9][0-9]{9}" /><label for="code">短信验证码</label><div class="code-field"><input id="code" v-model="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="6 位验证码" required pattern="[0-9]{6}" /><button type="button" :disabled="!validPhone || sending || remaining > 0" @click="sendCode">{{ remaining ? `${remaining}s 后重试` : sending ? '提交中…' : '获取验证码' }}</button></div><p v-if="message" class="success-message" role="status">{{ message }}</p><p v-if="error" class="error-message" role="alert">{{ error }}</p><button class="button full-width" :disabled="submitting" type="submit">{{ submitting ? '正在登录…' : '登录 / 注册' }}</button></form><small class="login-note">验证码由短信服务发送，请勿向他人泄露。</small></section></template>
