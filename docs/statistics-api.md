# 经营总览与统计报表 API

管理员接口前缀 `/api/v1/admin/statistics`，需要管理员 access token。V1.10 迁移将 `statistics:read`、`statistics:export` 分配给内置 admin 角色；其他角色通过权限管理授权。统计为全商城范围，不按员工所属门店隔离。

| 方法 | 路径 | 权限 | 功能 |
| --- | --- | --- | --- |
| GET | `/overview` | statistics:read | 经营指标、上期比较、工单积压、订单状态与最近订单 |
| GET | `/trend` | statistics:read | 按日趋势，空日期补零 |
| GET | `/products` | statistics:read | 已支付商品销量排行 |
| GET | `/stores` | statistics:read | 门店工单表现排行 |
| GET | `/reports/daily` | statistics:read | 分页经营日报 |
| GET | `/reports/daily/export` | statistics:export | CSV 导出所选日期全部日报 |

## 查询参数

示例：`GET /api/v1/admin/statistics/overview?startDate=2026-10-01&endDate=2026-10-22`。

- startDate、endDate：`yyyy-MM-dd`，日期两端都包含。endDate 默认上海时区当天，startDate 默认等于 endDate。最大 366 天，起始日期不能晚于结束日期，允许查询空数据或未来日期。
- limit：商品／门店排行返回数量，默认 10，范围 1～100。
- 日报 page 默认 1，size 默认 20，最大 100；sort 支持 date 或默认 createTime（均按日期排序），order 默认 desc，支持 asc。超出末页返回空列表。
- 导出包含整个范围，按日期升序，不受分页参数影响。返回 UTF-8 BOM CSV，文件名 `business-daily.csv`。其他接口使用统一 Result 响应；日报 data 为 PageResult。

日期范围转换为起始日零点至结束日次日零点的左闭右开区间。数据库业务时间按 Asia/Shanghai 本地时间解释。每个请求采用只读、可重复读事务，使同一响应中的多项查询使用一致快照；独立请求间数据可能更新。

## 统计口径

所有统计排除对应业务记录的逻辑删除数据；支付、退款、商品排行也排除已逻辑删除订单。

| Metrics 字段 | 含义 |
| --- | --- |
| createdOrders | 按订单 create_time 统计的新建订单数，包含后来取消、退款的订单 |
| paidOrders | 按 payment.paid_time 统计的已支付订单数 |
| receivedAmount | 实收金额，来自 success 或 refunded 状态支付记录；退款不抹去历史收款 |
| refundedOrders | 按售后 completed_time 统计完成退款的去重订单数 |
| refundAmount | completed 售后单金额合计，待审核、批准未完成或拒绝均不计入 |
| netReceivedAmount | 同期实收减同期退款；跨期退款可使净收款为负数 |
| newCustomers | 按用户 create_time 统计新增用户数，包含停用但未删除用户 |

防止异常历史支付记录重复计算，同一订单选取最小 ID 的未删除、paid_time 非空且状态为 success/refunded 的支付记录。failed/pending 支付不算收款。未经过支付记录的手工已支付订单不推算收款。

当前支付与退款是系统本地记录，统计不代表支付渠道已对账确认的财务收入。

### 经营总览

返回 startDate/endDate、previousStartDate/previousEndDate、current/previous（Metrics）。上期为紧接本期之前的相同天数区间，增长率单位为百分数，保留两位小数；上期基数为零时 receivedGrowthPercent/orderGrowthPercent 为 null，避免生成无意义的增长百分比。比较使用完整日期区间，不表示“昨日同期小时”。

orderStatuses 是本期创建订单的**当前**状态分布（pending_payment/paid/processing/completed/cancelled/refunded）。recentOrders 为本期最新 10 条订单，仅返回 ID、编号、状态、应付金额及创建时间，不包含客户手机号或地址。payAmount 为应付金额，不能作为未支付订单的实收金额展示。

pendingWorkOrders 是当前全部未完成工单数（pending/accepted/processing），overdueWorkOrders 是其中 appointment_time 早于查询时刻的数量。两者不受日期筛选限制，便于提醒积压；无预约时间不视为超时。

### 商品排行

按选定日期已收款订单的商品行统计；quantity 降序、productId 升序。返回 productId、productName、paidOrders、quantity、merchandiseAmount。

商品名来自历史订单快照；同商品存在多个名字时取字典序最大快照名。商品后来删除不影响其历史订单统计。merchandiseAmount 为商品行优惠前金额，**未分摊订单优惠券，也未扣退款**，因此不等于实收金额；销量是支付时的毛销量，后续退款不回减。关联多条支付记录不会重复放大商品行数量。

### 门店表现

返回 storeId、storeName、createdWorkOrders（按创建时间）、completedWorkOrders（按完成时间）、openWorkOrders（当前全部积压）。按完成数、新建数降序及门店 ID 升序排列。仅返回有未删除工单记录的门店组，未分配门店单独汇总；停用或删除门店的历史工单仍保留。

工单按**当前分配门店**归属，改派会改变历史归属。目前商城订单没有销售门店字段，库存支持跨仓预占，不能从库存仓推导门店销售额，因此本接口提供服务业绩，不提供门店销售收入。

## 数据库与验证

Flyway 执行 `V1.10__business_statistics.sql`，新增查询索引和权限，无需回填历史业务字段。自动化测试覆盖日期边界、跨期退款、重复支付、逻辑删除、空日期补零、排行、分页、权限与 CSV。测试使用 H2 MySQL 兼容模式，实际 MySQL 的迁移、查询计划和大数据量性能仍需部署环境验收。
