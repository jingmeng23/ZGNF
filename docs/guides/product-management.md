# 商品管理与前后端对接

## 管理入口

管理端 `/#/admin/products` 使用专用 `AdminProducts.vue` 页面；侧边栏“商品管理”直接进入。

支持商品新增、详情、编辑、上架、下架、删除，以及按名称、分类、状态查询，按创建时间、销售价、销量或编号排序和分页。商品表单支持分类、自由填写的可选品牌、销售价、可选原价、副标题、封面图片、纯文本描述及最多 20 张详情图。管理员直接选择图片上传，无需填写图片地址；封面可替换或移除，详情图支持批量上传、预览、调整顺序和移除。分类下拉展示父子路径，支持在商品表单中快速新增分类。

列表展示可用库存及销量，提供库存管理入口。库存通过现有库存接口独立维护，不允许商品编辑覆盖库存或订单预占。

### 商品图片上传与存储

网页端和小程序管理端均直接上传 JPG、PNG 或 GIF 文件，单张最多 10MB、4000 万像素。服务端解码验证实际图片格式，忽略客户端文件名并生成唯一文件名。上传过程中禁止提交表单；部分详情图上传失败时保留已完成的图片，可重新选择未完成的文件。

`POST /api/v1/admin/products/images` 接收 multipart 字段 `file`，需要管理员的 `product:write` 权限，返回 `{ "code": 200, "data": { "url": "/api/v1/media/products/唯一文件名.png" } }`。保存商品时，客户端自动将此地址放入 `coverUrl` 或 `detailImages`；图片通过 `GET /api/v1/media/products/{filename}` 公开读取，商城访客无需登录即可查看。

本地文件默认保存在后端工作目录的 `uploads/products/`，可通过环境变量 `PRODUCT_IMAGE_DIRECTORY` 配置。该目录应可写并随业务数据备份，不能放入前端构建目录。Docker Compose 已挂载 `product_images` 持久卷，图片可在容器重建后保留。反向代理需将 `/api/`（包含图片路径）转发到后端。小程序上传使用 API 域名，需配置微信 `uploadFile` 和 `downloadFile` 合法域名。

已有 `/images/products/...` 或 HTTP(S) 图片仍可展示；前端内置图片继续随完整 `dist` 发布。移除图片或取消表单只清除商品引用，不立即删除磁盘文件，以免影响其他商品或历史订单；未引用文件的清理需另行维护。

## 后端接口

品牌可直接填写名称（最多 100 字符），留空表示无品牌。保存时自动复用同名品牌或创建新品牌，商品详情和列表返回 `brandName`。新增、编辑接口支持可选 `brandName`，同时兼容旧客户端传入的 `brandId`；填写非空名称时以名称为准。

商城 `/mall/products/{id}` 的 `product.brandName` 和兼容接口 `/products/{id}` 的 `brandName` 均返回品牌名称，详情页直接展示。更新后端代码后需重启 Java 服务；可通过 `/v3/api-docs` 中 `ProductCreateRequest` 是否包含 `brandName` 确认当前运行版本。旧服务会忽略该字段，前端现会提示品牌未正确保存。此前被旧服务忽略的品牌需在更新后重新填写并保存。

统一前缀 `/api/v1`，响应使用现有 `Result`，分页数据为 `list/total/page/size/pages`。

| 方法 | 路径 | 用途 | 权限 |
| --- | --- | --- | --- |
| GET | `/admin/products` | 分页、搜索、分类/状态筛选、排序 | `product:read` |
| GET | `/admin/products/options` | 商品表单分类及品牌选项 | `product:read` |
| GET | `/admin/products/{id}` | 最新商品详情 | `product:read` |
| POST | `/admin/products/images` | 上传商品封面或详情图片 | `product:write` |
| GET | `/media/products/{filename}` | 读取已上传图片 | 公开 |
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
