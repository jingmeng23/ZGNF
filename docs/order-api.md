# 订单接口
+
+用户接口要求普通用户 access token，管理员接口要求管理员 access token 和订单权限。
+所有响应沿用项目的 Result 包装，以下字段均位于 data。
+
+| 方法 | 路径 | 说明 |
+| --- | --- | --- |
+| POST | /api/v1/orders | 从当前用户已选购物车项下单 |
+| GET | /api/v1/orders | 当前用户订单分页 |
+| GET | /api/v1/orders/{id} | 当前用户订单详情 |
+| PUT | /api/v1/orders/{id}/cancel | 取消待支付订单 |
+| PUT | /api/v1/orders/{id}/confirm-receipt | 确认处理完成 |
+| GET | /api/v1/admin/orders | 管理员订单分页，需 order:read |
+| GET | /api/v1/admin/orders/{id} | 管理员订单详情，需 order:read |
+| PUT | /api/v1/admin/orders/{id}/process | 已支付订单开始处理，需 order:write |
+
+下单请求：
+
+```json
+{"addressId": 1, "requestId": "client-unique-order-001", "remark": "请提前联系"}
+```
+
+requestId 为客户端为一次下单生成的唯一编号（1 至 64 位字母、数字、下划线或连字符）。
+同一用户用同一个 requestId 重试，会返回第一次创建的订单。每次新的下单应使用新编号。
+remark 可选，最多 500 字。地址必须属于当前用户。购物车必须有已选商品，所有商品须上架、
+分类有效、数量不超过可用库存；订单总额不能超过 99999999.99。
+
+列表查询支持 page（默认 1）、size（默认 20，最多 100）、status、orderNo。
+订单数据包含 id、userId、orderNo、status、totalAmount、payAmount、remark、
+createTime、expireTime、address、items。地址和商品信息为下单时的快照，
+地址手机号在数据库中加密保存，接口返回时解密。
+
+## 状态与库存
+
+- 新订单状态为 pending_payment，支付有效期 30 分钟。下单事务预占库存，记录具体库存行；
+  购物车中的已选项会软删除，未选项保留。
+- 用户取消或超时取消会释放预占库存，状态变为 cancelled。重复取消不会重复释放。
+- 支付模块后续应在支付回调中核验订单和支付金额，再将待支付订单更新为 paid。
+  订单模块本身不提供向客户端开放的“支付成功”接口。
+- 管理员只能将 paid 订单转为 processing，此时实际扣减库存并清除预占。
+- 用户只能将 processing 订单转为 completed。当前没有物流单独状态，
+  因此前端应在服务/配送确已完成时显示确认入口。
+- 定时扫描每分钟取消过期订单；RabbitMQ 超时消息如果到达，也调用同一幂等取消逻辑。
+
+下单时价格会锁定为订单商品快照。支持可选 userCouponId 满减券，返回 discountAmount 和优惠后的 payAmount，详见 [优惠券 API](coupon-api.md)。当前不计算运费和税费。
+订单号使用项目现有 Redis 序列生成器，创建订单需要 Redis 可用。
+参数和业务冲突返回 HTTP 400；未登录返回 401；权限不足返回 403。
+
+数据库部署时需执行 V1.2__order_reservations.sql。
+支付回调、退款、发货物流等流程属于后续支付/履约模块的集成范围。
+
