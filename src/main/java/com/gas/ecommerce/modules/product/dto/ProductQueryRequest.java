package com.gas.ecommerce.modules.product.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductQueryRequest extends PageRequest {
    @Positive private Long categoryId;
    @Size(max = 100) private String keyword;
}
