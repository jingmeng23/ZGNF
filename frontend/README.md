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

默认开启演示模式，无需启动后端或登录即可查看管理页面。可以复制 `.env.example` 为 `.env.local` 修改配置；Vite 环境变量变化后需要重启开发服务。

## 页面范围

经营总览包含演示指标、成交趋势、订单分布和最近订单；业务模块包括订单、商品、库存、工单、售后、优惠券、门店、员工、角色权限、操作日志、系统配置。列表支持搜索、筛选、分页、详情弹窗及 CSV 导出，适配桌面和窄屏。

当前完成的是管理页面框架。示例数据仅供预览，新增、编辑、派单、退款、角色授权等写操作需随后端业务 API 开发。角色页展示角色记录，不会据此修改真实权限。

## 接入后端

```dotenv
VITE_API_BASE_URL=/api/v1
VITE_API_PROXY_TARGET=http://localhost:8080
VITE_DEMO_MODE=false
```

开发代理将 `/api` 请求转发到 Spring Boot 8080。生产部署需要在 Web 服务器配置相同的反向代理，或将 `VITE_API_BASE_URL` 指向实际 API 地址并配置后端 CORS。前端不包含任何服务端密钥。

已存在且可调用的后端方法是 `POST /auth/sms-code`、`POST /auth/login`、`GET /users/me`。当前短信登录仅签发 `user` 角色；管理接口要求 `ADMIN`，所以真实模式需要后端先完善员工授权和管理员令牌签发。前端会拒绝普通用户角色，不保存其令牌为管理员会话。令牌保存在当前标签页的 `sessionStorage`，不持久化 refresh token；后端未实现刷新接口时不会自行调用刷新。

前端对 JWT 角色的判断只用于导航体验，JWT 验签、员工状态和权限校验必须由后端执行。401 会清除会话并跳转管理登录页，403 保留会话并显示权限错误。

下面的资源前缀与后端 Controller 一致，但这些 Controller 目前没有具体业务方法。列表 `GET` 及其搜索字段是前端预留约定，需要在后端实现或在 `src/api/admin.ts` 中增加适配。

| 管理页面 | 资源前缀（相对于 `/api/v1`） |
| --- | --- |
| 订单 | `/admin/orders` |
| 商品 | `/admin/products` |
| 库存 | `/admin/inventory` |
| 服务工单 | `/admin/work-orders` |
| 售后 | `/admin/aftersales` |
| 优惠券 | `/admin/coupons` |
| 门店 | `/admin/stores` |
| 员工 | `/admin/employees` |
| 角色权限 | `/admin/roles` |
| 操作日志 | `/admin/logs` |
| 系统配置 | `/admin/config` |

列表预留请求：`GET /admin/orders?page=1&size=20&keyword=...&filter=...`。`page` 从 1 开始，`size` 不超过 100；`keyword` 和 `filter` 尚需后端定义与实现，目前 `filter` 为界面选项值。

响应沿用后端 `Result<PageResult<T>>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [],
    "total": 0,
    "page": 1,
    "size": 20,
    "pages": 0
  },
  "timestamp": 1792540800000,
  "traceId": "request-trace-id"
}
```

管理表格字段定义在 `src/admin/adminData.ts`，示例记录不是已实现的后端 DTO 契约。后端 DTO 确定后，在 API 层将字段、枚举值和筛选值映射为页面需要的格式。Java Long ID 保持字符串，避免数值精度丢失。真实模式使用服务端分页，导出当前页；演示模式导出当前搜索和筛选结果。经营统计尚无后端接口，真实模式会显示待接入状态，不使用演示数值填充。

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

扩展业务模块时，先在 `adminData.ts` 定义列、筛选和资源路径，再在管理布局加入导航。实际业务表单建议按模块拆分为独立组件，并在 API 层增加写方法；不要在页面中直接拼接后端地址。管理样式限定于 `.admin-shell` 内。

## 检查与打包

```shell
npm run test
npm run build
npm run format
```

构建包含 TypeScript 类型检查，输出到 `dist/`。`package-lock.json` 固定依赖版本；本机 npm 10 的升级依赖操作曾遇到 peer 解析器内部错误，必要时可使用 `npm install --legacy-peer-deps`，日常安装优先使用 `npm ci`。

浏览器检查使用本机安装的 Edge 或 Chrome，无需下载浏览器。先运行 `npm run preview`，再执行 `npm run test:browser`。Windows 默认使用标准位置的 Edge；其他位置通过 `BROWSER_BIN` 指定。`SMOKE_BASE_URL` 可覆盖测试地址。测试会检查总览、全部业务路由、搜索、筛选、分页、详情、登录入口和移动布局，并生成被 Git 忽略的 `.screenshots/` 截图。

## Android APP

Android 工程已在 `android/` 中生成，接入返回键、网络状态、启动页、状态栏和键盘插件。复制 `.env.android.example` 为 `.env.android.local` 并填写手机可访问的 API 地址后，运行 `npm run app:apk` 构建调试安装包，或 `npm run app:release` 构建正式签名 AAB。需要 JDK 21 和 Android SDK 35。完整配置、签名及验收步骤见 [Android 开发说明](../docs/android-app.md)。真实支付、推送及正式部署仍需实际商户和服务器配置。
