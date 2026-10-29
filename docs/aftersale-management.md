# 售后管理与前后端对接

## 必需功能

本项目现有售后模型采用“按订单整单退款”，本次在该模型上完成用户申请与运营处理闭环。

| 功能 | 实现 |
| --- | --- |
| 申请入口 | 用户在已支付、履约处理中或已完成订单的详情中申请；填写原因，展示整单实付金额 |
| 申请校验 | 订单归属、成功支付记录和金额校验；金额由服务端计算；同订单不能并存有效申请 |
| 撤销与重提 | 用户只能撤销本人的待审核申请；拒绝或撤销后可使用新的请求编号重新申请 |
| 运营查询 | 售后单号、订单编号、状态、分页查询及售后详情 |
| 审核处理 | 同意/拒绝、必填审核意见、审核人记录 |
| 退款登记 | 先在真实支付渠道退款，再登记渠道流水号、备注、完成时间；同步订单和支付状态 |
| 操作追溯 | 申请、撤销、审核、退款登记的不可编辑操作记录与处理时间线 |
| 订单库存联动 | 待处理售后阻止继续履约和发货；未履约退款释放预占；已履约退款不自动增添实物库存 |
| 权限与一致性 | 用户数据隔离；管理员查询/处理权限分离；行锁、事务、参数幂等、重复提交保护 |

当前没有第三方退款渠道适配器，因此“登记退款完成”只记录客服确认的渠道结果，**不发起打款**。
页面明确提示这个限制，并要求登记前确认已实际退款。
退货收件、换货、维修、部分商品/部分金额退款不在现有订单模型中，本次不虚构这些流程。

## 页面与接口连接

- 管理台：`/#/admin/aftersales`，由 `frontend/src/admin/AdminAftersales.vue` 提供。
- 用户端：`/#/aftersales`，由 `frontend/src/views/AftersalesView.vue` 提供。
- 申请入口：订单详情中的“申请整单退款”，跳转 `/#/aftersales?orderId=订单ID`。
- 个人中心增加“我的售后”入口。
- 两端共用 `AftersaleDetailPanel.vue` 展示详情与操作记录。
- `frontend/src/api/aftersales.ts` 调用真实 `/api/v1/aftersales` 和 `/api/v1/admin/aftersales`，沿用项目的令牌、超时及错误处理；不使用演示数据。
- Long ID 保持字符串，申请表单使用固定 requestId 重试，且不提交退款金额。

## 后端补充

沿用 [售后 API](aftersale-api.md) 的申请、列表、详情、撤销、审核和退款登记接口，新增：

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/admin/aftersales/{id}/operations` | `aftersale:read` |
| GET | `/api/v1/aftersales/{id}/operations` | 当前用户，仅能查看本人记录 |

列表增加 `orderNo` 精确筛选。支持 `sort=createTime/updateTime/id/amount`、`order=asc/desc`，默认按创建时间倒序。
详情增加 `reviewedBy`、`completionRemark`、`completedTime`，用于展示审核人和退款登记详情。

幂等规则：

- 同一用户、同一 requestId 的申请仅在订单和规范化原因相同时返回原记录；更换参数复用 requestId 返回业务错误。
- 同一审核人以相同结果、相同意见重试审核时不追加记录。
- 同一退款流水号重试登记返回原结果，不重复释放库存或追加处理记录；不同流水号不能覆盖已完成记录。
- 操作记录与业务变更在同一个事务中写入；退款登记任一环节失败时，支付、订单、库存、售后单和操作记录一起回滚。

兼容旧数据：用户撤销仍使用 `rejected` 状态，审核人为空且审核意见为“用户撤销申请”时，界面展示“用户已撤销”。普通审核拒绝展示“审核未通过”。
历史售后单不补造操作日志；详情继续展示已有审核与退款字段。

## 迁移与启动

新增 Flyway 迁移 `V1.23__aftersale_management.sql`，创建 `aftersale_operation`。
应用上线必须先成功执行迁移；不要只发布前端而遗漏后端和数据库更新。

在项目根目录启动基础服务与后端：

```shell
docker compose up -d mysql redis rabbitmq
mvn spring-boot:run
```

在 `frontend` 目录执行 `npm install`、`npm run dev`。
默认 Vite 将 `/api` 请求代理到 `http://localhost:8080`，可通过 `VITE_API_PROXY_TARGET` 配置后端地址。
独立部署前端时设置 `VITE_API_BASE_URL`，或在网站网关为 `/api` 配置反向代理。
项目原有免登录测试配置保持不变；恢复登录模式后需使用具有售后权限的管理员。

## 验证

- `npm run build`：前端类型检查和生产构建。
- `npm test`：含售后 API 路径、筛选参数、Long ID、审核与退款请求、用户/管理端接口隔离测试。
- `mvn "-Dtest=AdminCatalogIntegrationTests#aftersale*" test`：申请、审核、取消、退款、权限、审计、金额、库存、幂等、并发及事务回滚。
- `mvn test`：包含订单、支付、库存与商城的回归测试。

后端自动化测试使用 H2 的 MySQL 兼容模式，不代表已在真实 MySQL 和真实支付渠道完成验收。
