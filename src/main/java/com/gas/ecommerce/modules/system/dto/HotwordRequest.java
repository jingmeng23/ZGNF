package com.gas.ecommerce.modules.system.dto;
import jakarta.validation.constraints.*;
public record HotwordRequest(@NotBlank @Size(max=100) String word, @NotNull @Min(0) Long searchCount, @NotBlank @Pattern(regexp="active|inactive") String status) { }
