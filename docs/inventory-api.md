# 商品库存 API

管理员接口前缀：`/api/v1/admin/inventory`。使用管理员 access token。查询需要 `inventory:read`，变更需要 `inventory:write`；V1.9 迁移为内置 admin 角色分配这两个权限。其他角色通过角色授权接口分配。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/` | 分页库存列表 |
| GET | `/{id}` | 单条库存详情 |
| GET | `/options` | 库存建档选项：未删除商品，仅需库存查询权限 |
| POST | `/` | 建立商品库存 |
| POST | `/{id}/inbound` | 入库 |
| POST | `/{id}/outbound` | 出库 |
| PUT | `/{id}/adjust` | 盘点，设置目标实物数量 |
| GET | `/logs` | 分页库存流水 |

## 请求与返回

建档示例：

```json
{"productId":123,"quantity":100,"requestId":"opening-20261022-001","remark":"期初库存"}
```

入库、出库示例，quantity 必须是正整数：

```json
{"quantity":10,"requestId":"receive-20261022-001","remark":"采购到货"}
```

盘点示例，quantity 是盘点后实物数量，允许为零：

```json
{"quantity":95,"requestId":"count-20261022-001","remark":"实物盘点"}
```

所有变更必须有 requestId 和非空 remark；requestId 为 1～64 位英文字母、数字、下划线或短横线，remark 最长 255 字。变更返回统一 Result 包装的流水对象：id、inventoryId、productId、changeType、changeQuantity、quantityBefore/After、lockedBefore/After、operatorId、requestId、referenceNo、remark、createTime。建档返回的 id 是流水 ID，库存 ID 使用 inventoryId。

详情包含商品名、quantity（实物）、lockedQuantity（订单锁定）、availableQuantity（可用）、version 和时间。可用数量 = 实物数量 − 锁定数量。

列表支持 productId、keyword（商品名称）、availableMax（可用数量小于等于阈值）。

流水支持 inventoryId、productId、operatorId、changeType、referenceNo（订单编号）筛选。两种列表都支持 page（默认 1）、size（默认 20，最大 100）、order（asc/desc，默认 desc）；库存 sort 支持 id/createTime/updateTime/quantity/availableQuantity，流水支持 id/createTime，默认 createTime。

## 库存与事务规则

- 新建库存需要存在的商品；同一商品只能通过接口建档一次。已有库存使用入库接口补货。
- 入库或向上盘点需要商品存在；出库和向下盘点允许清理历史商品的存量。商品有实物或锁定库存时禁止删除。
- 不提供直接修改锁定数量、版本或删除库存接口。订单创建预占，取消和超时释放，履约处理扣减实物及预占数量。
- 出库数量不能超过可用库存；盘点目标不能小于锁定数量；实物数量不能超过 2147483647。校验失败返回 400，库存与流水一起回滚。
- 库存行锁与事务保护订单和后台并发变更。后台变更同时持有组织管理和商品管理锁，保护关联数据并串行处理后台写入。
- 同一员工的 requestId 跨变更接口唯一。相同操作、库存目标、数量、备注的重试返回第一次流水快照；不同参数复用返回 400。新建库存重试还需商品相同。
- 库存为商城统一库存，订单按商品汇总可用数量，按实际预占的库存行释放或扣减。

## 流水与迁移

initial/inbound/outbound/adjustment 的 changeQuantity 表示实物增减；order_lock 表示锁定增加，order_release 表示锁定减少，order_deduct 表示实物与锁定同时减少。判断库存请结合实物、锁定的前后快照。订单流水的 referenceNo 是订单编号，operatorId/requestId 为空。

Flyway 自动执行 `V1.9__inventory_management.sql`。V1.28 删除库存位置字段，保留全部库存行 ID、实物数量、锁定数量与订单预占关系。同商品旧有多条记录继续按行维护，新建接口拒绝重复商品建档。历史流水缺少的快照不推算。

自动化验证使用 H2 的 MySQL 兼容模式；生产部署仍需在 MySQL 环境执行迁移和并发验收。
