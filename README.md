# gas-ecommerce

微信小程序客户端位于 [miniprogram](miniprogram/README.md)，支付配置、账号绑定和上线步骤见 [微信小程序接入说明](docs/wechat-mini-program.md)。

燃气智能服务电商后端框架，Java 25（已在 OpenJDK 25.0.2 验证）、Spring Boot 3.5.16、Maven。

前端管理框架位于 [frontend](frontend/README.md)，使用 Vue 3 + TypeScript + Vite，包含经营总览及 11 个管理模块。启动后访问 `http://localhost:5173/#/admin`；默认展示演示数据，管理员授权及业务接口待后端继续实现。

## 构建

Java 初学者可先阅读 [新手阅读指南](docs/新手阅读指南.md)，其中按短信登录请求顺序解释了项目分层和核心组件。

```shell
mvn clean compile
mvn clean verify
```

项目级 `.mvn/settings.xml` 将 Maven 缓存设为当前目录 `.m2/repository`，避免本机默认仓库目录无写权限。
测试使用 H2，并禁用 Flyway 和 RabbitMQ 消费；它验证 Spring 上下文及 HTTP 安全/响应契约，不验证 MySQL SQL 和真实消息收发。

## 本地运行

安装 Docker 后，在当前目录执行：

```shell
docker compose up -d mysql redis rabbitmq
mvn spring-boot:run
```

或者使用 `docker compose up --build -d` 同时构建并运行服务。
开发环境端口：应用 8080，MySQL 3306，Redis 6379，RabbitMQ 5672，管理界面 15672。
数据库和消息服务的开发凭据见 docker-compose.yml。首次应用启动由 Flyway 执行全部 26 张表的建表脚本；MySQL 初始化目录只设置字符集，避免提前建表导致 Flyway 报非空 schema。

- 健康检查：`GET http://localhost:8080/actuator/health`
- Swagger：`http://localhost:8080/swagger-ui.html`
- 短信提交：`POST /api/v1/auth/sms-code`，JSON `{"phone":"13800138000"}`
- 未登录访问 `GET /api/v1/users/me` 返回 HTTP 401 和业务码 401001。
- 登录：`POST /api/v1/auth/login`，JSON `{"phone":"13800138000","code":"短信验证码"}`。

短信接口缓存五分钟验证码并向 `gas.notification.sms` 发布 JSON 消息，包含 AES-GCM 加密手机号及验证码。需要业务方实现短信厂商消费者完成真实投递；日志与 HTTP 响应不输出验证码。登录原子消费验证码，按 HMAC 手机号指纹查找/创建数据库用户，并签发 access/refresh JWT。刷新令牌不能直接认证 API；刷新和注销端点仍需按令牌生命周期规则扩展。

## 环境配置

dev 使用可覆盖的本地默认值；生产通过 `SPRING_PROFILES_ACTIVE=prod` 并提供 `MYSQL_HOST`、`MYSQL_USER`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PASSWORD`、`RABBITMQ_HOST`、`RABBITMQ_USER`、`RABBITMQ_PASSWORD`、`JWT_SECRET`、`ENCRYPTION_KEY`。
JWT HS256 密钥至少 32 字节。加密密钥应稳定保存，变更时需要迁移既有密文与手机号指纹。
OSS 默认禁用；启用时设置 `aliyun.oss.enabled=true` 及 `OSS_ENDPOINT`、`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`。
CORS 默认允许 localhost:5173 与 localhost:3000，可通过 `app.cors.allowed-origins` 设置逗号分隔的来源。

## 实现范围

员工管理已接入专用页面 `/#/admin/employees`，支持档案维护、部门/岗位归属、筛选分页、启停用、删除、角色授权和密码重置，沿用管理员及工单保护，详见 [员工管理与前后端对接](docs/employee-management.md)。

商品管理已接入专用前端页面，支持新增、编辑、详情、上下架、删除、分类与状态筛选、排序和分页；商品表单提供分类/品牌选项及分类快速新增，详见 [商品管理与前后端对接](docs/product-management.md)。

分类管理入口 `/#/admin/categories`，支持树形查看、搜索筛选、增删改查、层级调整、排序、启停、关联商品统计与跳转；后端校验环路、同级重名、启停依赖和删除保护，详见 [分类管理与前后端对接](docs/category-management.md)。

订单管理已接入专用运营页面，支持订单筛选、详情、付款/状态/物流查看、开始处理、关闭待付款、发货、内部备注、操作记录和本页导出；用户订单页支持详情、物流、取消及确认完成，详见 [订单管理补齐功能](docs/order-management.md)。

经营总览与统计报表已完成核心指标、上期比较、每日趋势、商品销量排行、日报分页及 CSV 导出，详见 [统计报表 API](docs/statistics-api.md)。

服务工单已完成用户预约、派单改派、员工处理完结、取消及操作日志，详见 [服务工单 API](docs/work-order-api.md)。

优惠券已完成管理、领券、订单优惠、支付核销和取消返还，详见 [优惠券 API](docs/coupon-api.md)。

售后管理已接入专用管理页面 `/#/admin/aftersales` 和用户页面 `/#/aftersales`，支持整单退款申请、撤销、审核、渠道退款登记、订单筛选和操作追溯，详见 [售后管理与前后端对接](docs/aftersale-management.md)。上线需执行 `V1.23` 迁移；当前登记渠道结果，不直接调用第三方退款接口。

商品库存已完成库存查询、建档、入库、出库、盘点调整及流水查询，支持订单锁定保护和幂等变更，详见 [商品库存 API](docs/inventory-api.md)。
库存管理前端入口 `/#/admin/inventory` 已连接真实接口，支持商品/仓库选项、库存分页筛选、低库存提示、入出库、盘点和单条库存流水追踪，详见 [库存管理与前后端对接](docs/inventory-management.md)。

当前已提供认证、用户地址、商品分类、购物车、订单、本地支付记录与回调、售后及运营管理业务接口。员工、角色权限管理已补齐分页查询、资料维护、状态调整及授权接口，详见 [组织管理 API](docs/organization-api.md)。用户完整资料、第三方支付渠道及真实支付对账等仍需扩展。
已实现 JWT 安全链、统一响应/异常、全链路 traceId、Redis 幂等、权限注解、操作耗时日志、分页类型、自动填充、乐观锁、逻辑删除、订单/支付编号、AES-GCM/BCrypt、RabbitMQ 延迟队列、优惠券过期和订单超时条件取消。

业务扩展项：完整订单/库存事务（含超时库存释放）、第三方支付签名验签与回调、真实支付对账、短信厂商消费者、员工角色授权加载、操作日志数据库持久化、用户完整资料/地址接口、商品和订单等业务 API。支付对账任务会明确记录未配置适配器，不会报告虚假的对账成功。

枚举使用小写英文值；金额为 BigDecimal；实体 Long ID 输出字符串，响应 timestamp 为数值。分页 page 从 1 开始，size 默认 20、上限 100；排序列通过显式白名单映射。

Docker 镜像标签按 JDK 25 编写；当前开发机未安装 Docker，尚未验证镜像拉取及容器集成。运行镜像以非 root 用户启动。
