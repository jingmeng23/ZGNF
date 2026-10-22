package com.gas.ecommerce.modules.system.dto;
import jakarta.validation.constraints.*;
public record ConfigRequest(@NotBlank @Size(max=100) String configKey, @Size(max=10000) String configValue, @Size(max=255) String description) { }
