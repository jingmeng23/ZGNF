# 员工管理与前后端对接

## 功能与入口

管理台入口 `/#/admin/employees` 接入独立页面 `frontend/src/admin/AdminEmployees.vue`，通过 `frontend/src/api/employees.ts` 调用后端。支持员工列表和详情、账号/姓名搜索、状态/角色筛选、分页、新增和编辑、组织归属、启停用、软删除、角色分配及密码重置。页面提供加载状态、重复提交防护、错误展示、删除确认、只读访问和移动端布局。

## 接口

以下路径均以 `/api/v1/admin/employees` 为前缀。

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| GET | 空路径、`/{id}` | `employee:read` | 分页、详情 |
| GET | `/options` | `employee:read` | 组织及角色名称和当前操作权限 |
| POST | 空路径 | `employee:write` | 创建账号，密码单独传递 |
| PUT | `/{id}` | `employee:write` | 完整更新资料 |
| PUT | `/{id}/status` | `employee:write` | 只修改状态 |
| DELETE | `/{id}` | `employee:write` | 软删除 |
| PUT | `/{id}/roles` | `role:write` | 分配或清空角色 |
| PUT | `/{id}/password` | `role:write` | 重置密码 |

新增 `/options` 返回 `departments/positions/roles`，每项仅含字符串 ID、名称和状态；排除已删除项，保留停用项供历史名称展示。表单只允许选择启用组织和角色，无需额外授予角色权限目录查询权限。另返回服务端计算的 `canWrite/canManageRoles/currentEmployeeId`；每个写接口仍独立校验权限。

状态请求为 `{"status":"active"}` 或 `{"status":"inactive"}`。其他字段和请求结构见 [组织管理 API](../api/organization-api.md)。Long ID 在前端始终保留为字符串。

## 业务及安全规则

- 新账号默认无角色，需要授权人员分配有效角色才能登录管理台。
- 密码至少12个字符，UTF-8 最多72字节；采用 BCrypt 存储，不返回明文或哈希。手机号沿用 AES 加密存储。
- 普通资料更新不改密码和角色。重置密码阻止旧密码再次登录；已有访问令牌仍遵循有效期，停用可阻断后续请求。
- `role:write` 包含高权限授权和重置密码能力，只应授予授权管理人员。
- 禁止删除当前登录账号，必须保留至少一个有效内置管理员。
- 员工存在已派单或处理中工单时，禁止停用、删除或使其归属/处理权限失效，需要先处理工单。
- 变更沿用组织事务锁，校验失败回滚；软删除保留历史业务记录。
- 部门/岗位下拉使用数据库已有记录，本次不增加独立的部门/岗位维护模块。

## 运行与验证

不新增数据库迁移，沿用现有组织管理及工单迁移。开发运行后端 `mvn spring-boot:run`，前端在 `frontend` 中运行 `npm run dev`，访问 `http://localhost:5173/#/admin/employees`。Vite 将 `/api` 代理到 `http://localhost:8080`，可用 `VITE_API_PROXY_TARGET` 覆盖。

登录校验默认启用。仅在临时开发测试时，才显式设置后端 `LOGIN_DISABLED=true` 和前端 `VITE_LOGIN_DISABLED=true`；正式运行请保持后端 `LOGIN_DISABLED=false`、`LOCAL_MANAGEMENT_BYPASS=false`，前端 `VITE_LOGIN_DISABLED=false`、`VITE_LOCAL_MANAGEMENT_BYPASS=false`，并使用具有 `employee:read` 和所需写权限的账号。

验证命令：

```text
mvn -q -Dtest=OrganizationManagementIntegrationTests,LocalManagementBypassTests test
cd frontend
npm test
npm run build
```

后端集成测试使用 H2 MySQL 兼容模式，覆盖 CRUD、加密、角色授权、密码重置、选项权限隔离、状态回滚和并发管理员保护。真实 MySQL 环境仍需部署验证。
