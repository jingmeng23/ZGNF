# 优惠券 API

响应使用统一 `Result` 包装；分页为 `list/total/page/size/pages`，page 从 1 开始，size 默认 20、最大 100。时间格式 `yyyy-MM-dd HH:mm:ss`，使用应用本地时间。

## 管理端

需要管理员令牌，读取需要 `operation:read`，写入需要 `operation:write`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/coupons` | 分页，支持 keyword、status，sort 支持 createTime/amount/startTime/endTime |
| GET | `/api/v1/admin/coupons/{id}` | 详情，包含 claimedQuantity、remainingQuantity |
| POST | `/api/v1/admin/coupons` | 创建 |
| PUT | `/api/v1/admin/coupons/{id}` | 修改 |
| DELETE | `/api/v1/admin/coupons/{id}` | 软删除，停止新领取 |

创建和修改使用完整请求：

```json
{"name":"满100减20","amount":20.00,"minimumAmount":100.00,"startTime":"2026-10-01 00:00:00","endTime":"2026-11-01 00:00:00","quantity":100,"status":"active"}
```

amount 必须大于零，minimumAmount 非负，金额最多两位小数；quantity 为非负整数，代表总发行量，已使用和过期券仍占发行量。status 可为 active/inactive，省略时默认 active。结束时间必须晚于开始时间。已有用户领取后，面额、门槛和有效期不能修改，总量不能减少至已领取数量以下。名称或模板状态的修改不改变用户已领取的快照。

## 用户端

需要普通用户令牌及有效用户账号。管理员令牌不能调用。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/coupons` | 领券中心，分页及 keyword；仅返回当前有效、启用且尚有发行余量的模板 |
| POST | `/api/v1/coupons/{id}/claim` | 领取，空请求体；每人每种券限领一张，重试返回原券 |
| GET | `/api/v1/user-coupons` | 我的券，分页；status 为 unused/locked/used/expired，可传 orderAmount 筛选当前可用券 |

领券中心可能包含当前用户已领模板；每人限领规则由领取接口保证。quantity 为零、尚未开始、已到期、停用或删除的模板不可新领取。库存竞争和重复领取使用数据库事务及行锁保护。

我的券返回 id、couponId、name、amount、minimumAmount、startTime、expireTime、status、usedOrderId、createTime。`id` 是用户券 ID，`couponId` 是模板 ID。orderAmount 仅用于展示筛选，结算以服务端读取的购物车价格为准。

## 订单、支付与退款

下单可选传 `userCouponId`（我的券的 id），例如：

```json
{"addressId":1,"requestId":"coupon-order-001","userCouponId":10}
```

每单最多一张券，不叠加。商品原价合计须达到门槛，且必须大于券面额，确保应付金额大于零。订单返回 userCouponId、discountAmount、totalAmount、payAmount；payAmount = totalAmount - discountAmount。未使用优惠券时 discountAmount 为 0。

下单事务将券从 unused 改为 locked 并绑定订单；库存或其他步骤失败时整体回滚。使用相同 requestId 重试返回原订单。不同订单不能重复占用同一张券。模板停用或删除不会影响已领取的有效券。

支付成功回调在同一事务将券核销为 used。下单时有效、随后到期的锁定券，在该订单原有支付时限内仍可核销。支付失败不立即退券，订单仍可重试支付。未支付订单取消或超时后释放券，尚在有效期则恢复 unused，否则变为 expired，并清除 usedOrderId。退款按实际支付金额办理，已核销券不返还。

## 数据库与验证

Flyway 迁移 `V1.7__coupon_workflow.sql` 增加用户券快照、领取唯一索引和订单优惠字段。升级前如有人工导入的历史领券数据，应先检查重复记录：

```sql
SELECT user_id,coupon_id,COUNT(*) FROM user_coupon
GROUP BY user_id,coupon_id HAVING COUNT(*)>1;
```

重复记录须按实际核销历史处理后再迁移，迁移不会自动删除用户权益。孤立历史券无法补全快照时会拒绝用于新订单。

自动化集成测试覆盖领取限额、并发领取、权限、有效期和门槛、快照、订单回滚、支付核销、取消返还及实付退款。测试数据库使用 H2 的 MySQL 兼容模式；上线前需在目标 MySQL 环境执行迁移和并发验收。
