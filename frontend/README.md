# 中南燃服前端

基于 Vue 3、TypeScript、Vite、Vue Router、Pinia 和 Lucide 图标。管理端与现有购物端共用请求和会话基础，购物端保留 Capacitor 配置供后续 APP 开发使用。

## 启动

需要 Node.js 22.12 或更高版本。

```shell
cd frontend
npm ci
npm run dev
```

- 管理端：http://localhost:5173/#/admin
- 管理员登录：http://localhost:5173/#/admin/login
- 购物端：http://localhost:5173/#/

复制 `.env.example` 为 `.env.local` 修改配置；示例配置使用真实接口并要求登录。Vite 环境变量变化后需要重启开发服务。

## 业务与接口

前端包含商城用户端和运营后台，商品、分类、订单、库存、员工、用户、角色权限、售后等模块使用独立页面。当前功能与权限以 [模块 API 对照](../docs/api/module-api-map.md) 和对应业务指南为准，统一入口见 [文档索引](../docs/README.md)。

## 接入后端

```dotenv
VITE_API_BASE_URL=/api/v1
VITE_API_PROXY_TARGET=http://localhost:8081
VITE_DEMO_MODE=false
VITE_LOGIN_DISABLED=false
```

开发代理将 `/api` 转发到 `VITE_API_PROXY_TARGET`；按实际后端端口修改，例如后端默认端口 8080。生产部署需配置反向代理，或使用实际 API 地址并配置后端 CORS。

管理员通过 `/admin/auth/login` 使用员工账号登录，普通用户会话与管理会话独立。前端权限控制用于导航和交互，接口权限由后端校验。401 清除失效会话，403 显示权限错误。

请求层和类型放在 `src/api/`，业务页面复用相应 API 适配；Java Long ID 使用字符串，避免精度丢失。演示数据用于本地预览，不能据此判断实际服务接入情况。

## 代码位置

```text
src/
  admin/                 管理布局、登录、总览、业务模块、权限提示和 CSV
  api/                   统一 HTTP、认证、管理 API 适配
  platform/session.ts    会话存储适配器
  router/                路由和导航守卫
  stores/                Pinia 状态
  styles/                全局基础样式
  views/                 现有购物端页面
```

扩展业务模块时，沿用现有专用页面和 API 适配，并在路由、管理布局中注册入口；通用列表的列与筛选定义位于 `adminData.ts`。管理样式限定于 `.admin-shell` 内。

## 检查与打包

```shell
npm run test
npm run build
npm run format
```

构建包含 TypeScript 类型检查，输出到 `dist/`。`package-lock.json` 固定依赖版本；本机 npm 10 的升级依赖操作曾遇到 peer 解析器内部错误，必要时可使用 `npm install --legacy-peer-deps`，日常安装优先使用 `npm ci`。

浏览器检查使用本机安装的 Edge 或 Chrome，无需下载浏览器。先运行 `npm run preview`，再执行 `npm run test:browser`。Windows 默认使用标准位置的 Edge；其他位置通过 `BROWSER_BIN` 指定。`SMOKE_BASE_URL` 可覆盖测试地址。测试会检查总览、全部业务路由、搜索、筛选、分页、详情、登录入口和移动布局，并生成被 Git 忽略的 `.screenshots/` 截图。

## Android APP

Android 工程已在 `android/` 中生成，接入返回键、网络状态、启动页、状态栏和键盘插件。复制 `.env.android.example` 为 `.env.android.local` 并填写手机可访问的 API 地址后，运行 `npm run app:apk` 构建调试安装包，或 `npm run app:release` 构建正式签名 AAB。需要 JDK 21 和 Android SDK 35。完整配置、签名及验收步骤见 [Android 开发说明](../docs/guides/android-app.md)。真实支付、推送及正式部署仍需实际商户和服务器配置。
