package com.gas.ecommerce.modules.product.dto;
import jakarta.validation.constraints.*;
public record ProductStatusRequest(@NotBlank @Pattern(regexp = "draft|on_sale|off_sale|sold_out|presale|scheduled") String status) { }
