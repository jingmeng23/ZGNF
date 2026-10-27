# 支付回调与补偿

接口：`POST /api/v1/payment/callback/{channel}`。仅供可信支付网关调用，生产渠道 SDK 验签、商户号/币种核对及网络调用由支付服务适配器负责，订单模块不实现渠道协议。

请求头：`X-Payment-Timestamp`（Unix 秒）、`X-Payment-Signature`（十六进制 HMAC-SHA256）。签名原文严格为 `channel + "\n" + timestamp + "\n" + 原始请求体`。密钥至少 32 字节，时间窗 ±300 秒，常量时间比较；验签在 JSON 解析之前。可信网关必须核对原始渠道签名与商户信息后重新签名。TLS、密钥隔离和轮换由部署侧提供。

请求体示例：

```json
{"paymentNo":"PAY-001","transactionNo":"TRADE-001","amount":50.00,"status":"success","eventTime":"2026-10-27 12:29:59"}
```

`transactionNo` 即 tradeNo。回调仅接受 success/failed。支付号必须预先存在，归属、渠道、金额须与本地账本及订单快照一致，不接受回调创建支付单或改变金额。只有本地事务提交成功才返回成功响应，失败让发送方重试。

来源使用真实 socket peer，配置 `PAYMENT_CALLBACK_ALLOWED_PEERS`（IP/CIDR，逗号分隔），空值拒绝全部；不读取 X-Forwarded-For。反向代理部署须在网络层只允许可信网关访问该路径，代理地址白名单不能证明最终调用者身份，HMAC 校验始终必需。

## 双重幂等与乱序

沿用 V1.5 的数据库约束 `UNIQUE(channel,provider_transaction_no)`，不同支付单不能占用同一渠道流水。先锁订单再锁支付单，状态机仅允许 pending_payment→paid，一次成功只生成一组 outbox；outbox 另有 `UNIQUE(order_no,event_type)`。相同流水重复回调返回当前账本状态。success/refunded 不被迟到的 failed 降级，failed→success 可以补正；同一支付号突然换流水则拒绝。重复成功的第二笔支付进入退款补偿，不能重复履约。

## 29:59 支付与 30:00 关单

仅比较本机时间不足以判断收款；支付实际发生后即使回调迟到仍需入账。`PaymentCloseGuard` 在持有订单锁时调用下游原子 close-or-return-success：已支付则通过统一 handler 入账；渠道明确 CLOSED（保证该支付号不能再付款）才允许关单；UNKNOWN、超时或 PENDING 保留预占并重试。失败回调不能充当 CLOSED 证明。所有支付尝试均检查，避免另一个支付号仍可收款。

状态条件更新位于 `OrderStateMachine.persistTransition`，与账本及 outbox 同事务：

```sql
-- 先 SELECT * FROM `order` WHERE id=? AND is_deleted=0 FOR UPDATE;
-- 再 SELECT * FROM payment WHERE id=? AND is_deleted=0 FOR UPDATE;
UPDATE `order` SET status='paid',update_time=CURRENT_TIMESTAMP
 WHERE id=? AND status='pending_payment' AND is_deleted=0;

UPDATE `order` SET status='cancelled',update_time=CURRENT_TIMESTAMP
 WHERE id=? AND status='pending_payment' AND is_deleted=0
 AND NOT EXISTS (SELECT 1 FROM payment p
   WHERE p.order_id=`order`.id AND p.status='success' AND p.is_deleted=0);
```

受影响行必须为 1，否则事务回滚。关单状态更新后才释放库存/券，当前资源服务共享本地事务；拆分远程服务时释放须改为各资源幂等 outbox 消费，不可直接远程调用假定事务回滚。无支付单的订单可直接关单，支付创建也锁订单，因此不能在关单后再创建支付。

若已误关且资源已释放，再收到有效成功：保存 success 账本并在同事务把 `payment_recovery` 改为 refund；订单保持 cancelled，不向履约队列发送消息。定时任务使用稳定退款号 `LATE-{paymentNo}` 全额退款；超时、异常和未确认结果保留 pending，重试同一退款号；仅渠道确认退款完成后账本变为 refunded。不会在库存可能已卖出的情况下复活订单。

## 丢失回调与可靠通知

V1.17 新增 payment_recovery，本地支付创建与 query 工作同事务提交；定时每 30 秒扫描到期任务，最多 100 条，逐条独立事务，并通过行锁避免多实例重复执行。存量 pending/failed/closed 支付会补建任务。认证回查结果进入同一个 PaymentOutcomeHandler，不能绕过金额和流水校验。未知结果保留任务与错误记录，退避重试；closed 也继续检查迟到结算。refund 同样落库并持续补偿，不会因进程重启丢失。

支付成功、优惠券本地核销、订单状态及三条消息共事务写入 order_outbox：payment.stock、payment.points、payment.notification，分别路由到三个独立持久化队列。复用 OrderOutboxPublisher 的 mandatory、持久化消息、publisher confirm 和定时重投。崩溃窗口可能重复投递，消费方应以 messageId 或 orderNo+eventType 唯一 inbox，与本地业务修改同事务。远程调用使用订单号作为下游幂等键；不可先写消费成功再调用下游。

这里选择本地消息表，不额外引入事务消息框架。消息正文为订单号，接收方查询已持久化快照；库存确认、积分和通知属于下游职责。三个队列与可靠投递已实现，下游业务消费者尚需对应服务接入，不把排队等同于积分发放/通知送达。现有库存扣减仍由订单处理流程调用库存服务，新增消息不能重复实现扣减逻辑。

## 适配器与验证边界

`PaymentProviderGateway` 提供 query/close/refund 扩展点，必须验证渠道响应真实性、商户号、币种、金额和支付号，配置有界连接/读取超时。默认适配器返回 UNKNOWN/退款未确认；没有真实渠道集成时不会伪造成功，也不会释放无法确认的支付订单资源。当前同步调用在行锁内执行，应保持严格短超时；高延迟渠道可再演进为持久化 closing 阶段，但不可放开关闭与收款互斥约束。

自动测试包含重复并发回调、流水跨订单重用、失败/成功乱序、超过订单期限的成功入账、关单发现支付、未知关单、关单竞争的两种结果、误关退款、丢回调回查、退款失败重试、双支付退款和验签/来源校验。H2 并发测试验证本地协议，生产 MySQL 及真实渠道关闭语义仍需接入环境验证。题目中的 reference:6 未附内容，未据此虚构引用。
