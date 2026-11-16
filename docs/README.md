# 文档索引

| 目录 | 用途 | 入口 |
| --- | --- | --- |
| `api/` | 接口契约、参数与权限 | [模块 API 对照](api/module-api-map.md)、[商品](api/catalog-api.md)、[订单](api/order-api.md)、[统计](api/statistics-api.md) |
| `guides/` | 开发、配置和业务操作 | [项目目录与维护](guides/repository-layout.md)、[新手阅读指南](guides/新手阅读指南.md)、[微信小程序](guides/wechat-mini-program.md)、[Android](guides/android-app.md) |
| `architecture/` | 数据结构、结算、缓存与状态流转 | [结算](architecture/order-checkout.md)、[支付回调](architecture/payment-callback.md)、[商品缓存](architecture/product-detail-cache.md)、[搜索](architecture/search-es.md) |
| `archive/` | 带时间背景的盘查、升级及验收记录 | [功能盘查](archive/product-gap-audit-2026-11-10.md)、[门店移除](archive/store-removal.md)、[历史业务验收](archive/business-check/REPORT.md) |
| `notes/` | 开发学习笔记 | [Java 笔记](notes/java-notes.md)、[Docker 命令](notes/docker-commands.md) |

各客户端的启动与检查命令分别见 [Web 前端](../frontend/README.md) 和 [微信小程序](../miniprogram/README.md)。后端启动见 [项目 README](../README.md)。

业务操作指南：

- [商品管理](guides/product-management.md)、[分类管理](guides/category-management.md)、[库存管理](guides/inventory-management.md)
- [订单管理](guides/order-management.md)、[用户订单操作](guides/order-user-operations.md)、[售后管理](guides/aftersale-management.md)
- [员工管理](guides/employee-management.md)、[角色权限](architecture/role-permissions.md)

手工 SQL 统一放在 `scripts/sql/`，应用自动迁移仍位于 `src/main/resources/db/migration/`。历史记录保留当时结论，当前能力以模块接口文档、源码和本轮验证为准。
