package com.gas.ecommerce.modules.system.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
public class ManagementQuery extends PageRequest {
    @Size(max = 100) private String keyword;
    @Pattern(regexp = "active|inactive") private String status;
    @Positive private Long storeId;
    @Positive private Long roleId;
}
