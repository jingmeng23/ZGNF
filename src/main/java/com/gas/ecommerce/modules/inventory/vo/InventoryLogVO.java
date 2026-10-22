package com.gas.ecommerce.modules.inventory.vo;

import java.time.LocalDateTime;

public record InventoryLogVO(Long id,Long inventoryId,Long productId,Long storeId,String changeType,
        Integer changeQuantity,Integer quantityBefore,Integer quantityAfter,Integer lockedBefore,Integer lockedAfter,
        Long operatorId,String requestId,String referenceNo,String remark,LocalDateTime createTime) { }
