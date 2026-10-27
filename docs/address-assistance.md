# 收货地址辅助能力

地址模块仅管理收货人、电话、地区、详细地址、标签和坐标。配送服务负责可达性和运费；订单落库保存本次交易的地址快照。编辑或删除地址簿不会修改已有订单，已支付订单变更收货地址必须走独立订单流程。

## 用户接口

地址管理页面为 `/addresses`，接口前缀为 `/api/v1/users/me/addresses`，均要求用户身份，并按当前用户隔离数据。

- `POST /parse`：提交 `{ "text": "粘贴的收货信息" }`，返回姓名、电话、地区和详细地址草稿。解析在本地执行，不上传第三方；歧义字段留空并提示。页面要求用户确认后才能应用草稿，接口本身不保存地址。
- `GET /regions`：获取有效省份；传 `parentCode` 获取下级地区。
- `POST /reverse-geocode`：提交 `latitude`、`longitude`、`consent: true`，返回待确认草稿。
- 原地址新增/修改接口支持 `label`（最多 20 字）、三级地区编码、成对经纬度和 `allowDuplicate`。

标签支持家、公司、学校和自定义值。同一用户的省市区与详细地址经规范化后相同，默认返回 HTTP 409、业务码 `409002`。用户明确选择仍然保存后，以 `allowDuplicate: true` 重试；不同用户之间不进行重复提示，也不泄露地址。

## 地图与定位

用户明确打开地图后才加载 OpenStreetMap 地图瓦片；点击定位按钮才请求浏览器位置授权。浏览器定位通常要求 HTTPS 或 localhost。拒绝授权时仍可手动选点或输入地址。地图选点需再次确认，使用 WGS84 坐标。

反向地理编码独立征求同意，配置 `ADDRESS_GEOCODING_URL` 为兼容 Nominatim 的服务基础地址后启用。未配置或服务失败时保留手动输入能力。部署时应按地图和地理编码供应商要求配置服务容量、使用政策和隐私说明；生产环境未进行供应商连通性验证。

## 行政区划数据与更新

迁移 `V1.20` 创建区划字典及地址扩展字段，不内置未经核验的全国数据。上线前需导入权威行政区划数据，编码为 6–12 位数字。

管理员调用 `PUT /api/v1/admin/address-regions`，请求示例：

```json
[
  {"code":"510000","parentCode":null,"name":"四川省","level":1,"active":true,"replacementCode":null,"version":"2026"},
  {"code":"510300","parentCode":"510000","name":"自贡市","level":2,"active":true,"replacementCode":null,"version":"2026"},
  {"code":"510302","parentCode":"510300","name":"自流井区","level":3,"active":true,"replacementCode":null,"version":"2026"}
]
```

每次最多导入 10000 条，可分批导入，父级必须已存在或在同批中。导入为更新/新增，遗漏记录不会自动停用；废弃编码应显式设 `active: false`，可通过 `replacementCode` 指向同级有效编码。名称变更也会要求用户重新确认地址。字典更新不会改写历史地址文本或订单快照。

旧地址始终可以展示；响应中的 `needsRegionCompletion`、`regionMessage` 和 `suggestedDistrictCode` 用于提示补全。默认 `ADDRESS_REQUIRE_REGION_CODES=true`，结算时必须提供有效且名称匹配的三级编码。渐进迁移可临时关闭该开关以兼容无编码地址，但已有失效编码仍会阻止结算，不能借兼容开关绕过校验。

## 验证范围

测试覆盖解析歧义、失效地区和层级校验、重复保存选择、用户隔离、标签与坐标清空、角色限制，以及修改地址后已支付订单快照不变。地图投影与地址客户端有前端单测；浏览器真实定位授权和供应商服务需在部署环境验收。
