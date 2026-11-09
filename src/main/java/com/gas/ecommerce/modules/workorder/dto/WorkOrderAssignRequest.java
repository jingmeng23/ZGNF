package com.gas.ecommerce.modules.workorder.dto;
import jakarta.validation.constraints.*;
public record WorkOrderAssignRequest(@NotNull @Positive Long employeeId,
        @NotBlank @Size(max=800) String remark) { }
