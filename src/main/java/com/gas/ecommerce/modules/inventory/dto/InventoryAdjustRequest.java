package com.gas.ecommerce.modules.inventory.dto;
public record InventoryAdjustRequest(Long productId, Long storeId, Integer changeQuantity, String remark) { }
