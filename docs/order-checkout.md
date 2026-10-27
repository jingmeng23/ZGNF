# 结算预览与提交订单

## 1. 服务端聚合与失败降级

用户接口要求普通用户 access token，统一响应为 Result，业务字段在 data。

`POST /api/v1/orders/preview`

```json
{"addressId":1,"userCouponId":10}
```

从当前用户已选购物车项结算。订单只编排，不复制商品、营销、地址或库存业务：

| 下游服务 | 负责内容 | 关键数据失败策略 |
| --- | --- | --- |
| ProductCheckoutService | 商品可售状态、最新价格，调用库存服务查询可用量 | 停止预览，基础设施异常返回 503；不使用购物车历史价格 |
| CheckoutMarketingService | 满减、优惠券门槛、叠加规则、优惠报价 | 停止预览并返回 503；不偷偷取消优惠或把优惠写成零 |
| AddressCheckoutService | 地址归属、可达性、权威运费 | 停止预览并返回 503；不可达/缺少配送规则返回业务错误，不默认免邮 |

业务错误（下架、库存不足、券失效、不可达）返回 400；下游故障返回 503 / 503001。
只有关键数据全部成功才签发可提交 Token。商品图片等非计价字段允许为空，客户端可展示占位图；不能用展示降级掩盖计价失败。
当前适配器是进程内模块服务，不伪造 HTTP 调用或网络超时配置。以后替换为 RPC 时必须在适配器配置连接/读取超时；超时按上述策略失败，不能无界等待。

金额公式：`商品总额 - 满减 - 优惠券 + 运费 = 应付金额`。前端传入的价格、应付、优惠、运费均不参与计算。
当前不支持积分抵现及税费计算，不接受客户端积分抵扣金额。
满减由营销模块选择当前有效且门槛满足的最大优惠一次；在满减后金额上检查优惠券门槛。规则来自 `checkout_full_reduction`，迁移不会捏造营销活动。
运费与可达性来自地址模块的 `checkout_delivery_rule`，省/市/区越精确的匹配优先，`*` 为通配。
生产默认不免邮兜底；测试为兼容旧数据显式开启 `app.checkout.delivery.legacy-free-shipping-enabled`。该开关只用于缺少规则时的旧系统兼容，服务故障仍然返回 503。

预览返回：`orderNo`、`checkoutToken`、`expireTime`、`address`、`items`、`totalAmount`、`discountAmount`、`fullReductionAmount`、`couponDiscountAmount`、`freightAmount`、`payAmount`。
优惠采用累计比例向下取整到分、最后一行承担尾差，明细优惠与行应付合计守恒；订单应付等于明细应付合计加运费。
Token 为 SecureRandom 生成的 256 位随机数（64 位十六进制），数据库只存 SHA-256 摘要，绑定用户、订单号和报价指纹，有效期 15 分钟。
预览不创建订单，不锁库存、不占券、不扣积分、不删除购物车。

## 2. 提交顺序与事务边界

`POST /api/v1/orders`

```json
{"orderNo":"预览返回的订单号","checkoutToken":"预览返回的64位Token","addressId":1,"userCouponId":10,"remark":"请提前联系"}
```

可选请求头 `Idempotency-Key` 必须等于 checkoutToken。备注最多 500 字，商品数量 1—999，总额上限 99999999.99。

1. `@CheckoutIdempotent` 校验键和请求头；持久化 `order_submission`，唯一键取得本次执行权。先查询历史结果，再校验未使用 Token 的归属和有效期。
2. 调用报价聚合服务重新结算，比较商品、购物车、地址、优惠及运费指纹。变化时要求重新预览，不直接接受新金额。
3. 按订单号调用库存 `lockCheckout`，在库存自己的短事务中锁定资源，记录具体库存行及幂等记录。
4. 按订单号调用营销 `reserve`，在营销自己的短事务中重新验证并占用优惠券、固定满减报价。
5. 调用积分资源端口；当前项目没有积分账户/扣减服务，默认实现明确不扣积分。后续真实积分服务必须按本接口提供幂等释放与延迟预占拦截，不能只调用远程扣减而没有回滚协议。
6. 最终本地短事务复核执行权、购物车、商品价格和地址，保存订单/明细/金额分摊/地址快照，关联本地资源记录，软删除选中购物车项，同时写 order.created、order.timeout 消息及 SUCCEEDED 标记。
7. 提交后发布器投递超时消息；不会在数据库提交前直接发 MQ。MQ 暂不可用不把已创建订单误判为失败，消息留在本地表等待补发。

“下单优惠核销”使用可逆的 locked 预占；支付成功才变为 used，避免未支付订单提前永久耗券。
没有全局大事务或分布式事务框架。库存/营销预占独立提交，订单事务失败必须显式补偿。
当前部署仍是同库模块化单体，最终事务里的 bind 仅关联数据库内部 ID；远程资源始终按订单号识别，不应把 bind 实现成依赖订单事务的远程写入。

订单状态只能经 OrderStateMachine 初始化或流转，支付与售后现有调用也统一到这个入口。

## 3. 幂等方案与三种重试

选择 **结算 Token + 数据库唯一约束**，不使用 Redis SETNX 作为正确性基础，也不套用旧的通用 IdempotentAspect。

- `order_checkout_ticket.token_hash` 唯一，`order_submission.token_hash` 唯一。
- `order_submission(user_id,request_key)` 唯一；订单表 `order_no` 和 `(user_id,request_id)` 唯一。
- 同一用户的短用户行锁串行化认领；库存、营销各自有 `(resource_type,order_no)` 幂等记录，允许资源调用安全重试。
- 同键请求参数摘要必须一致。成功重放不重新计价、不再锁资源；即使预览过期、购物车已空、产品已改价，也返回原订单。
- 执行者有 2 分钟租约，步骤间续约。最终保存持有提交记录行锁，并检查 owner、phase、lease，防止旧执行者在补偿后保存订单。

| 场景 | 行为 |
| --- | --- |
| 重复提交，第一次已完成 | HTTP 200 返回原订单，订单、预占及超时消息均只有一份 |
| 网络超时/响应丢失 | 用相同 Token 和参数重试；已成功重放，尚在处理短暂等待最多 2 秒，仍未完成返回 HTTP 409 / 409001 |
| 用户连续点击/并发提交 | 唯一键只允许一个执行者；其他请求等待原结果或收到处理中，不创建第二单 |

客户端收到 409 后，可用同 Token 重试或用 `GET /api/v1/orders?orderNo=...` 查询。不要为网络重试生成新 Token。
失败且资源释放完成的提交为 FAILED，原 Token 不重新占用资源，必须重新预览获取新 Token。这是为防止补偿与重试交错，不是丢弃网络重试结果。
兼容旧客户端仅传 requestId 的调用（1—64 位字母、数字、下划线或连字符）：仍持久化幂等、服务端重新计价和补偿，但没有预览价格确认能力，新客户端应使用 Token 协议。

## 4. 资源回滚与崩溃补偿

正向为 **库存 → 优惠 → 积分 → 订单落库**，反向为 **积分 → 优惠券 → 库存**。下单预占不物理扣减库存；失败只释放 locked_quantity。
调用返回超时并不等于下游失败，因此补偿按订单号对所有资源执行，不依赖内存里的“调用成功”标记。
每个释放独立事务提交且幂等；某一项失败仍继续释放其他项。

- 失败先在本地事务把提交标记为 COMPENSATING，并写 order.compensate 本地消息，再尝试立即释放。
- MQ 消费与每 30 秒扫描共同兜底。进程在资源预占后崩溃时，过期 PROCESSING 记录也会进入补偿。
- 释放全部成功才标记 FAILED。部分失败保留 COMPENSATING、错误及次数，30 秒后重试；恢复进程再次崩溃则由租约到期后接管。
- 资源服务保留 released 墓碑；释放先到、预占后到时拒绝预占，避免网络乱序导致库存或优惠券再次被占。
- 最终订单与 SUCCEEDED 同事务提交；补偿认领锁定提交记录，并复查订单存在性，绝不释放已成功订单的资源。
- 订单事务提交结果不确定且数据库不可用时，不盲目释放资源；保留持久化记录，数据库恢复后核对再处理。

积分端口的失败/丢失响应和释放失败已用模拟下游测试；仓库没有真实积分账户服务，不能声称已完成真实积分扣减或返还。
成功订单的取消/超时关单沿用现有状态机和资源释放；本次补偿表专门处理尚未成功落库的提交。

## 5. 订单号与可靠消息

采用 **数据库持久化日期 + 序列**：`ODyyyyMMdd + 12位序列`，日期为 Asia/Shanghai，可读、可排序。
所有应用实例共享 `order_number_sequence`，行锁分配序列，REQUIRES_NEW 单独提交；业务回滚不回收序列，不把异常或空序列兜底成 0。
每天最多 999999999999 个，耗尽明确失败；日期回拨也只会继续对应日期的持久序列。新 OD 前缀避免与旧 Redis GAS 编号冲突。
订单号唯一约束再次兜底。全局唯一的前提是同一分配数据库，不能把互不相通的数据库各自从 1 开始后仍宣称全局唯一；未来分库需增加已分配的命名空间或统一号段服务。

Outbox 每 10 秒最多处理 100 条，使用持久消息、mandatory、publisher confirm、退回检查，确认成功才标记 sent；失败按 5 秒递增、最高 300 秒间隔重试。
稳定 messageId 和 `(order_no,event_type)` 保证可识别重复投递。Broker 已接收但发送方在更新 sent 前崩溃，仍可能重复发送，消费方必须幂等。
order.timeout 走现有 30 分钟延迟队列，已有超时扫描负责消息延迟/丢失兜底；order.compensate 走 gas.order.compensate。

## 6. 代码与测试

| 组件 | 路径（相对于 src/main/java/com/gas/ecommerce） |
| --- | --- |
| Controller | modules/order/controller/OrderController.java |
| 编排 Service | modules/order/service/impl/OrderServiceImpl.java |
| 聚合结算 | modules/order/service/CheckoutSettlementService.java |
| 最终订单事务 | modules/order/service/CheckoutOrderWriter.java |
| Token/执行权 | modules/order/service/CheckoutTicketService.java、OrderSubmissionStore.java |
| 注解与拦截切面 | modules/order/annotation/CheckoutIdempotent.java、CheckoutIdempotentAspect.java |
| 补偿与任务 | modules/order/service/CheckoutCompensationService.java、task/CheckoutCompensationTask.java |
| 订单号 | common/utils/OrderNoGenerator.java |

单测目录为 `src/test/java/com/gas/ecommerce/modules/order/service/`，包含编排顺序、失败分支、降级策略、反向补偿、注解校验、双实例并发发号和业务回滚不回收编号。
`AdminCatalogIntegrationTests` 覆盖 MockMvc Token 协议、连点/并发重试、运费满减叠加、资源预占幂等、崩溃恢复、释放失败重试、延迟预占拦截和已有支付/取消/优惠券回归。
`OrderOutboxPublisherTests` 覆盖 broker ack、nack 重试和连接失败（使用模拟 broker，不替代真实 RabbitMQ 联调）。

部署须按顺序执行 V1.15、V1.16 迁移。原 V1.15 预览凭证没有 Token 摘要，部署后需重新预览。
需由地址服务配置配送范围/运费；不应以开启兼容免邮开关代替生产规则。迁移不预置满减活动或配送范围。
配置：`app.orders.outbox-enabled` 默认 true、`outbox-delay-ms` 默认 10000、`compensation-enabled` 默认 true、`compensation-delay-ms` 默认 30000。测试关闭后台投递/补偿，显式调用服务验证恢复。
