package com.gas.ecommerce.modules.payment.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
/**
 * 数据库访问接口，继承 BaseMapper 后可使用通用增删改查。
 */
@Mapper public interface PaymentMapper extends BaseMapper<Payment> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM payment WHERE order_id=#{orderId} AND status='success' AND is_deleted=0 ORDER BY id LIMIT 1 FOR UPDATE")
    Payment lockSuccessfulForOrder(@org.apache.ibatis.annotations.Param("orderId") Long orderId);
    @org.apache.ibatis.annotations.Select("SELECT * FROM payment WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Payment lockById(@org.apache.ibatis.annotations.Param("id") Long id);
    /** Current read after acquiring the order lock: never enumerate attempts from an RR snapshot. */
    @org.apache.ibatis.annotations.Select("SELECT * FROM payment WHERE order_id=#{orderId} AND is_deleted=0 ORDER BY id FOR UPDATE")
    java.util.List<Payment> lockAttemptsForOrder(@org.apache.ibatis.annotations.Param("orderId") Long orderId);
}
