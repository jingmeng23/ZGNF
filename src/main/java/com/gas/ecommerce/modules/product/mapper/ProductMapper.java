package com.gas.ecommerce.modules.product.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
/**
 * 数据库访问接口，继承 BaseMapper 后可使用通用增删改查。
 */
@Mapper public interface ProductMapper extends BaseMapper<Product> { java.util.List<Long> selectActiveProductIds(); }
