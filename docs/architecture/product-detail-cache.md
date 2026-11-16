# 商品详情缓存方案

## 缓存边界

详情拆成稳定内容与易变交易展示数据。商品名、摘要、封面和主图、介绍、图文详情、服务承诺、评价汇总、展示属性进入缓存；数据库仍是商品中心的数据源。
商品状态、价格、SKU、每 SKU 库存、可选组合不进入该缓存。每次详情响应均从商品中心、营销和库存服务实时读取，并在读取稳定内容后重新验证 SPU 是否仍可公开。
这样缓存只优化详情多表读取，避免缓存命中时展示过期价格、库存或错误的可售状态。实时价格/库存请求仍是详情热点的底层成本；进一步优化须由所属服务提供有版本的短 TTL 查询缓存或批量读接口。

## 三层配置

| 层 | 当前配置默认值 | 容量 / 淘汰 | 说明 |
| --- | --- | --- | --- |
| Caffeine L1 | 写入 20 秒 | 最多 5000 条，Window TinyLFU 准入、频率/近期综合淘汰 | 进程内，每实例单独占用；每个 Redis 命中填入时保留原逻辑到期时间，不因访问续期 |
| Redis L2 | 稳定详情 10 分钟 + 0–2 分钟随机抖动；负缓存 20 秒 | 每条序列化对象最多 512 KiB；Redis 全局容量由部署内存和淘汰策略约束，不在应用内假设固定最大键数 | 共享热点数据；物理 TTL 略晚于逻辑 TTL，最多留 30 秒用于短暂旧值服务 |
| DB / 所属服务 | 无缓存 | MySQL 索引、连接池、限流与熔断 | L2 冷未命中或超过旧值宽限时回源；库存和价格每次实时读取 |

全部参数可用 `MALL_DETAIL_*` 环境变量配置，见 `application.yml`。生产 Redis 应设置有界内存及适合业务的淘汰策略，并监控 evicted_keys；逻辑条目 TTL 到达后缓存服务发起刷新，物理 TTL 仅负责回收。详情序列化超限时跳过缓存，不截断业务字段。

## 穿透与击穿

不引入布隆过滤器。商品 ID 通常有效集合变化快：新增、删除、类目迁移、状态变化都要求同步维护过滤器；过滤器误判还会错误隐藏新商品。短空值缓存更简单，缺失商品 20 秒负缓存，并以请求级 Redis 锁合并并发首查。回源确认存在但当前商品/类目不可公开时返回 404 且不负缓存，防止上下架瞬间把有效详情变成负值。

逻辑过期后，第一个成功执行 Redis `SET NX PX` 的节点获得每商品 10 秒租约，异步回源并刷新；其他请求在宽限期内立即读取最多陈旧 30 秒的稳定内容。L2 空 miss 请求竞争同一把分布式锁，其他节点等待最多 2 秒并轮询结果；随后返回宽限内旧值，过宽限则快速失败给重试，不同时打爆数据库。每 JVM 的 Caffeine 只负责单进程命中，不单独充当集群互斥锁。

锁采用唯一 token、Redis `SET NX PX` 和 Lua 比对后释放。锁租约不能提供无限时长的绝对单飞保证：线程暂停超过租约或 Redis 网络分区时，可能有第二个持锁者。分布式数据库连接池限流和 Resilience4j 熔断进一步限制故障扩散；生产配置让锁租约覆盖回源 P99、重试预算和调度延迟，并监控超租约比例。版本栅栏让失效前启动的重建无法覆盖失效后的数据。极端分区下允许源站请求被限流/熔断而快速失败，避免无界并发。

## 雪崩、降级与热点

正缓存 TTL 每次随机增加 0–120 秒，负缓存单独使用短 TTL，避免同批预热键同时过期。逻辑过期将普通重建移出请求关键路径。缓存读取失败时以唯一锁重建；Redis 整体不可用时不返回缓存中的动态售价、库存或上架状态，依靠所属服务的熔断/舱壁、连接池限流和 503 降级。

通过 Micrometer/Actuator 采集 Caffeine hit/miss/eviction、Redis 命中率与延迟、重建锁获取/等待/超时、逻辑过期、负缓存、源站回源量、详情 404、刷新失败和内容字节数。当前服务已有 actuator，但具体指标导出与仪表盘由部署配置开启。用 Redis `OBJECT FREQ`/应用访问计数和高频 Key 告警发现热点；只允许预热已公开商品的稳定内容，不预热价格、库存。单 Key 高频通过 L1 和逻辑过期吸收，多实例仍以 Redis 合并重建。

## 一致性结论

采用**事务提交后删除稳定详情缓存**，不把数据库写入后的新内容主动写入缓存。DB 是事实源，删除动作幂等；下次读经商品中心重建。商品编辑、状态变化、删除及类目移动通过 `ProductDetailChanged` 事务事件清 L1/L2。

价格和库存归各自服务所有，本模块既不缓存，也不尝试从商品编辑事件推断价格库存。若将来在所属服务采用缓存，应由其价格版本/库存版本事件主动删其本地键，或消费者按版本更新；下单仍必须由交易链路重新校验。

不需要延迟双删。延迟双删只是概率补偿，依赖固定睡眠时间，增加延迟且不能证明旧读写一定结束。这里事务提交后先增加 Redis 版本栅栏并删除键；缓存重建读取当前版本，在 Lua 脚本里只允许相同版本写入。并发旧读拿旧版本时即使晚于删除，也不能回填 Redis。每次成功写 Redis 只在 Lua 成功后写入本机 L1。

单 JVM 的 L1 无法由 Redis `DEL` 原子地广播删除；依靠 20 秒上限 TTL 保证本机稳定内容有界陈旧，跨节点精确一致性可再引入 Redis Pub/Sub invalidation channel。类目变更使用同样事件清理相关商品；生产类目树/商品所属关系若批量移动，应发送受影响商品 ID 或版本化整个类目详情命名空间。

回源还会对商品与类目当前公开状态做实时校验。缓存值只有静态文案和展示元数据；即便状态变更事件延迟或消息丢失，下架内容也不会因缓存命中而返回。

## 核心流程伪代码

```java
Optional<StableDetail> get(long id) {
    Entry local = caffeine.getIfPresent(id);
    if (local != null && local.logicalExpiry > now()) return local.value;

    Entry shared = redis.get(id);
    if (shared != null && shared.logicalExpiry > now()) {
        caffeine.put(id, shared); // preserve original expiry, do not extend it
        return shared.value;
    }
    if (shared != null && shared.isWithinStaleGrace()) {
        if (redis.setNx(lock(id), token, lease)) refreshAsync(id, token);
        return shared.value; // immutable stable fields only
    }

    if (!redis.setNx(lock(id), token, lease)) return awaitWinnerOrFailFast(id);
    try {
        long generation = redis.currentFence(id);
        Optional<StableDetail> fresh = productCenter.readStableDetail(id);
        if (generation == redis.currentFence(id)) {
            redis.set(id, fresh, randomizedTtl);
            caffeine.put(id, fresh);
        }
        return fresh;
    } finally {
        redis.compareAndDelete(lock(id), token);
    }
}

void afterProductDbCommit(long id) {
    redis.incrementFenceAndDelete(id); // one atomic Lua operation
    caffeine.invalidate(id);
}

Detail detail(long id) {
    StableDetail stable = get(id).orElseThrow(NotFound::new);
    LiveCard product = productCenter.readCurrentStateAndPrice(id);
    LiveMatrix skus = aggregate(marketing.readCurrentSkuPrices(id), inventory.readCurrentSkuStock(id));
    return combine(stable, product, skus);
}
```

实际实现位于 `ProductDetailCacheService`；Redis 故障时缓存旁路失败快速返回，不应改为无界 DB 穿透。Resilience4j 可在服务部署配置中对数据库连接与调用线程配置舱壁/熔断，阈值基于压测校准。

## 压测关注点

使用 Gatling/k6 对详情 ID 分布分别测试 Zipf 热点（如 80/20）、均匀冷读、缓存预热、整点批量失效、Redis 丢包/重启、商品回源延迟和单商品突发。逐步加压到目标 RPS 的 1.5–2 倍，并分别运行单实例与实际生产节点数。

记录端到端及缓存/商品/营销/库存各自 P50/P95/P99、吞吐、错误率、Redis OPS/内存/命中率、L1 eviction、DB QPS/活跃连接/锁等待、价格和库存调用放大、重建锁成功率及重复回源数。逻辑过期并发实验里同一 productId 一次过期应最多一次持锁回源；Redis 故障实验需确保 DB QPS 不超过舱壁上限。验证状态更新后缓存没有旧公开数据，价格/库存不因详情缓存命中而冻结。压测样本需覆盖大图文详情、缓存超尺寸跳过、负缓存过期和热点迁移。
