package com.gas.ecommerce.modules.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CartQuery(@Min(1) Integer page, @Min(1) @Max(100) Integer size) {
    public int pageValue() { return page == null ? 1 : page; }
    public int sizeValue() { return size == null ? 20 : size; }
}
