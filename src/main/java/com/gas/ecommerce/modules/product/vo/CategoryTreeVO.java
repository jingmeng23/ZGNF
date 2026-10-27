package com.gas.ecommerce.modules.product.vo;

import java.util.List;

public record CategoryTreeVO(Long id, Long parentId, String name, Integer sort,
                             String status, List<CategoryTreeVO> children) { }
