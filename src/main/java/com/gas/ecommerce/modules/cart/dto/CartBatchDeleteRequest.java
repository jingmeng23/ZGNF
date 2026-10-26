package com.gas.ecommerce.modules.cart.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CartBatchDeleteRequest(@NotEmpty @Size(max = 100) List<Long> ids) { }
