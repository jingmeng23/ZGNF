package com.gas.ecommerce.modules.aftersale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AftersaleReviewRequest(
        @NotBlank @Pattern(regexp = "approved|rejected") String status,
        @NotBlank @Size(max = 500) String remark) { }
