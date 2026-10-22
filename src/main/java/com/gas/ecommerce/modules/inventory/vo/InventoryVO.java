package com.gas.ecommerce.modules.inventory.vo;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record InventoryVO(Long productId, Long storeId, Integer quantity, Integer lockedQuantity) { }
