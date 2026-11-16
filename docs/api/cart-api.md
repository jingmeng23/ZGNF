# 购物车接口

所有接口均要求普通用户 access token：Authorization: Bearer <accessToken>。
用户 ID 从登录身份读取，请求无需传入 userId。管理员不能访问购物车接口。
返回格式沿用项目 Result，以下数据位于 data。

| 方法 | 路径 | 功能 / JSON 请求体 |
| --- | --- | --- |
| GET | /api/v1/cart | 查询购物车及汇总 |
| POST | /api/v1/cart/items | 加购：{"productId":1,"quantity":2} |
| PUT | /api/v1/cart/items/{id} | 设置数量：{"quantity":3} |
| PUT | /api/v1/cart/items/{id}/selection | 单项选中/取消：{"selected":true} |
| PUT | /api/v1/cart/selection | 全选/取消全选：{"selected":false} |
| DELETE | /api/v1/cart/items/{id} | 删除一项 |
| DELETE | /api/v1/cart/selected | 删除所有已选项 |
| DELETE | /api/v1/cart | 清空当前用户购物车 |

路径中的 id 为购物车项 ID，非商品 ID。删除成功返回 data: null。
其余接口返回购物车：items、totalQuantity、selectedQuantity、selectedAmount。
items 包含 id、productId、productName、coverUrl、quantity、unitPrice、
availableStock、selected、available、subtotal。

## 业务规则

- 数量必须为 1 至 999；同商品重复加购累加，累计也不能超过 999。
- 新加购或重复加购后该项自动选中。修改数量不改变选中状态。
- 加购和修改数量要求商品上架、分类及祖先分类有效，且数量不超过可用库存。
- 可用库存为各库存行 max(quantity - lockedQuantity, 0) 之和，与商品浏览口径一致。
- 购物车不预占库存，不锁定价格；列表使用当前价格及当前库存。
- 商品下架、删除、分类不可见或库存少于购物车数量时，available 为 false。
  这类项保留用于提示或删除，不计入 selectedQuantity、selectedAmount。
- totalQuantity 包含所有项的数量。subtotal 为当前单价乘数量；
  已删除商品无法读取价格时为 0。汇总金额不包含运费或优惠。
- 删除使用软删除；再次加入同一商品会恢复原行并采用本次数量。
- 停用用户无法读取或修改；跨用户操作按购物车项不存在处理。
- 参数或业务校验失败返回 HTTP 400，未登录返回 401，非普通用户返回 403。

后续下单时仍需重新核验商品、价格和库存。
