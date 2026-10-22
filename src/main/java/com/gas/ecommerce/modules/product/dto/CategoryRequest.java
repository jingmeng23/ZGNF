package com.gas.ecommerce.modules.product.dto;
import jakarta.validation.constraints.*;
public record CategoryRequest(@NotNull @Min(0) Long parentId,
        @NotBlank @Size(max = 64) String name, @NotNull @Min(0) Integer sort,
        @NotBlank @Pattern(regexp = "active|inactive") String status) { }
