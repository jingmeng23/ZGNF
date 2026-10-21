package com.gas.ecommerce.modules.inventory.vo;
public record InventoryVO(Long productId, Long storeId, Integer quantity, Integer lockedQuantity) { }
