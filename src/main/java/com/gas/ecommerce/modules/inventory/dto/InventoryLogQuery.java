package com.gas.ecommerce.modules.inventory.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper=true)
public class InventoryLogQuery extends PageRequest {
    @Positive private Long inventoryId;
    @Positive private Long productId;
    @Pattern(regexp="initial|inbound|outbound|adjustment|order_lock|order_release|order_deduct") private String changeType;
    @Size(max=64) private String referenceNo;
    @Positive private Long operatorId;
}
