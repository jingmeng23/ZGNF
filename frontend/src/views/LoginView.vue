<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Flame, ArrowLeft } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { ApiError } from '../api/http'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const phone=ref(typeof route.query.phone==='string'?route.query.phone:''),password=ref(''),error=ref(''),submitting=ref(false)
const validPhone=computed(()=>/^1[3-9]\d{9}$/.test(phone.value))
async function submit(){error.value='';if(!validPhone.value){error.value='请输入有效的11位手机号';return}if(!password.value){error.value='请输入密码';return}submitting.value=true;try{await auth.login(phone.value,password.value);const target=typeof route.query.redirect==='string'?route.query.redirect:'/account';await router.replace(target.startsWith('/')&&!target.startsWith('//')?target:'/account')}catch(e){error.value=e instanceof ApiError?e.message:(e as Error).message}finally{submitting.value=false}}
</script>
<template><RouterLink to="/account" class="back-link"><ArrowLeft :size="17"/> 返回个人中心</RouterLink><section class="login-panel"><span class="brand-mark"><Flame :size="28"/></span><span class="eyebrow">WELCOME HOME</span><h1>用户登录</h1><p>使用已注册的手机号和密码登录。</p><p v-if="route.query.registered === '1'" class="success-message" role="status">注册成功，请使用刚设置的手机号和密码登录。</p><form @submit.prevent="submit"><label for="phone">手机号</label><input id="phone" v-model="phone" type="tel" inputmode="tel" autocomplete="tel" maxlength="11" placeholder="请输入手机号" required/><label for="password">密码</label><input id="password" v-model="password" type="password" autocomplete="current-password" maxlength="64" placeholder="请输入密码" required/><p v-if="error" class="error-message" role="alert">{{error}}</p><button class="button full-width" :disabled="submitting" type="submit">{{submitting?'登录中…':'登录'}}</button></form><RouterLink class="text-action" :to="{path:'/register',query:route.query.redirect?{redirect:route.query.redirect}:{}}">还没有账号？立即注册</RouterLink></section></template>
