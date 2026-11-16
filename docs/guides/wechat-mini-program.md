# 微信小程序与微信支付

本次新增 `miniprogram/` 微信原生工程，用户端直接调用现有 Spring Boot 商城 API；Vue 管理后台继续负责商品、库存、订单、售后等运营工作。账号、商品、价格、库存和订单共用后端，没有新建第二套商城数据库。

## 已实现的购买链路

首页/商品搜索 → 商品详情 → 购物车 → 收货地址/优惠券 → 订单预览与确认 → 创建订单 → 微信预支付 → `wx.requestPayment` → 微信官方通知/服务端查单 → 原有订单状态机更新为已付款。客户端收银成功只触发查单，不会直接把订单改为已支付。

原生页面包括首页、商品搜索、商品详情、购物车、账号、注册登录与微信绑定、结算、订单列表/详情、地址管理、领券/我的券、安装维修安检预约、售后申请。用户使用原网站手机号密码登录；首次在小程序登录后绑定当前微信，后续可用微信登录。服务端通过 `jscode2session` 交换 `wx.login` 的一次性 code，openid/session_key 不交由客户端自行声明，不向客户端返回 appSecret/session_key。

这是独立的微信原生客户端，并非将原网页放入 web-view。Web/Android 客户端继续保留；管理后台无需迁入小程序。当前版本聚焦交易主流程，原 Web 的多规格展示、收藏/历史、地图点选等不是逐像素迁移，需按产品计划继续完善。

## 打开工程

1. 安装微信开发者工具，导入仓库的 `miniprogram/` 目录。
2. 将 `project.config.json` 的 `touristappid` 改为自己已注册的小程序 AppID。游客 AppID 仅用于页面开发，无法真实登录或支付。
3. 编辑 `miniprogram/config.js` 的 `apiBase` 和 `assetBase`，使用同一套正式后端/商品图片 HTTPS 域名。不要把商户密钥写入任何小程序文件。
4. 在小程序后台配置 request 和 downloadFile 等合法域名，配置头像/图片 CDN 的域名。`assetBase` 应能提供当前商品的 `/images/...` 路径，否则请将商品图片改为完整 HTTPS CDN URL。
5. 后端执行迁移 `V1.29__wechat_mini_program.sql`。Flyway 正常启动时会自动创建微信绑定表。
6. 首次使用原商城手机号密码登录，点击“绑定当前微信”，再从订单详情使用微信支付。

微信真机不接受 `10.0.2.2` 或仅电脑可访问的 localhost。开发者工具的“跳过域名校验”只适用于本地联调，发布必须使用正确的 HTTPS 域名、有效证书和真实小程序配置。

## 服务端配置

支付默认关闭，配置使用环境变量：

```dotenv
WECHAT_ENABLED=true
WECHAT_APP_ID=wx你的真实小程序AppID
WECHAT_APP_SECRET=从小程序后台获取，仅服务端保存
WECHAT_MERCHANT_ID=微信支付商户号
WECHAT_MERCHANT_SERIAL=商户API证书序列号
WECHAT_PRIVATE_KEY_PATH=/run/secrets/apiclient_key.pem
WECHAT_PLATFORM_PUBLIC_KEY_PATH=/run/secrets/wechatpay_public_key.pem
WECHAT_PLATFORM_SERIAL=PUB_KEY_ID_你的微信支付公钥ID
WECHAT_API_V3_KEY=32字节的APIv3密钥
WECHAT_NOTIFY_URL=https://商城域名/api/v1/payment/wechat/notify
PAYMENT_ALLOWED_CHANNELS=wechat_mini
```

配置支持微信支付 **公钥模式**：商户私钥使用 PKCS#8 PEM 格式，平台公钥使用 X.509 SubjectPublicKeyInfo PEM 格式。`WECHAT_PLATFORM_SERIAL` 必须是该平台公钥对应的公钥 ID，不能填商户 API 证书序列号。请求签名、响应验签、小程序调起支付签名与通知解密都在服务器执行。签名不匹配、未知公钥 ID、超过五分钟的通知时间戳、商户/AppID/币种/付款人/支付金额不匹配均拒绝处理。公钥轮换需先按微信商户平台要求配置并部署，当前实现使用固定受信公钥而非自动下载平台证书。

申请小程序微信支付产品，完成商户与小程序 AppID 的关联授权。API v3 密钥不是小程序 AppSecret。商户号、证书、公钥 ID 和小程序关联信息必须与真实商户后台一致。

如通过 Docker 运行，需要在实际使用的 `gas-server` 容器上注入这些环境变量并只读挂载密钥文件；只修改电脑环境变量不会更改已有容器。不要覆盖数据库加密密钥或 JWT 密钥。构建并更新后端服务后确认网站与小程序都访问这套服务。

回调路径使用微信官方 RSA/AES-GCM 协议，返回成功时 HTTP 204。不能让网关把该成功响应包裹成商城的 `Result`。原内部 HMAC 回调不接受 `wechat_mini` 渠道，以免绕过微信官方验签。

## 接口

| 方法 | 路径 | 认证与用途 |
|---|---|---|
| POST | `/api/v1/auth/wechat/login` | 公开；`{code}`，仅已绑定且启用的商城账号可登录 |
| POST | `/api/v1/users/me/wechat-binding` | 普通用户 token；`{code}`，绑定已认证商城账号与微信；一微信/一商城用户在同 AppID 下只能绑定一次 |
| POST | `/api/v1/payments/wechat/prepay` | 普通用户 token；`{orderId}`，返回支付单 ID/编号及 `wx.requestPayment` 参数；金额和付款人从服务端读取 |
| POST | `/api/v1/payments/wechat/{id}/sync` | 普通用户 token；主动查单，复用已有支付结果处理器和订单事务 |
| POST | `/api/v1/payment/wechat/notify` | 微信官方验签通知；无需用户 token，原始请求体不能被网关改写 |
| POST | `/api/v1/admin/aftersales/{id}/wechat-refund` | 管理员及 `aftersale:write`；审核通过的售后微信原路全额退款/查询结果 |

## 一致性与退款

预支付先提交本地支付记录，再调用微信，发生外部超时仍保留相同 `paymentNo`。重试使用同订单的 pending 支付单，不生成第二笔付款身份。预支付与取消共用订单行锁：关单得到明确 CLOSED 才能释放资源，已付款则落账，未知状态继续保留资源等待查单。签名确认的 `ORDER_NOT_EXIST` 在持有订单锁时可作为未创建收银会话的关闭凭据。

回调和查单使用 `PaymentOutcomeHandler` 的金额、归属、流水唯一性和状态单调检查。重复通知不重复履约；取消后迟到的成功款项仍入账，进入原有退款补偿任务，不恢复已释放的订单。账号被停用后仍可核验历史交易收据，防止真实款项无法处理。

管理后台审核售后后，点击“微信原路退款 / 查询退款结果”。固定退款编号为 `WXAS{售后ID}`；先查询该编号，再在微信明确不存在时发起退款。微信仍返回 PROCESSING 时保留售后 approved 和本地付款 success，页面提示稍后再查。重试使用相同编号，只有微信返回 SUCCESS 且金额一致才把支付和订单更新为 refunded。不得用手工输入退款流水把微信订单直接标记为已退款。误关/重复支付的补偿退款使用现有 `LATE-{paymentNo}` 固定编号与持久化重试任务。

目前普通售后退款的结果确认由管理后台再次查询触发；没有自动扫描所有 approved 售后，也未新增退款异步通知。退款处理中需要运营在后台继续查询。库存的实物退货/质检入库仍按现有流程处理，不因退款自动认定货物已退回。

## 验证与上线边界

本次本地验证：支付和账号相关后端测试 37 项通过；Vue 构建与 88 项测试通过；小程序静态检查和 7 项测试通过；后端可执行 JAR 打包成功。完整 Maven 回归共 232 项，其中 1 项跳过、浏览历史排序断言 1 项失败；该模块单独重跑 13 项全部通过，仍需关注历史记录时间精度造成的排序稳定性，不能据此宣称全量回归通过。未修改该现有模块。

本地包含 RSA 防篡改/时间戳、AES-GCM 解密与关联数据、精确分币、商户/用户/金额核对、预支付数据源、关单状态、重复退款身份、通知响应、账号绑定唯一性与停用状态测试。小程序测试覆盖登录过期、收银取消竞态、客户端成功但服务端未确认、商户未配置及媒体地址。`npm run check` 检查 13 个页面路由、事件绑定和 JS 语法，不替代微信开发者工具的 WXML 编译及真机验收。

```powershell
mvn -o "-Dtest=WechatPayTests,WechatIdentityIntegrationTests,WechatNotifyTests,PaymentCallbackIntegrationTests" test
cd miniprogram
npm run check
npm test
```

尚未接入真实商户凭据、可访问的 HTTPS 域名及真实小程序 AppID，因此没有宣称已完成微信收款、退款或发布。上线前必须在微信开发者工具编译、真机完成注册/绑定/下单/取消/支付/回调丢失查单/退款、网络中断恢复及真实小额交易；执行真实 MySQL 迁移与支付竞态验收，并完成小程序类目、隐私声明、用户协议及审核提交。小程序目前展示用户手机号等信息，需按实际业务完善个人信息处理与保留规则。
