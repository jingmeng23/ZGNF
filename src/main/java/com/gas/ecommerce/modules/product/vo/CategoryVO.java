package com.gas.ecommerce.modules.product.vo;
import com.gas.ecommerce.modules.product.entity.Category;
public record CategoryVO(Long id, Long parentId, String name, Integer sort, String status) {
    public static CategoryVO from(Category c) {
        return new CategoryVO(c.getId(), c.getParentId(), c.getName(), c.getSort(), c.getStatus());
    }
}
