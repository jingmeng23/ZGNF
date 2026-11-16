# 运营管理 API

所有接口位于 `/api/v1/admin` 下，需管理员登录。读操作要求 `operation:read`，写操作要求 `operation:write`。分页查询沿用项目参数 `page`、`size`、`sort`、`order`。

## 优惠券

领券、订单核销、领取后修改限制及迁移说明见 [优惠券 API](coupon-api.md)。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/coupons` | 分页查询，支持 `keyword`、`status` 过滤 |
| GET | `/coupons/{id}` | 详情及已领取数量、剩余发行量 |
| POST | `/coupons` | 新建优惠券 |
| PUT | `/coupons/{id}` | 更新优惠券 |
| DELETE | `/coupons/{id}` | 软删除优惠券 |

创建/更新请求字段：`name`、`amount`、`minimumAmount`、`startTime`、`endTime`、`quantity`、`status`。时间格式为 `yyyy-MM-dd HH:mm:ss`；结束时间必须晚于开始时间。状态为 `active` 或 `inactive`，优惠金额必须大于零，数量不能为负数。

## 系统配置

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/config` | 查询配置项 |
| PUT | `/config` | 新建或更新配置项 |

请求示例：`{"configKey":"shop.notice","configValue":"欢迎光临","description":"商城公告"}`。配置按 key upsert。

## 热搜词

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/hotwords` | 按搜索次数倒序查询 |
| POST | `/hotwords` | 新建热搜词 |
| PUT | `/hotwords/{id}` | 更新词条及状态/计数 |
| DELETE | `/hotwords/{id}` | 软删除词条 |

写入示例：`{"word":"燃气灶","searchCount":120,"status":"active"}`。

## 操作日志

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/logs` | 分页查询操作日志，按创建时间倒序 |

首次部署需执行 Flyway 迁移 `V1.4__operation_management.sql`，为管理员角色授予运营读写权限。
