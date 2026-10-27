# 订单接口

结算预览、提交顺序、Token 幂等、聚合降级、资源补偿、订单号和测试说明见 [结算预览与提交订单](order-checkout.md)。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/v1/orders/preview | 从当前用户已选购物车项聚合结算，签发 Token |
| POST | /api/v1/orders | 按结算 Token 幂等提交，兼容旧 requestId |
| GET | /api/v1/orders | 当前用户订单分页 |
| GET | /api/v1/orders/{id} | 当前用户订单详情 |
| PUT | /api/v1/orders/{id}/cancel | 幂等取消待支付订单 |
| PUT | /api/v1/orders/{id}/confirm-receipt | 幂等确认完成 |
| GET | /api/v1/admin/orders | 管理员分页，order:read |
| GET | /api/v1/admin/orders/{id} | 管理员详情，order:read |
| PUT | /api/v1/admin/orders/{id}/process | 开始处理，order:write |

用户接口要求普通用户 access token。分页支持 page（默认1）、size（默认20，最多100）、status、orderNo。

订单状态只经 OrderStateMachine：pending_payment → paid/cancelled；paid → processing/refunded；processing → completed/refunded；completed → refunded。
新订单支付有效期30分钟；下单锁库存，取消释放，开始处理时消费预占。地址与商品为快照，地址手机号在数据库中加密保存。
优惠券在下单时 locked，支付后 used；金额及优惠分摊仅由服务端计算。

数据库需执行至 V1.16__checkout_orchestration.sql。未登录401、权限不足403、业务校验400、提交处理中409、关键报价服务不可用503。
