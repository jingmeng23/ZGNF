package com.gas.ecommerce.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminOrderNoteRequest(@NotBlank @Size(max=1000) String remark) { }
