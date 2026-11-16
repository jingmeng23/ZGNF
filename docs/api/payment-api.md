# 支付模块

当前实现支付记录与订单状态闭环，供可信支付网关接入。尚未接入微信/支付宝的统一下单、二维码、收银台、官方验签、主动查单或退款 API；创建接口仅生成本地支付记录，不产生渠道交易。

## 用户接口

均需普通用户 Bearer Token，管理员不能代替用户支付。

| 方法 | 路径 | 功能 |
|---|---|---|
| POST | `/api/v1/payments` | 创建支付单；同一订单已有同渠道 pending 记录时返回原记录 |
| GET | `/api/v1/payments` | 本人支付记录分页，支持 `status`、`orderId`、`page`、`size`，固定按创建时间倒序 |
| GET | `/api/v1/payments/{id}` | 查询本人支付单详情 |

创建请求：

```json
{"orderId":123,"channel":"test"}
```

金额由服务端读取订单 `pay_amount`，不接受客户端指定。订单必须属于当前用户、仍处于 `pending_payment` 且未过期。支付单状态包括 `pending`、`success`、`failed`、`refunded`。失败后可重新创建支付记录，已有其他渠道待支付记录时禁止切换渠道。

## 网关回调

`POST /api/v1/payment/callback/{channel}` 不使用用户 Token，使用 HMAC-SHA256 认证。该协议用于自有可信网关，不等同于任何支付平台的官方回调协议。只有网关确认真实渠道结果后才能调用。

请求头：

- `X-Payment-Timestamp`：Unix 秒，服务器允许前后 300 秒。
- `X-Payment-Signature`：HMAC-SHA256 十六进制结果。

签名原文是 `channel + "\n" + timestamp + "\n" + 原始请求体`，编码为 UTF-8，密钥来自服务端 `PAYMENT_CALLBACK_SECRET`。密钥仅由可信网关和后端持有。

请求体：

```json
{
  "paymentNo":"PAY2026102200000001",
  "transactionNo":"provider-transaction-id",
  "amount":50.00,
  "status":"success",
  "eventTime":"2026-10-22 14:00:00"
}
```

`status` 接受 `success`、`failed`；`eventTime` 使用北京时间 `yyyy-MM-dd HH:mm:ss`，不得早于支付单创建时间或晚于当前时间 5 分钟。金额、渠道、用户和订单必须匹配。同渠道交易流水号只能对应一条支付记录。

支付成功时在一个事务内将支付单改为 `success`、订单改为 `paid`，库存继续保持预占，进入订单处理时扣减。重复相同结果及流水号幂等；退款后的原成功通知不会覆盖退款状态。失败只更新支付记录，订单保持待支付。

订单已过期或取消时，成功通知返回业务错误且不修改状态；真实扣款需通过支付渠道查单、退款和人工对账处理。当前每日对账任务仍为占位，不能自动处置此情形。回调成功返回统一 `Result`，官方支付平台所需的响应报文应由网关转换。

## 配置和迁移

- `PAYMENT_ALLOWED_CHANNELS`：以逗号分隔的网关渠道白名单，默认空，所有渠道关闭。
- `PAYMENT_CALLBACK_SECRET`：至少 32 字节的回调密钥，默认空，签名回调关闭。
- Redis 用于生成支付单号。
- Flyway `V1.5__payment_callback.sql` 增加渠道流水号和唯一索引，启动时迁移。

本地测试使用 `test` 渠道和测试专用密钥，生产环境接入渠道适配器后再启用对应渠道。
