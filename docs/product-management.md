# 商品管理与前后端对接

## 管理入口

管理端 `/#/admin/products` 使用专用 `AdminProducts.vue` 页面；侧边栏“商品管理”直接进入。

支持商品新增、详情、编辑、上架、下架、删除，以及按名称、分类、状态查询，按创建时间、销售价、销量或编号排序和分页。商品表单支持分类、自由填写的可选品牌、销售价、可选原价、副标题、封面地址、纯文本描述及最多 20 张详情图；图片通过已有 URL 维护，不包含文件上传。分类下拉展示父子路径，支持在商品表单中快速新增分类。

列表展示可用库存及销量，提供库存管理入口。库存通过现有库存接口独立维护，不允许商品编辑覆盖库存或订单预占。

### 本地商品图片

将图片放入 `frontend/public/images/products/`，在封面或详情图片地址中填写 `/images/products/文件名`。例如已提供的百事可乐图片使用 `/images/products/pepsi.png`。不要填写 `D:\...` 或 `file://...` 本机路径。Vite 开发服务会直接提供 public 下的文件，生产构建会将其复制到 `dist/images/products/`；部署时需发布完整的 `dist` 目录。

## 后端接口

品牌可直接填写名称（最多 100 字符），留空表示无品牌。保存时自动复用同名品牌或创建新品牌，商品详情和列表返回 `brandName`。新增、编辑接口支持可选 `brandName`，同时兼容旧客户端传入的 `brandId`；填写非空名称时以名称为准。

商城 `/mall/products/{id}` 的 `product.brandName` 和兼容接口 `/products/{id}` 的 `brandName` 均返回品牌名称，详情页直接展示。更新后端代码后需重启 Java 服务；可通过 `/v3/api-docs` 中 `ProductCreateRequest` 是否包含 `brandName` 确认当前运行版本。旧服务会忽略该字段，前端现会提示品牌未正确保存。此前被旧服务忽略的品牌需在更新后重新填写并保存。

统一前缀 `/api/v1`，响应使用现有 `Result`，分页数据为 `list/total/page/size/pages`。

| 方法 | 路径 | 用途 | 权限 |
| --- | --- | --- | --- |
| GET | `/admin/products` | 分页、搜索、分类/状态筛选、排序 | `product:read` |
| GET | `/admin/products/options` | 商品表单分类及品牌选项 | `product:read` |
| GET | `/admin/products/{id}` | 最新商品详情 | `product:read` |
| POST | `/admin/products` | 创建草稿 | `product:write` |
| PUT | `/admin/products/{id}` | 完整更新商品资料 | `product:write` |
| PUT | `/admin/products/{id}/status` | 修改状态 | `product:write` |
| DELETE | `/admin/products/{id}` | 逻辑删除 | `product:write` |
| POST | `/admin/categories` | 快速新增分类 | `category:write` |

列表参数：`page` 从 1 开始，`size` 最大 100，`keyword` 为商品名，`categoryId` 精确匹配分类，`status` 为 `draft/on_sale/off_sale/sold_out/presale/scheduled`。`sort` 为 `createTime/price/sales/id`，`order` 为 `asc/desc`。

创建及编辑示例：

```json
{
  "categoryId": "123",
  "brandId": null,
  "name": "双灶燃气灶",
  "subtitle": "家用灶具",
  "coverUrl": "/images/stove.jpg",
  "price": 899.00,
  "originalPrice": 999.00,
  "description": "商品规格与使用说明",
  "detailImages": ["https://images.example/stove-detail.jpg"]
}
```

可空字段传 `null` 可清空旧值。金额最多两位小数，销售价至少 0.01 元，原价不得低于销售价。图片地址只能使用 `http://`、`https://` 或 `/` 开头的站内路径，不接受协议相对地址、脚本地址或换行。Long ID 在前端保持字符串，避免精度损失。

状态接口请求：`{"status":"on_sale"}` 或 `{"status":"off_sale"}`。

## 业务约束

- 新增默认草稿；保存和上架为独立操作。
- 上架要求分类及祖先分类启用，设置品牌时品牌也必须启用。零库存商品可以上架，商城显示售罄且不能购买。
- 下架后商品退出商城商品列表，结算仍由服务端重新校验状态和库存。
- 删除前必须为草稿或已下架，且所有库存记录的实际库存和订单预占都为零；前端先提示并确认，后端在事务内最终校验。采用逻辑删除，历史订单保留。
- 商品修改通过既有事件机制通知商城详情缓存失效，不另建缓存或库存计算逻辑。
- 分类及品牌选项沿用 `product:read`，不要求为商品编辑额外授予分类读取权限；创建分类仍独立要求 `category:write`。

## 本地运行与验证

1. 按项目 README 配置 MySQL、Redis、RabbitMQ 和登录账号；根目录运行 `mvn spring-boot:run`。
2. 在 `frontend` 运行 `npm install` 和 `npm run dev`，访问 `/#/admin/products`。默认 Vite 将 `/api` 代理到 `http://localhost:8080`，通过 `VITE_API_PROXY_TARGET` 可修改代理目标。
3. 使用有商品权限的管理员登录。页面提交真实接口，不使用演示记录执行增删改。
4. 前端验证：`npm run build`、`npm test`。
5. 后端验证：`mvn -o "-Dtest=AdminCatalogIntegrationTests,ProductDetailCacheServiceTests" test`，覆盖生命周期、过滤、权限、图片地址及缓存相关行为。集成测试使用 H2，未替代目标 MySQL 和浏览器的部署验收。
