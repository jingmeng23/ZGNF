# 未支付订单超时关单

|方案|优点|局限|适用|
|---|---|---|---|
|RabbitMQ TTL+死信/延迟插件|触发及时，消费者可水平扩展|依赖 broker；TTL 队列可能队头阻塞，死信转发仍可能丢失；插件需运维|交易主路径|
|Redis ZSet|可动态调整执行时间，按 score 批量领取|需 Lua 原子领取、租约和处理中集合；Redis 故障/持久化丢失需补偿|已有可靠调度基础设施、时间可变任务|
|数据库扫描|订单为事实源，易恢复、无额外依赖|精度取决于间隔；需索引、限流和处理热点|低流量主路径或生产兜底|

本项目采用持久化 order_outbox→RabbitMQ 固定 30 分钟 TTL 队列→死信关单消费者；每 60 秒数据库扫描逾期 pending_payment 订单兜底。消息只携带订单号，消费者再次读取状态及 expire_time。outbox 发布使用 mandatory/confirm/持久化消息并重试；丢消息由扫描补回；重复消息、多实例扫描均进入同一事务关单入口。消费者遇到未知支付等异常记录错误并交给数据库扫描，避免消息立即重入形成热点。生产必须启用 expiry-scan-enabled。

OrderServiceImpl.cancelExpired 为关单服务，OrderTimeoutTask 为消费者，OrderExpiryScheduler 为兜底；OrderCloseReleaseService/OrderCloseReleaseTask 为持久化资源释放与扫描。V1.18 增加释放进度表和到期扫描索引。关单事务先锁订单、调用 PaymentCloseGuard 确认渠道关闭或发现成功支付，再通过状态机执行条件更新，并同事务插入唯一 order_id 释放任务。

```sql
UPDATE `order` SET status='cancelled',update_time=CURRENT_TIMESTAMP
 WHERE id=? AND status='pending_payment' AND is_deleted=0
 AND NOT EXISTS (SELECT 1 FROM payment p
 WHERE p.order_id=`order`.id AND p.status='success' AND p.is_deleted=0);
```

超时入口仅在 expire_time<=当前时间时调用上述状态机；主动取消复用状态机但不要求已到期。多实例均先锁同一订单，只有第一个可从 pending_payment 变为 cancelled，后续直接返回，不重复插入释放任务。渠道状态未知不关单，已支付优先；误关后迟到成功走支付退款补偿，详见 payment-callback.md。

提交后立即尝试库存释放→优惠券退还→积分回滚，每步独立事务保存 step；失败停止后续步骤，记录 attempts/last_error，60 秒后可重试，后台每 30 秒扫描最多 100 条。进程在提交后崩溃也由进度表恢复。库存/券当前在本地事务内调用所属服务；积分是幂等扩展端口，必须按 orderNo 去重并阻断延迟预占。远程成功但本地未提交会重放当前步骤，下游必须保证幂等。不能对已经成功释放的步骤再做反向预占。取消成功只代表订单已关闭，释放任务可能仍在补偿。

每次释放失败产生 ERROR 事件 ORDER_CLOSE_RELEASE_FAILED 及 Micrometer order.close.release.failures 计数；运维应配置该计数增长或 step<3 且长期积压的数据库监控告警。当前没有外部告警渠道，不宣称已经发送短信/钉钉通知。任务持续重试，不自动丢弃失败记录。

建议从订单成功落库时刻计算固定 30 分钟，expire_time 持久化；发起支付、切换渠道、重复点击均不延长。29:59 已收款而回调晚到：渠道关闭返回成功则优先支付；超时未完成的支付会话必须渠道明确关闭后才能释放。未支付订单不允许通过支付动作无限占库存。业务确需延期应显式变更截止时间并设最大占用时长；本实现不提供该能力。

固定 TTL 从消息入队计时，outbox 堵塞会导致消息晚到，数据库扫描仍以订单截止时间处理；提前消息不会提前关单。数据库时间与应用节点须同步；误差较大时需统一数据库时间判断。每轮扫描固定数据库截止时间和最大订单 ID，按 ID 游标每批读取 100 条直到本轮窗口结束；失败订单也推进游标，下一轮再重试，不会让前 100 条失败订单饿死后续订单。窗口之后的新订单交给下一轮，避免持续插入使扫描无法结束。需监控整轮耗时和最老逾期时间；持续未知的支付渠道应告警处置，不能强行放开库存。

关单支付检查必须在持有订单锁后使用 `SELECT * FROM payment WHERE order_id=? AND is_deleted=0 ORDER BY id FOR UPDATE`。MySQL 可重复读事务可能已经在取得订单锁前建立旧快照，普通 SELECT 会漏掉等待期间提交的新支付单；锁定当前读可避免此窗口。支付创建同样持有订单锁，因而取得订单锁后不能再出现新的并发支付创建。

回归测试包含前 100 条持续失败时后 105 条仍被处理、失败订单下一轮重试和扫描窗口边界；`PaymentCloseGuardMySqlTests` 用真实 InnoDB 可重复读和两个并发事务证明普通查询仍看不到支付单时，生产锁定查询可以发现该支付单并阻止不确定关单。该测试通过 `ORDER_MYSQL_TEST_URL`、`ORDER_MYSQL_TEST_USER`、`ORDER_MYSQL_TEST_PASSWORD` 启用，数据库必须为可丢弃的 `order_p1_test_*` 专用库；测试会清理其中的 order/payment 测试表数据。
