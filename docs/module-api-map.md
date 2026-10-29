# 系统模块与前后端 API 对照

本文依据当前仓库中的前端路由、API 客户端和后端 Controller 整理，供后续开发、联调和排期使用。接口路径均省略统一前缀 **`/api/v1`**，例如 `GET /orders` 实际地址为 `/api/v1/orders`。

## 模块总览

后端包含 14 个业务模块：售后、认证、购物车、优惠券、配送、库存、商城、订单、支付、商品、统计、系统、用户、工单。前端由商城用户端和后台管理端构成。各页面和接口的实际连接情况见下表。

## 用户端模块

| 模块 | 前端页面 / 客户端 | 前端调用的 API | 后端 API | 当前状态 / 后续工作 |
|---|---|---|---|---|
| 登录认证 | `/login`；`src/api/auth.ts`、`src/stores/auth.ts` | `POST /auth/sms-code`、`POST /auth/login`、`GET /users/me` | 同左 | 登录流程接口存在；`GET /users/me` 当前只返回当前用户 ID，完整个人资料未实现。 |
| 首页和商城楼层 | `/`；`MallFloors.vue`、`ProductGrid.vue` | `GET /mall/home`、`GET /mall/products` | 同左 | 楼层和首页商品读取已接入。 |
| 商品目录、分类、搜索 | `/catalog`；`CatalogView.vue`；`src/api/catalog.ts`、`src/api/mall.ts` | `GET /mall/products`、`GET /categories`、`GET /mall/search`、`GET /mall/brands`、`GET /mall/attributes`、`GET /mall/search/hotwords`、`GET /mall/search/suggest` | 同左；另有 `GET /categories/tree` | 搜索、筛选和分类读取已接入；分类树 API 当前未被该页面调用。 |
| 商品详情和 SKU | `/products/:id`；`ProductDetailView.vue` | `GET /products/{id}` 或 `GET /mall/products/{id}`、`GET /mall/products/{id}/skus`；浏览时 `POST /mall/me/history/{id}`；收藏状态 `GET /mall/me/favorites/{id}` | 同左 | 详情、SKU、浏览和收藏接口已接入。 |
| 收藏与浏览历史 | `/favorites`、`/history`；`MallPersonalView.vue` | `GET /mall/me/favorites`、`GET /mall/me/history`、`DELETE /mall/me/favorites/{id}`、`DELETE /mall/me/history/{id}`、`DELETE /mall/me/history` | 同左；收藏写入另有 `PUT /mall/me/favorites/{id}` | 列表和删除已接入；收藏/取消收藏由商品详情页调用。 |
| 购物车 | `/cart`；`stores/cart.ts`、`src/api/commerce.ts` | `GET /cart`、`POST /cart/items`、`PUT /cart/items/{id}`、`PUT /cart/items/{id}/selection`、`PUT /cart/selection`、`DELETE /cart/items/{id}`、`DELETE /cart/items`、`DELETE /cart/selected`、`DELETE /cart` | 同左 | 购物车读取、增删改、选择和批量操作已接入。 |
| 订单 | `/orders`、`/cart`；`OrdersView.vue`、`CartView.vue`、`src/api/commerce.ts` | 当前调用 `GET /orders`、`POST /orders` | 另有 `POST /orders/preview`、`GET /orders/{id}`、`GET /orders/scroll`、`PUT /orders/{id}/cancel`、`PUT /orders/{id}/confirm-receipt` | **部分接入**：订单列表和创建已连接；预览、详情、游标分页、取消和确认收货需要继续接入前端。购物车当前直接创建订单。 |
| 支付 | 暂无用户端支付页或支付 API 客户端 | 无 | `POST /payments`、`GET /payments`、`GET /payments/{id}` | 后端具备支付记录/发起支付接口；前端支付流程未接入。支付渠道回调见“后端接口但没有用户页面”一节。 |
| 收货地址 | `/addresses`；`AddressesView.vue`、`src/api/addresses.ts` | `GET/POST /users/me/addresses`、`PUT/DELETE /users/me/addresses/{id}`、`PUT /users/me/addresses/{id}/default`、`POST /users/me/addresses/parse`、`GET /users/me/addresses/regions`、`POST /users/me/addresses/reverse-geocode` | 同左 | 地址 CRUD、智能解析、区划读取和反向地理编码已接入。地图瓦片和浏览器定位由前端地图组件处理。 |
| 优惠券（用户侧） | 暂无用户领券/我的优惠券页面 | 无 | `GET /coupons`、`POST /coupons/{id}/claim`、`GET /user-coupons` | 后端接口已提供；前端页面和 API 客户端待开发。 |
| 服务介绍和用户工单 | `/services`；`ServicesView.vue` | 当前没有工单 API 调用 | `POST/GET /work-orders`、`GET /work-orders/{id}`、`GET /work-orders/{id}/logs`、`PUT /work-orders/{id}/cancel` | **部分接入**：服务介绍为静态页面；预约/工单创建、列表、详情和取消尚未接入。 |
| 售后（用户侧） | 暂无用户售后页面 | 无 | `POST/GET /aftersales`、`GET /aftersales/{id}`、`PUT /aftersales/{id}/cancel` | 后端用户接口已提供；前端入口和页面待开发。 |
| 用户资料 | `/account`；`AccountView.vue` | 登录后可调用 `GET /users/me` | `GET /users/me` | 当前接口只返回用户 ID；用户资料查看/编辑及后台顾客管理均未形成完整功能。 |

## 后台管理模块

后台模块路由由 `src/admin/AdminApp.vue` 和 `src/admin/adminData.ts` 配置，业务页面共用 `AdminModuleView.vue`，读取列表使用 `src/api/admin.ts` 的通用 `adminApi.list()`。

| 模块 | 前端路由 | 前端实际调用 | 后端 API | 当前状态 / 后续工作 |
|---|---|---|---|---|
| 管理员认证 | `/admin/login` | 登录页复用 `POST /auth/sms-code`、`POST /auth/login` | `POST /admin/auth/login`、`GET /admin/auth/me` | **需核实对接**：后端有独立管理员登录接口；当前前端登录页走通用用户认证 API，应在管理员登录联调时统一认证协议。 |
| 经营总览 | `/admin`；`AdminOverview.vue` | 静态展示，无统计 API 调用 | `GET /admin/statistics/overview`、`/trend`、`/products`、`/stores`、`/reports/daily`、`/reports/daily/export` | 后端统计接口存在；前端看板仍为演示数据，待接入真实统计。 |
| 订单管理 | `/admin/orders` | `GET /admin/orders` 分页列表 | `GET /admin/orders`、`GET /admin/orders/{id}`、`PUT /admin/orders/{id}/process` | 列表已接入；详情和处理操作待接入。 |
| 商品管理 | `/admin/products` | `GET /admin/products` 分页列表 | `GET/POST /admin/products`、`GET/PUT/DELETE /admin/products/{id}`、`PUT /admin/products/{id}/status` | 列表已接入；新增、编辑、删除和上下架操作待接入。 |
| 分类管理 | `/admin/categories`；`AdminCategories.vue`、`src/api/categories.ts` | `GET /admin/categories/management`、`GET/PUT/DELETE /admin/categories/{id}`、`POST /admin/categories`、`PUT /admin/categories/{id}/status` | 同左；原 `GET /admin/categories` 保留兼容 | 树形展示、搜索筛选、增删改查、层级调整、排序、启停和直接商品统计跳转已连接。 |
| 库存管理 | `/admin/inventory` | `GET /admin/inventory` 分页列表 | `GET /admin/inventory`、`GET /admin/inventory/{id}`、`GET /admin/inventory/logs`、`POST /admin/inventory`、`POST /admin/inventory/{id}/inbound`、`POST /admin/inventory/{id}/outbound`、`PUT /admin/inventory/{id}/adjust` | 列表已接入；详情、库存变更和流水查看待接入。 |
| 工单管理 | `/admin/work-orders` | `GET /admin/work-orders` 分页列表 | `GET /admin/work-orders`、`GET /{id}`、`GET /{id}/logs`、`PUT /{id}/assign`、`PUT /{id}/start`、`PUT /{id}/complete` | 列表已接入；分派、开始、完成和日志查看待接入。路径中的 `{id}` 均位于 `/admin/work-orders` 下。 |
| 售后管理 | `/admin/aftersales` | `GET /admin/aftersales` 分页列表 | `GET /admin/aftersales`、`GET /{id}`、`PUT /{id}/review`、`PUT /{id}/complete` | 列表已接入；详情、审核和完成操作待接入。路径中的 `{id}` 均位于 `/admin/aftersales` 下。 |
| 员工管理 | `/admin/employees` | `GET /admin/employees` 分页列表 | `GET/POST /admin/employees`、`GET/PUT/DELETE /admin/employees/{id}`、`PUT /admin/employees/{id}/password`、`PUT /admin/employees/{id}/roles` | 列表已接入；员工维护、密码重置和角色分配待接入。此处管理内部员工，不是顾客用户。 |
| 门店管理 | `/admin/stores` | `GET /admin/stores` 分页列表 | `GET/POST /admin/stores`、`GET/PUT/DELETE /admin/stores/{id}` | 列表已接入；新增、编辑和删除待接入。 |
| 角色权限 | `/admin/roles` | `GET /admin/roles` 分页列表 | `GET/POST /admin/roles`、`GET/PUT/DELETE /admin/roles/{id}`、`GET /admin/roles/permissions`、`PUT /admin/roles/{id}/permissions` | 列表已接入；角色及权限维护待接入。 |
| 优惠券管理 | `/admin/coupons` | `GET /admin/coupons` 分页列表 | `GET/POST /admin/coupons`、`GET/PUT/DELETE /admin/coupons/{id}` | 列表已接入；优惠券维护待接入。 |
| 操作日志 | `/admin/logs` | `GET /admin/logs` 分页列表 | `GET /admin/logs` | 列表查询已接入。 |
| 系统配置 | `/admin/config` | `GET /admin/config` | `GET/PUT /admin/config` | 配置读取已接入；保存配置待接入。 |
| 热搜词管理 | 暂无后台页面 | 无 | `GET/POST /admin/hotwords`、`PUT/DELETE /admin/hotwords/{id}` | 后端已提供；前端页面待开发。 |
| 行政区划管理 | 暂无后台页面 | 无 | `PUT /admin/address-regions` | 后端支持管理员批量导入区划；前端导入工具待开发。 |

## 后端接口但没有对应用户操作页面

| 能力 | 后端接口 | 说明 |
|---|---|---|
| 支付回调 | `POST /payment/callback/{channel}` | 支付服务端向系统发送的回调，不是浏览器页面接口；由后端验签、校验来源并处理幂等。 |
| 配送报价 | 当前由配送服务内部提供结算报价能力，没有独立 Controller | 配送服务负责可达性和运费；由订单结算流程调用。 |
| 搜索重建索引 | `POST /admin/mall/search/reindex` | 后端管理操作；当前后台没有对应按钮/页面。 |

## 开发联调注意事项

1. 当前前端后台通用列表页基本只执行 `GET` 列表请求。若要完成后台 CRUD，需要按模块补充专用 API 客户端、表单校验、详情加载和写操作交互，不能仅以“列表展示正常”视为管理功能全部完成。
2. 用户端订单、支付、优惠券、工单和售后接口与页面尚未全部打通，开发时建议逐项建立 API 客户端和页面状态/错误处理。
3. 顾客用户管理目前缺少后台 Controller 和管理页面；`/admin/employees` 只代表员工管理。
4. 地址模块管理收货信息，配送服务管理可达性和运费，订单保存本次交易地址快照。编辑地址簿不会改动已创建订单。
5. 当前仓库含临时免登录测试开关。完成本地验证后，部署环境应显式关闭前后端免登录开关，并按正常认证权限验证接口。

## 主要代码入口

- 前端路由：`frontend/src/router/index.ts`
- 前端 API 客户端：`frontend/src/api/`
- 后台模块配置：`frontend/src/admin/adminData.ts`
- 后台通用列表：`frontend/src/admin/AdminModuleView.vue`
- 后端 API Controller：`src/main/java/com/gas/ecommerce/modules/**/controller/`
