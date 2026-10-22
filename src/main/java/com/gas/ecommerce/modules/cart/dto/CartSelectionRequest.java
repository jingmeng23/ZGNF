package com.gas.ecommerce.modules.cart.dto;

import jakarta.validation.constraints.NotNull;

public record CartSelectionRequest(@NotNull Boolean selected) { }
