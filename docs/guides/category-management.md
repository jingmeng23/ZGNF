# 分类管理

管理入口 `/#/admin/categories`，后台导航「经营 → 分类管理」。页面连接真实分类 API，无演示数据回退。

## 必备功能

- 树形展示与展开/收起；名称或编号搜索、状态筛选保留匹配项的上级路径。
- 新增根分类或子分类，查看详情、编辑名称、调整上级、排序和启用状态；排序值越小越靠前，同值按编号升序。
- 上级选择排除自身和全部后代，服务端再次校验环路。移动父分类会连同整个子树移动。
- 独立启用/停用操作；启用要求所有上级已启用，停用要求先停用直接子分类并下架直接关联的公开商品。
- 显示直接子分类数及直接关联的未删除商品数；商品数链接跳转到商品管理并携带分类筛选。
- 安全删除采用既有逻辑删除：有未删除子分类或商品时拒绝；草稿和下架商品也需先转移或删除。
- 加载、空数据、失败重试、操作反馈、重复提交限制及危险操作确认；服务端执行权限与关联约束。

## 接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/v1/admin/categories/management` | 平铺全量分类及直接子分类数、商品数，前端按层级展示与筛选 |
| GET | `/api/v1/admin/categories` | 兼容既有平铺分类列表 |
| GET | `/api/v1/admin/categories/{id}` | 分类详情 |
| POST | `/api/v1/admin/categories` | 新增，`{name,parentId,sort,status}` |
| PUT | `/api/v1/admin/categories/{id}` | 编辑、移动及排序，字段同新增 |
| PUT | `/api/v1/admin/categories/{id}/status` | 仅变更状态，`{status:"active"或"inactive"}` |
| DELETE | `/api/v1/admin/categories/{id}` | 安全删除 |

查询要求 `category:read`，维护要求 `category:write`。ID 按字符串传递以保留 Java Long 精度，根级 `parentId="0"`。名称去除首尾空白后不能为空，同一父级不能重名，最大 64 字；排序为非负 INT。

所有写入沿用分类事务锁和提交后的分类树缓存失效事件；商城继续使用 `/api/v1/categories/tree`，仅展示根连通的启用分类。本次无需新增数据库结构或迁移。

验证：`npm test`、`npm run build`、`mvn -o test`。测试覆盖分类 CRUD、计数、层级与重名保护、启停约束、权限、前端 API 请求和树形过滤。
