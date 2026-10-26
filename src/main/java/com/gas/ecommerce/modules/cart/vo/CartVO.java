package com.gas.ecommerce.modules.cart.vo;

import java.math.BigDecimal;
import java.util.List;

public record CartVO(List<CartItemVO> items, List<CartItemVO> invalidItems, long total,
        int page, int size, int totalQuantity, int selectedQuantity, BigDecimal selectedAmount) { }
