# gas-ecommerce

燃气智能服务电商后端框架，Java 25（已在 OpenJDK 25.0.2 验证）、Spring Boot 3.5.16、Maven。

## 构建

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

这是后端项目框架。用户、商品、购物车、订单、支付、售后、工单、库存、系统管理的实体、Mapper、Service 及 Controller 入口齐备，Service 使用 MyBatis-Plus 基础 CRUD。除认证、当前用户标识之外的业务 Controller 尚未定义具体业务端点。
已实现 JWT 安全链、统一响应/异常、全链路 traceId、Redis 幂等、权限注解、操作耗时日志、分页类型、自动填充、乐观锁、逻辑删除、订单/支付编号、AES-GCM/BCrypt、RabbitMQ 延迟队列、优惠券过期和订单超时条件取消。

业务扩展项：完整订单/库存事务（含超时库存释放）、第三方支付签名验签与回调、真实支付对账、短信厂商消费者、员工角色授权加载、操作日志数据库持久化、用户完整资料/地址接口、商品和订单等业务 API。支付对账任务会明确记录未配置适配器，不会报告虚假的对账成功。

枚举使用小写英文值；金额为 BigDecimal；实体 Long ID 输出字符串，响应 timestamp 为数值。分页 page 从 1 开始，size 默认 20、上限 100；排序列通过显式白名单映射。

Docker 镜像标签按 JDK 25 编写；当前开发机未安装 Docker，尚未验证镜像拉取及容器集成。运行镜像以非 root 用户启动。
