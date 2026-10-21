package com.gas.ecommerce.modules.product.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface ProductMapper extends BaseMapper<Product> { java.util.List<Long> selectActiveProductIds(); }
