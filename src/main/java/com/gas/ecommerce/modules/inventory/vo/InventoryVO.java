package com.gas.ecommerce.modules.inventory.vo;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record InventoryVO(Long id,Long productId,String productName,Long storeId,String storeName,
        Integer quantity,Integer lockedQuantity,Integer availableQuantity,Integer version,
        java.time.LocalDateTime createTime,java.time.LocalDateTime updateTime) { }
