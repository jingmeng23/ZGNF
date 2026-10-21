package com.gas.ecommerce.modules.order.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface OrderMapper extends BaseMapper<Order> { java.util.List<Long> selectExpiredOrderIds(); }
