package com.gas.ecommerce.modules.product.mapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CatalogLockMapper {
    @Select("SELECT id FROM catalog_lock WHERE id = 1 FOR UPDATE")
    Integer lock();
}
