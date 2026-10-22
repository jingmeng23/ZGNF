package com.gas.ecommerce.modules.workorder.dto;
import jakarta.validation.constraints.*;
public record WorkOrderActionRequest(@NotBlank @Size(max=1000) String remark) { }
