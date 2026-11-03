# 商品商城 BFF

新增接口统一以 `/api/v1/mall` 为前缀；保留历史 `/products` 接口供旧客户端使用。
前端商城列表、详情、首页及个人中心已接入新接口。Java Long 标识在 JSON 中按项目约定输出字符串。

## 边界与数据源

商城只聚合展示，不处理订单、库存扣减、优惠公式或购物车。
当前仓库是模块化单体，商品中心适配器为 `CatalogReadService`，库存查询为 `StockReadService`，营销报价查询为 `PublishedPriceService`。
迁移 `V1.13__mall_catalog.sql` 增加商品中心的 SKU、规格、属性、主图、服务承诺和评价汇总表，以及库存、营销所有者的只读数据契约。
由对应数据所有者维护这些源数据，商城无 SKU 库存或营销报价写接口。
已有无 SKU 的商品继续使用商品中心基础报价和原库存数据；不会将 SPU 库存复制给每个 SKU。
营销只读取有效期内已经发布的最终报价，无有效报价时使用商品中心 SKU 基础报价。
商品展示起价优先采用有可用库存的有效 SKU 的最低已发布报价；全部售罄时使用有效 SKU 的最低报价，无有效 SKU 时使用商品基础价。筛选、价格排序与详情卡片使用相同数据源。迁移 `V1.22__available_sku_display_price.sql` 更新该规则。
库存服务拥有可用库存计算及数据库只读视图，商城不重复计算。

## 公共接口

| 方法 / 路径（相对前缀） | 行为 |
| --- | --- |
| GET `/products` | 商品列表，先筛选排序再分页 |
| GET `/products/{id}` | `product` 基础卡片、`mainImages`、图文详情、`servicePromises`、`review`、`attributes`、`matrix` |
| GET `/products/{id}/skus?selection={...}` | 实时规格矩阵；selection 是 URL 编码后的 JSON 对象 |
| GET `/brands`、`/attributes` | 品牌、属性筛选项 |
| GET `/search` | ES 关键词召回 + 商品中心实时筛选、排序和分页 |
| GET `/search/suggest?keyword=...` | ES 前缀联想；故障时匹配运营热词 |
| GET `/search/hotwords` | 已启用热搜词，按热度倒序，最多 10 条 |
| GET `/home` | 仅返回启用且在投放时间范围内的公开楼层 |

列表与搜索参数：`page`（从 1 开始）、`size`（1–100）、`categoryId`（含后代）、`keyword`、`brandId`、`minPrice`、`maxPrice`、`inStock`、`attributes[属性名]=属性值`。
多个属性之间采用 AND；价格区间包含端点，负数及倒置区间返回参数错误。
`sort=comprehensive|sales|price|newest`，`order=asc|desc`；兼容 `createTime`。
综合排序：普通列表按销量、ID 降序；搜索按 ES 相关度，再按 ID 稳定排序。
`inStock=true` 表示有可用库存且可售；预售、待上架商品不满足该条件。

规格矩阵返回全部真实 SKU、每 SKU 报价与库存、规格维度和各选项的 `disabled`。
置灰判断会匹配其他已选维度，排除不存在、停用、售罄及商品状态不允许购买的组合。
只有完整且可选组合才返回 `selectedSkuId`；单个无规格 SKU 自动选中。
页面选择规格时重新查询，另提供手动刷新和每 20 秒刷新；这只是展示报价与库存，不代表预占。
SKU 数据不接入现有仅支持 SPU 的购物车流程，本次不扩展购物车。

状态：`on_sale` 上架、`off_sale` 下架、`sold_out` 售罄、`presale` 预售、`scheduled` 待上架。
上架商品无可用库存时展示售罄；只有上架且有货时 `purchasable=true`。
下架商品从列表隐藏，但详情保留内容并返回明确的 `statusMessage`。
草稿、删除或类目不可见商品不公开；待上架不等同于公开草稿，必须显式设置 `scheduled`。

## 用户接口

以下接口需要 USER 身份。用户 ID 来自认证上下文，不接受客户端传入用户 ID。

| 方法 / 路径 | 行为 |
| --- | --- |
| PUT、DELETE `/me/favorites/{productId}` | 幂等收藏 / 取消收藏 |
| GET `/me/favorites/{productId}` | 是否收藏 |
| GET `/me/favorites?page=1&size=12` | 收藏列表，最近收藏在前 |
| POST `/me/history/{productId}` | 记录足迹，同商品去重并移到最前 |
| GET `/me/history?page=1&size=12` | 最近浏览在前 |
| DELETE `/me/history/{productId}`、`/me/history` | 删除单条 / 清空足迹 |

足迹容量默认 200，环境变量 `MALL_HISTORY_CAPACITY` 可配置 1–10000。
唯一键保证用户与商品去重；按用户加事务锁，避免并发记录突破容量。
失效商品在个人列表保留 `productId` 并标记 `unavailable=true`，允许用户清理。
前端路由 `/favorites`、`/history` 已加入个人中心，登录后的详情访问自动记录足迹。

## ES 配置与同步

配置环境变量 `MALL_ES_URL`、可选 `MALL_ES_API_KEY`（ApiKey），默认别名 `MALL_ES_INDEX=mall-products`。
开启 `MALL_ES_SYNC_ENABLED=true` 后，每 `MALL_ES_SYNC_MS` 毫秒同步一次，默认 300000；首次定时同步同样等待该间隔。
首次启动或商品批量更新后，可用具有 `product:write` 权限的管理员调用 `POST /api/v1/admin/mall/search/reindex` 立即同步。
该接口创建版本索引、批量写入、refresh 后原子切换别名，失败保留旧索引，成功后清理原别名所属的旧版本。
本地可以使用 `docker compose up -d` 启动完整环境，主配置 `docker-compose.yml` 已包含 Elasticsearch，并开启后端商品索引定时同步。
ES 使用内置 standard 分词器，无需安装插件；后续若需要词典、拼音、同义词可在商品搜索源进一步配置。

ES 只用于文本召回与相关度。筛选、价格/销量/新品排序、总数和分页交由商品中心的实时数据读取，避免索引中价格和库存滞后造成错误展示。
搜索返回 `results`、`recommendations`、`degraded`、`message`。
ES 未配置、超时、错误或超过 10000 个召回候选时，回退数据库基础关键词搜索，保留筛选条件并标记降级；不返回被截断的总数。
无结果时保持 `results.total=0`，另返回最多 6 条热门推荐，前端独立展示。
商品名称等文本变更在索引同步后参与 ES 搜索；商品下架、删除、类目隐藏始终由商品中心实时校验。

## 首页运营配置

复用既有管理员接口 `PUT /api/v1/admin/config`（`operation:write`），写入 `configKey=mall.home.floors`。
`configValue` 为下列数组的 JSON 字符串；C 端只读取此公开键，不暴露其他系统配置。

```json
[
  {
    "id": "summer-banner",
    "type": "banner",
    "title": "安心好物",
    "sort": 10,
    "enabled": true,
    "startsAt": null,
    "endsAt": null,
    "items": [
      { "title": "精选燃气灶", "imageUrl": "/images/banner.jpg", "path": "/catalog", "productId": null }
    ]
  }
]
```

楼层类型为 `banner|activity|recommend`，按 sort、id 排序。投放时间使用 ISO-8601（例如 `2026-10-27T00:00:00Z`），结束时间不包含端点。
链接仅允许站内路径，图片允许 HTTPS 或站内路径；绑定失效、下架商品的运营项不会下发。

## 验证

`mvn test`；`frontend` 目录运行 `npm test` 和 `npm run build`。
新增 H2 集成测试覆盖价格/库存/属性过滤、状态、详情、规格矩阵、用户隔离、收藏幂等、足迹容量、无结果兜底和首页白名单。
ES HTTP 协议测试使用本地模拟服务验证召回、降级、结果窗口保护及索引批量发布，不依赖外部 ES；真实 ES 和 MySQL 部署仍需按上述配置联调。
