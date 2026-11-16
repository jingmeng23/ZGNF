# 购物车 MySQL 表

独立建表脚本：[sql/cart-mysql.sql](../../scripts/sql/cart-mysql.sql)。适用于 MySQL 8.0 及以上版本，执行前先选择目标数据库。脚本与当前 CartItem 实体字段保持一致，不删除或覆盖已有数据。

## 使用方式

项目正常启动时优先使用 Flyway：V1.0 创建基础 cart_item 表，V1.11 增加价格、名称及封面快照并回填已有商品信息。不需要再执行独立脚本。不要在一个准备由 Flyway 初始化的空数据库中提前手工建表，否则可能导致 Flyway 非空 schema 校验失败。

手工维护、未使用 Flyway 的数据库，可在 MySQL 客户端选择数据库后执行：

```sql
USE gas_ecommerce;
SOURCE /absolute/path/to/scripts/sql/cart-mysql.sql;
SHOW CREATE TABLE cart_item;
```

CREATE TABLE IF NOT EXISTS 只负责新建；已有旧表不会自动增加列。已由 Flyway 管理的旧表应通过迁移升级。

## 字段与索引语义

- 每个用户和商品只保留一条记录，唯一键为 `(user_id, product_id)`。软删除后再次加购应恢复原记录，与现有 Mapper.restore 一致。不要把 is_deleted 加入唯一键，否则会改变恢复逻辑。
- `(user_id, is_deleted)` 索引用于查询当前用户未删除的购物车；所有业务查询和写入仍必须验证用户归属，索引本身不提供访问隔离。
- 价格使用 DECIMAL(10,2)，避免浮点误差。快照价格只用于展示和比较；小计及选中总额由服务端使用当前商品价格计算，下单时再次校验。
- invalidItems、priceChanged、degraded、可用库存和汇总金额是动态返回字段，不作为购物车表的持久化列。
- 不增加数据库外键，以兼容现有商品逻辑删除和快照降级展示；用户及商品有效性由服务层校验。
- 当前幂等保护使用 Redis；此建表脚本不声明数据库幂等或埋点事件已经持久化。

本次仅生成建表脚本，未连接或修改实际 MySQL 数据库。
