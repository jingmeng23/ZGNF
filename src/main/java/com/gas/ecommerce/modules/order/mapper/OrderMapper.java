package com.gas.ecommerce.modules.order.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
/**
 * 数据库访问接口，继承 BaseMapper 后可使用通用增删改查。
 */
@Mapper public interface OrderMapper extends BaseMapper<Order> { java.util.List<Long> selectExpiredOrderIds(); }
