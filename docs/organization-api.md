# 门店、员工与角色权限 API

统一前缀为 `/api/v1/admin`。接口沿用 `Result` 响应结构，分页数据为 `list/total/page/size/pages`。ID 输出为字符串，输入可使用数字或数字字符串。业务校验失败返回 HTTP 400，未登录返回 401，缺少权限返回 403。

## 接口与权限

| 方法 | 路径 | 权限 | 功能 |
|---|---|---|---|
| GET | `/stores`、`/stores/{id}` | `store:read` | 门店分页、详情 |
| POST | `/stores` | `store:write` | 创建门店 |
| PUT | `/stores/{id}` | `store:write` | 更新完整资料及状态 |
| DELETE | `/stores/{id}` | `store:write` | 软删除门店 |
| GET | `/employees`、`/employees/{id}` | `employee:read` | 员工分页、详情 |
| POST | `/employees` | `employee:write` | 创建员工，初始无角色 |
| PUT | `/employees/{id}` | `employee:write` | 更新完整资料及状态 |
| DELETE | `/employees/{id}` | `employee:write` | 软删除员工并清理角色关联 |
| PUT | `/employees/{id}/password` | `role:write` | 重置密码 |
| PUT | `/employees/{id}/roles` | `role:write` | 全量替换员工角色 |
| GET | `/roles`、`/roles/{id}` | `role:read` | 角色分页、详情、权限 ID 和成员数 |
| GET | `/roles/permissions` | `role:read` | 查询系统可分配权限目录 |
| POST | `/roles` | `role:write` | 创建自定义角色 |
| PUT | `/roles/{id}` | `role:write` | 更新角色名称及状态 |
| DELETE | `/roles/{id}` | `role:write` | 删除未被分配的自定义角色 |
| PUT | `/roles/{id}/permissions` | `role:write` | 全量替换角色权限 |

列表支持 `page`（默认 1）、`size`（默认 20，上限 100）、`keyword`（名称；员工还匹配账号，角色还匹配标识）、`status`（`active/inactive`）。员工列表额外支持 `storeId`、`roleId`。排序字段 `sort` 支持 `createTime/updateTime/id/name/status`，`order` 支持 `asc/desc`，默认创建时间倒序。

## 请求示例

创建或更新门店：

```json
{
  "name": "城东服务门店",
  "phone": "020-88206001",
  "province": "广东省",
  "city": "广州市",
  "district": "天河区",
  "address": "中山大道 188 号",
  "manager": "张三",
  "status": "active"
}
```

门店电话支持数字、空格及 `+()-`，长度 5–30；`manager` 为负责人名称，可省略。手机号或电话使用 AES-GCM 加密存储，查询时返回明文，查询权限应仅授予需要查看联系信息的员工。

创建员工：

```json
{
  "employee": {
    "username": "store_operator",
    "name": "李四",
    "phone": "13800138000",
    "storeId": "1",
    "departmentId": null,
    "positionId": null,
    "status": "active"
  },
  "password": "a-strong-password"
}
```

更新员工时仅传上例 `employee` 内的对象。`storeId/departmentId/positionId` 为可选关联，传 `null` 或省略会清空；非空必须指向有效且启用的记录。姓名、账号、手机号、状态为必填。账号由 3–64 位英文、数字、下划线、点或连字符组成；账号唯一，已删除账号不允许复用。

密码为 12–72 个字符，UTF-8 编码后不得超过 72 字节，使用 BCrypt 存储。接口不返回密码或密码哈希。重置密码请求为 `{"password":"new-strong-password"}`；重置阻止旧密码再次登录，已签发 access token 仍遵循原有效期；如需立即阻断已有会话，应停用员工账号。

创建或更新角色：

```json
{"name":"门店运营","roleKey":"store_operator","status":"active"}
```

角色标识为 2–64 位小写英文、数字或下划线，首位必须为英文；创建后不能修改，已删除标识不允许复用。角色详情返回 `permissionIds`、`members`、`system`。权限来自系统目录，API 不允许创建任意权限键。

员工角色和角色权限均使用 `{"ids":["1","2"]}` 全量替换，空列表表示清空。重复、空值、非正数、不存在的 ID 均拒绝；员工只能分配启用的角色。

## 登录及保护规则

- 沿用 `POST /auth/login` 管理员登录接口（完整路径 `/api/v1/admin/auth/login`）。员工必须启用且至少有一个启用角色才能登录，自定义角色同样适用。JWT 中管理端身份为 `admin`，数据库角色与细粒度权限在每次请求重新加载。
- 停用/删除员工、停用角色、移除角色或撤销权限，在后续请求生效。`role:write` 可分配系统所有权限和重置账号密码，应仅授予负责授权管理的人员。
- 内置 `admin` 角色不能删除、停用、改标识或替换权限；允许修改展示名称。授权变更与员工状态变更在数据库事务锁内执行，必须保留至少一个有效内置管理员。不能删除当前登录账号。
- 门店关联未删除员工、库存或工单时不能删除；角色关联未删除员工时不能删除，须先转移关联。
- 有已接单或处理中服务工单时，不能停用其门店、调离或删除负责员工、撤销其有效处理权限；须先改派、取消或完结工单，详见 [服务工单 API](work-order-api.md)。
- 本模块管理功能权限。`storeId` 表示组织归属，不提供按所属门店限制订单、库存等业务数据的隔离规则。

## 数据库与验证

启动时 Flyway 执行 `V1.6__organization_management.sql`，增加门店负责人、员工姓名及所属门店、事务锁和六个权限，并赋予内置管理员。已有账号的新增字段保持空值；后续资料更新按新请求约束补齐。

集成测试：`mvn -o -Dtest=OrganizationManagementIntegrationTests test`。测试使用 H2 的 MySQL 兼容模式，覆盖 CRUD、加密、授权生效、校验回滚、受限访问及管理员保护；真实 MySQL 部署仍需环境验证。
