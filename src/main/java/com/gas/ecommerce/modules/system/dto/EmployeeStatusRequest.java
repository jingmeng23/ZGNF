package com.gas.ecommerce.modules.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EmployeeStatusRequest(@NotNull @Pattern(regexp="active|inactive") String status) { }
