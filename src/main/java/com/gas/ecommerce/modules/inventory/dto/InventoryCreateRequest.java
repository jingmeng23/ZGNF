package com.gas.ecommerce.modules.inventory.dto;

import jakarta.validation.constraints.*;

public record InventoryCreateRequest(@NotNull @Positive Long productId,@Positive Long storeId,
        @NotNull @Min(0) Integer quantity,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String requestId,
        @NotBlank @Size(max=255) String remark) { }
