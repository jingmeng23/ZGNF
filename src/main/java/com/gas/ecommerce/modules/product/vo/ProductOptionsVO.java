package com.gas.ecommerce.modules.product.vo;

import java.util.List;

public record ProductOptionsVO(List<CategoryVO> categories, List<BrandOption> brands) {
    public record BrandOption(Long id, String name, String status) { }
}
