# 用户订单列表与操作

所有用户接口从登录凭证取得 userId，不接受请求指定用户。列表强制 user_id 条件，详情根查询和写操作行锁均同时使用 id+user_id+is_deleted；归属校验失败与不存在统一返回错误，未查询子表或调用物流/释放服务。用户必须为 active，未登录返回 401。管理员管理接口独立授权，不与用户查询混用。

|接口|行为|
|---|---|
|GET /api/v1/orders?status=paid&page=1&size=20|状态 Tab 浅分页，省略 status 查询全部；size 1–100|
|GET /api/v1/orders/scroll?status=paid&size=20&afterId=123|深分页；首次省略 afterId，后续传 nextCursor；不计算总数|
|GET /api/v1/orders/{id}|主表/商品快照/地址、timeline、payments、可选 logistics|
|PUT /api/v1/orders/{id}/cancel|仅 pending_payment 可取消；cancelled 重放成功|
|PUT /api/v1/orders/{id}/confirm-receipt|processing→completed；completed 重放成功|

固定按 create_time DESC,id DESC 排序，时间相同由 ID 保证稳定顺序，不根据前端 sort/order 改变。超过 10000 offset 的用户列表请求被拒绝，应改游标接口；管理导出应使用独立批处理。V1.19 增加两个索引：

```sql
CREATE INDEX idx_order_user_tab
 ON `order`(user_id,is_deleted,status,create_time,id);
CREATE INDEX idx_order_user_all
 ON `order`(user_id,is_deleted,create_time,id);
```

前缀等值限定当前用户、逻辑删除、状态 Tab，后缀沿 B-tree 反向扫描排序；全部 Tab 使用不含 status 的第二个索引，避免跨状态排序。order_item 通过已有 order_id 索引，按本页订单 ID 批量读取，避免 N+1。浅分页返回 total/pages；深分页取 size+1 决定 hasMore，不做 COUNT 和大 OFFSET：

```sql
SELECT * FROM `order`
 WHERE user_id=? AND is_deleted=0 AND status=?
 AND (create_time<? OR (create_time=? AND id<?))
 ORDER BY create_time DESC,id DESC LIMIT ?;
```

游标是上页末行 ID，服务端从同一用户订单读取实际排序时间，不相信客户端传入时间。外部用户的 afterId 被拒绝；切换 Tab 应重启分页，锚点状态已变化或删除则返回错误提示重启。分页期间新插入的订单位于前面，不导致后续重复；这不是跨请求冻结快照，订单状态改变可能使记录移入移出 Tab。应在生产 MySQL 用 EXPLAIN 检查索引与扫描行数。

详情保留原有顶层字段，增加 timeline（id/fromStatus/toStatus/occurredAt）、payments（支付号/渠道/状态/金额/支付时间）。不暴露渠道交易号或签名凭据。主订单先鉴权，时间轴查询再 JOIN 用户订单，支付查询同时限定 user_id+order_id，避免错挂支付记录泄露。主从本地读取使用只读可重复读事务。列表与写接口响应不执行额外支付、时间轴或物流查询。

状态变更统一进入 OrderStateMachine.persistTransition：条件更新、OrderStatusLog 插入与业务效果同一事务；取消、支付、处理、确认收货、退款均记录。初始化在下单事务记录首条日志，幂等重放不新增日志。历史订单迁移仅补一条当前状态快照，不伪造历史操作时间。时间轴按日志 ID 保证事务顺序。

物流通过可选 OrderLogisticsGateway 聚合；未接入、返回空或异常时响应中省略 logistics，订单与支付信息照常返回，并增加 order.detail.logistics.degraded 指标。适配器必须设置有界超时，不在订单服务中拉取或计算物流轨迹。

取消会先校验归属/状态并锁订单，再走支付关闭保护，不能把已收款订单误当未支付；渠道未知保留原状态并报错，发现成功支付则返回实际 paid 状态。成功取消后持久化释放任务并按库存→优惠券→积分补偿，详见 order-timeout.md。确认收货使用相同归属行锁和状态条件更新，两个并发请求仅产生一次 completed 日志，之后重复请求返回成功。其它状态拒绝操作，不允许 completed 之后重复发起新的状态变更。

自动测试覆盖状态 Tab/分页、同秒游标、深分页限制、跨用户游标、详情/取消/确认收货水平越权、支付子记录隔离、物流异常隐藏、重复取消、并发确认收货、非法状态及停用用户。真实物流适配器仍由下游服务接入。
