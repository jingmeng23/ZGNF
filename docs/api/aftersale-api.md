# 售后服务 API

统一前缀：`/api/v1`。接口使用项目统一响应格式；需要登录的接口从当前登录身份确定用户或管理员，不接受客户端传入用户 ID。

## 用户接口

| 方法 | 路径 | 功能 |
|---|---|---|
| POST | `/aftersales` | 为已支付订单提交整单退款申请 |
| GET | `/aftersales` | 分页查询当前用户的申请，可按状态筛选 |
| GET | `/aftersales/{id}` | 查看本人申请详情 |
| PUT | `/aftersales/{id}/cancel` | 撤销本人仍处于 `pending` 的申请 |
| GET | `/aftersales/{id}/operations` | 查看本人申请的处理记录 |

创建申请示例：

```json
{
  "orderId": 123,
  "reason": "商品不符合预期",
  "requestId": "client-generated-unique-key"
}
```

`requestId` 用于重复提交幂等；同一用户重复使用同一个值不会创建第二条申请。金额由服务端读取订单实付金额，客户端不能指定金额。只有处于允许售后的订单且有成功支付记录时才能申请；同一订单不能重复创建有效的售后申请。跨用户访问详情时按资源不存在处理。

## 管理员接口

| 方法 | 路径 | 权限 | 功能 |
|---|---|---|---|
| GET | `/admin/aftersales` | `aftersale:read` | 分页查询售后，可按状态、售后单号筛选 |
| GET | `/admin/aftersales/{id}` | `aftersale:read` | 查看售后详情 |
| PUT | `/admin/aftersales/{id}/review` | `aftersale:write` | 审核申请 |
| PUT | `/admin/aftersales/{id}/complete` | `aftersale:write` | 登记退款渠道已完成 |
| GET | `/admin/aftersales/{id}/operations` | `aftersale:read` | 查看操作人、状态变化、备注和处理时间 |

审核示例：

```json
{
  "status": "approved",
  "remark": "审核通过"
}
```

`status` 仅接受 `approved` 或 `rejected`。完成退款示例：

```json
{
  "refundReference": "支付渠道退款流水号",
  "remark": "退款已到账"
}
```

## 状态与退款约定

- `pending`：待审核，可由用户撤销。
- `approved`：审核通过，等待退款渠道处理。
- `rejected`：审核拒绝。
- `completed`：退款完成。

当前服务不直接调用第三方支付退款接口。管理员应先在支付渠道完成退款，再调用完成接口登记渠道退款流水号。登记成功后售后单、订单和支付记录会同步标记为退款完成；使用同一流水号重复登记具有幂等性。

数据库首次部署会由 Flyway 执行 `V1.3__aftersale_workflow.sql`，为售后流程增加幂等、审核、退款字段及管理员权限。

## 管理页面与增强规则

管理端入口 `/#/admin/aftersales`，用户端入口 `/#/aftersales`。详见 [售后管理与前后端对接](../guides/aftersale-management.md)。

列表支持 `orderNo`、`aftersaleNo`、`status` 精确筛选及 page/size 分页；排序字段为 createTime/updateTime/id/amount。
详情增加 reviewedBy、completionRemark、completedTime。操作记录由 `V1.23__aftersale_management.sql` 创建的表保存，历史记录不推算补造。
用户撤销为兼容旧数据仍使用 rejected 状态，通过审核人和撤销备注区分。

申请重试必须保持同一 requestId 的订单及原因相同；不同参数复用会拒绝。同一审核人相同决定和意见重试不重复留痕。
退款流水号会去除首尾空白后保存和比较，同流水号重复登记不会重复释放库存。
未履约订单退款会释放预占库存，已履约退款不自动增加实物库存。存在 pending/approved 售后时禁止继续履约和发货。
