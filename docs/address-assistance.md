# 收货地址模块

页面 `/addresses` 使用 Element Plus 弹窗和四列地区面板，支持新增、编辑、删除、默认地址、粘贴识别和高德地图选点。

## 四级地区面板

`RegionSelector.vue` 支持 `v-model` 绑定字符串或数字编码数组，如 `[510000, 510300, 510303, 510303002]`（四川省 / 自贡市 / 贡井区 / 贡井街道）。默认通过地区接口逐级加载，也可通过 `loadRegions(parentCode)` 传入异步加载函数。`change(values, regions)` 在手动选择时返回当前路径及地区记录；外部赋值仅回显，不触发 change。

面板横向分省份、城市、区县、街道四列，支持关闭按钮、点击外部和 Esc 关闭、加载失败重试及异步竞态保护。切换上级立即清除下级；初始仅加载省份。区划导入接口已允许 level=4，街道 parentCode 必须指向 level=3 的区县。包内未覆盖的地区显示“暂无地区数据”。

收货地址表单已接入该组件。为兼容固定保存字段，街道名称并入 detail，编辑、粘贴识别、地图选点时从所属区县的街道列表匹配最长名称进行四级回显；省市区仍独立保存，旧三级地址可继续编辑保存。

### province-city-china 数据接入

数据来自开发依赖 `province-city-china@9.0.0`（上游项目 https://github.com/uiwjs/province-city-china，MIT）。在 frontend 目录运行 `npm run regions:generate`，生成后端资源 `src/main/resources/regions/province-city-china.json`，随 JAR 打包；重启后端即可使用，不需要导入 SQL 或重启前端构建来下载全国数据。更新依赖后需重新生成资源并测试。

资源含 34 个省级地区、342 个市级节点、2,847 个区县、38,930 个唯一街道节点；直辖市补充同名市级节点以维持四级路径。自贡有 6 个区县及 90 个街道/乡镇。上游 town.code 为区县编码，town.town 为街道编码片段，常规街道组合为 9 位编码；有非零尾码时保留完整 12 位编码。上游北泉镇同编码别名重复记录按编码去重，保留第一条名称。

地区 API、解析服务和保存校验统一使用该资源。数据库已有同编码记录优先，停用地区不会被包内数据重新启用；未配置的编码使用包内默认数据。未写入或覆盖数据库记录，也不使用 frontend/streets.json。

## 环境配置

- 前端安装依赖后，在 `frontend/.env.local` 填写 `VITE_AMAP_KEY` 和 `VITE_AMAP_SECURITY_CODE`，使用高德 JavaScript API 2.0 凭证并配置域名白名单。`.env.example` 仅供参考，Vite 不会加载；修改环境配置后重启开发服务，生产环境需重新构建。`.env.local` 已被 Git 忽略。
- 后端启动由 Flyway 执行 `V1.26__address_coordinate_system.sql`。历史坐标保留 WGS84，新地图选点采用 GCJ-02；历史坐标在地图展示时转换，未修改点位时保留原坐标系。
- 地区默认读取包内数据，可通过 `PUT /api/v1/admin/address-regions` 管理覆盖配置（code、parentCode、name、level、active、replacementCode、version）。后端继续校验三级编码、父子关系、名称和有效状态。

## 交互

textarea 监听 paste：图片提示“图片识别功能开发中”并调用空函数 `uploadToOcr(file)`；纯文本调用本地 address-parse，不上传第三方。

解析器兼容 CommonJS 的多层 default 导出；文本先做 NFKC 全角转换，并规范化 +86、手机号中的空格/横线、多行输入和常见字段标签。多个手机号仍按歧义处理，不自动覆盖。地区接口失败与低置信度识别分开提示。

识别完整度按姓名、手机号、省、市、区及编码、详细地址六项计算，阈值 0.95，且须匹配后端有效区划。多个手机号、低分或失效地区只提示，不修改已填内容。该评分为应用层完整度规则，并非概率。

级联选择后地图定位城市中心；点击地图或拖动 Marker 后调用 Geocoder 逆编码，将 formattedAddress 回填详细地址并同步省市区。直辖市 city 为空数组时用 district 兜底，再按 adcode 找到字典父级以兼容“市辖区”。

地图在 dialog opened 后初始化并 resize，关闭或卸载时 destroy，过期异步回调失效。编辑坐标直接定位原位置；新增默认自贡市中心。点击定位按钮才申请浏览器授权，拒绝或超时仍可手动选点。Key 缺失、SDK 超时和网络失败均提示降级，表单可正常提交，经纬度可同时为空。

前后端校验：收件人 2–20 字、手机号 11 位、详细地址不少于 5 字。

## 保存契约

POST `/api/v1/users/me/addresses` 或 PUT `/{id}` 的 JSON 固定字段：

`receiver, phone, provinceCode, cityCode, districtCode, province, city, district, detail, fullAddress, longitude, latitude, adcode, isDefault`

响应 fullAddress 从保存的有效区划与详细地址派生；adcode 独立保存地图行政区编码，手动选择时使用区县编码。重复地址返回 HTTP 409 / 409002，用户点击“仍然保存”时通过查询参数 `allowDuplicate=true` 重试，固定 JSON 不添加字段。旧客户端 body.allowDuplicate 继续兼容。

原有 `/parse`、`/reverse-geocode` 保留兼容；新页面使用 address-parse 和高德 SDK，不需要旧 Nominatim 配置。地址簿修改或删除不改变历史订单。

## 验证

运行前端 `npm run build` 和地址相关 Vitest，后端运行 AddressAssistanceTests 和 AdminCatalogIntegrationTests 的地址用例。

真实地图需在部署域名配置有效 Key 后验收：打开弹窗、编辑旧地址、点击、拖点、城市切换、拒绝授权、关闭重开，以及无 Key 时手动保存。
