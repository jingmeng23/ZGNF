import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import './styles/main.css'
import './styles/native.css'
import { initializeNativeApp } from './platform/native'

createApp(App).use(createPinia()).use(router).mount('#app')
void initializeNativeApp(router).catch((error) => console.error('原生功能初始化失败', error))
