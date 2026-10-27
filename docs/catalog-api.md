# 商城商品浏览

正式接入时前端配置 `VITE_DEMO_MODE=false`；默认 API 前缀为 `/api/v1`。

- `GET /categories`：真实可用分类，返回 `id、parentId、name、sort、status`。禁用分类及其后代不展示。
- `GET /products?page=1&size=12&keyword=燃气&categoryId=1&sort=price&order=asc`：数据库搜索和分页。分类包含后代分类；关键词匹配商品名称。返回 `list、total、page、size、pages`。
- 排序字段：`createTime、price、sales、id`；方向 `asc、desc`。每页最多 100 条，此限制是单页大小，不是搜索范围上限。
- `GET /products/{id}`：商品详情，含可用库存、`description` 纯文本介绍和 `detailImages` 图片 URL 数组。商品不存在、下架或分类不可用时返回 404。

列表不读取详情长文本和图片列表，详情页单独请求完整内容。商城支持最新上架、销量、价格升降序；搜索、分类、页码和排序保存在 URL，可使用浏览器返回恢复。

## 内容维护

管理员商品新增/更新接口支持可选 `description`（最多 16000 字符）和 `detailImages`（最多 20 张，每个 URL 最多 512 字符，仅 HTTP/HTTPS 或站内绝对路径）。PUT 省略或设为 null 会清空这些字段；图片列表也可传空数组。

```json
{
  "categoryId": "1",
  "name": "智能燃气灶",
  "price": 1299,
  "description": "5.2kW 大火力\n配备熄火保护装置",
  "detailImages": ["https://images.example.com/stove-detail.jpg"]
}
```

部署前通过 Flyway 执行 `V1.12__product_details.sql`，为 product 表添加介绍和详情图片字段。已有商品无需补齐字段即可浏览；图片失败时显示占位图，介绍按纯文本渲染。
