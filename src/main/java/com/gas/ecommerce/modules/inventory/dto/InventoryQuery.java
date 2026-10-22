package com.gas.ecommerce.modules.inventory.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper=true)
public class InventoryQuery extends PageRequest {
    @Positive private Long productId;
    @Positive private Long storeId;
    private Boolean headquarters;
    @Size(max=200) private String keyword;
    @Min(0) private Integer availableMax;
}
