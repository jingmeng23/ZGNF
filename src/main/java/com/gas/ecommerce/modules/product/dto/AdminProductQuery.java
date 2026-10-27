package com.gas.ecommerce.modules.product.dto;
import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
public class AdminProductQuery extends PageRequest {
    @Positive private Long categoryId;
    @Size(max = 200) private String keyword;
    @Pattern(regexp = "draft|on_sale|off_sale|sold_out|presale|scheduled") private String status;
    @Size(max = 64) private String filter;
}
