package com.gas.ecommerce.modules.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record EmployeeCreateRequest(@NotNull @Valid EmployeeRequest employee,
        @NotBlank @Size(min=12,max=72) String password) { }
