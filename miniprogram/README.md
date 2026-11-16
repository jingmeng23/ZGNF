# 自贡能发微信小程序

微信原生商城客户端，复用仓库现有 Spring Boot API。商城入口为首页、分类、购物车、我的；员工管理入口为“我的 → 员工工作台”。“我的”页面调整与验收路径见 [个人中心优化说明](ACCOUNT-OPTIMIZATION.md)。

本轮开发覆盖购物车、商品、库存、分类、订单、员工、用户、角色权限和售后。具体操作、权限及验收步骤见 [功能与管理说明](MANAGEMENT.md)。个人中心另提供资料修改、密码、收藏、浏览记录、积分明细、隐私偏好和反馈。

## 打开项目

1. 在微信开发者工具中导入本目录，使用有权开发的小程序 AppID。
2. 检查 project.config.json 的 AppID，以及 config.js 的 apiBase 和 assetBase。保留的局域网 HTTP 地址用于当前本地开发；真机及发布环境需要有效 HTTPS 域名和小程序合法域名配置。
3. 启动现有 Java 后端及其数据库、Redis 等依赖。商品、组织、权限和库存等业务数据均由后端提供。
4. 重新编译小程序。现有豆奶、可乐封面已随小程序打包；如使用其他 `/images/...` 静态图片，在本目录运行 `npm run assets` 并保持终端运行。
5. 顾客使用已有商城账号登录；员工工作台使用独立员工账号及管理授权。

微信绑定、商户配置和官方支付回调说明见 [微信支付配置](../docs/guides/wechat-mini-program.md)。客户端没有商户密钥。支付与原路退款最终结果由服务端核验。

## 本地商品图片

员工工作台的商品表单可直接从相册或相机上传封面、详情图片，无需输入地址。上传功能需要更新并重启后端；新图片保存在后端 `uploads/products/`（可配置），通过 API 域名的 `/api/v1/media/products/...` 读取，自动与下方内置演示图片区分。发布前将 API 域名加入微信 `uploadFile`、`downloadFile` 合法域名。

现有 `/images/products/pepsi.png` 和 `/images/products/唯怡.jpg` 已复制到小程序 `assets/products/`，由 `utils/api.js` 映射为包内图片路径。首页、商城及其他使用 `api.image()` 的页面无需启动 8082 图片服务即可显示这两张图片。替换这两张静态图片时，需要同步更新包内文件并重新编译。

其他 `/images/...` 对应仓库的 `frontend/public/images/`，仍通过 `config.js` 的 `assetBase`（本地为 8082）读取。当前 Java 后端的 8081 端口不提供这些静态图片，直接访问会得到 HTTP 401 JSON。新上传的 `/api/v1/media/products/...` 图片继续使用业务 API 的 8081 端口。

`npm run assets` 只提供图片目录下的 PNG、JPEG、GIF、WebP 等文件，支持中文文件名和 HEAD 请求，缺失文件返回 404。服务不需要安装额外依赖，不提供 API 或目录列表。可用 `http://192.168.2.95:8082/images/products/pepsi.png` 检查本地图片服务；本机 IP 变化时同步修改 `config.js` 的两个地址。若开发者工具仍拦截本地 HTTP，在“详情 → 本地设置”启用“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”。真机调试时手机与电脑需要网络互通，并允许访问该端口。

当前豆奶商品的演示数据还引用了不存在的 `/images/products/唯怡豆奶.png`；封面 `/images/products/唯怡.jpg` 正常。如打开该商品的详情页仍看到单张详情图加载失败，需要将商品 `detailImages` 更新为仓库中实际存在的媒体文件。

默认图片目录是相对于脚本的 `../../frontend/public/images`，可通过环境变量 `MINI_ASSET_ROOT` 修改；`MINI_ASSET_PORT` 默认 8082，修改后同步调整 `assetBase`。这是本地开发服务，发布时将图片部署到公开 HTTPS 静态资源服务器或 CDN，并更新 `assetBase` 与小程序域名配置。

## 结算预览返回 400

`POST /api/v1/orders/preview` 的 HTTP 400 包含服务端业务错误，页面保留原始错误，并提示更换地址、完善省市区、核对购物车或联系商家等处理方式。报价失败会清除旧报价并禁用提交。

如果提示“配送服务未配置该地区规则，暂不能结算”，需要在后端 `checkout_delivery_rule` 配置实际配送范围和运费。空表不会默认免运费，也不应关闭配送校验来绕过。匹配使用服务端的省、市、区名称，精确地区规则优先于 `*`。

`scripts/sql/local-delivery-demo.sql` 是可选的本地演示配置，仅覆盖天津市河东区，运费 0 元，读取服务端区划名称且不覆盖已有地区规则。仅在明确采用这一演示配送政策后执行；它不随小程序启动自动执行，也不用于生产迁移。配置规则后点击“刷新结算信息”，由服务端重新计算商品、优惠及运费。

## 检查

- npm run check：页面配置、路由、JS 语法、相对依赖、WXML 结构、表达式和事件处理器。
- npm test：模拟接口下的支付、认证、权限、库存、购物车、结算恢复和售后等关键流程。
- npm run check:wechat：调用本机微信开发者工具附带的 wcc / wcsc 原生编译器。Windows 默认查找 C:/Program Files (x86)/Tencent/微信web开发者工具/resources/app.asar.unpacked/node_modules/wcc-exec；其他位置可设置环境变量 WECHAT_COMPILER_DIR。

原生编译和模拟接口测试通过后，仍需在开发者工具及真机连接实际后端，完成登录、购物、管理操作和真实商户的小额交易验收。图片当前通过 URL 维护，SKU 为信息展示；SKU 编辑、按 SKU 购买及图片上传需要补充对应后端接口。
