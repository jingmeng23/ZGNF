package com.gas.ecommerce.modules.aftersale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AftersaleCompleteRequest(@NotBlank @Size(max = 100) String refundReference,
        @Size(max = 500) String remark) { }
