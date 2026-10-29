package com.gas.ecommerce.modules.product.vo;

public record AdminCategoryVO(Long id, Long parentId, String name, Integer sort, String status,
        long childCount, long productCount) { }
