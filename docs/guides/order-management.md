# 订单管理补齐功能

本次补齐订单运营端缺失的查询、履约及审计能力，并连接用户端订单详情。

## 管理端

入口 `/#/admin/orders`。页面支持完整订单编号、用户编号、状态、下单时间范围筛选，分页和本页 CSV 导出；订单详情展示商品及金额分摊、收货地址快照、付款尝试、状态时间线和物流。

运营操作包括：

- 已付款订单“开始处理”：沿用原有库存消费与状态机，仅允许 `paid → processing`。
- 待付款订单“关闭”：要求关闭原因，调用支付关闭保护；只有支付提供方确认未成功才进入 `cancelled`，随后由既有释放流程释放库存、优惠券和积分。
- `paid` 或 `processing` 订单登记/修改物流公司和单号。首次发货会开始处理并消费库存预占；修改物流不会重复扣库存。
- 添加管理端内部备注，所有处理、关闭、发货、物流修改和备注均记录操作员、时间和原因。

退款不直接放在订单页，已付款订单必须使用售后退款流程，避免绕过支付渠道和库存补偿。

## 用户端

`/#/orders` 支持订单状态/编号筛选、分页、详情、付款记录、状态时间线和物流查询。用户可取消自己的待付款订单，或在 `processing` 状态确认收到商品/完成服务。订单详情始终由服务端校验归属；物流服务不可用时仍返回本地已登记物流。

## 接口

管理端需要 `order:read` / `order:write` 权限：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/api/v1/admin/orders` | 分页筛选：`orderNo/status/userId/startTime/endTime` |
| GET | `/api/v1/admin/orders/{id}` | 订单详情、付款、状态、物流 |
| PUT | `/api/v1/admin/orders/{id}/process` | 开始履约处理 |
| PUT | `/api/v1/admin/orders/{id}/cancel` | 关闭待付款订单，JSON `{remark}` |
| PUT | `/api/v1/admin/orders/{id}/shipment` | JSON `{carrier,trackingNo}` 登记或修改物流 |
| GET | `/api/v1/admin/orders/{id}/operations` | 管理操作记录 |
| POST | `/api/v1/admin/orders/{id}/notes` | JSON `{remark}` 添加内部备注 |

用户端继续使用 `/api/v1/orders`、`/{id}`、`/{id}/cancel` 和 `/{id}/confirm-receipt`。

## 数据库与验证

Flyway 迁移 `V1.21__admin_order_operations.sql` 增加 `order_shipment` 和 `order_admin_operation`，并建立后台查询索引。升级前执行正常 Flyway 迁移；不能只复制前端页面。

前端：`npm run build`、`npm test`。后端：`mvn -o "-Dtest=AdminCatalogIntegrationTests,PaymentCallbackIntegrationTests" test`。测试覆盖既有支付/库存/取消状态机；上线前仍应使用真实支付渠道、物流提供方和 MySQL 做验收。
