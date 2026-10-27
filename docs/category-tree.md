# C 端类目树

匿名调用 `GET /api/v1/categories/tree`，返回标准 `Result`，`data` 为根节点数组。
节点字段：`id`、`parentId`、`name`、`sort`、`status`、`children`。
根类目的 `parentId` 为 `0`，叶子节点的 `children` 为 `[]`；无数据时 `data` 为 `[]`。
同级按 `sort` 升序、`id` 升序排列，支持多级；隐藏停用、已删除类目及其后代，隐藏孤儿和循环关系。
原 `GET /api/v1/categories` 保持平铺结构，兼容现有客户端。

数据通过现有商品目录查询服务读取，商城层只构建展示树与缓存，不维护类目副本。
Redis 键为 `mall:categories:tree:v1`，空树也缓存。
配置 `app.mall.category-tree-cache-ttl`，默认 `PT5M`，必须为正时间段。
类目新增、修改、删除在事务提交后删除缓存，回滚不失效。
Redis 故障或缓存内容无效时回源，查询源失败仍正常传播错误。
采用 TTL 与提交后失效的最终一致性：并发回填、Redis 故障或绕过管理服务的外部修改，可能在 TTL 内保留旧数据。

本次仅实现类目树，不涉及价格计算、库存扣减、订单、优惠计算及购物车。
