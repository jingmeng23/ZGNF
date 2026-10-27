package com.gas.ecommerce.common.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
/**
 * 持久化日期序列：所有实例共享数据库；独立事务分配，业务回滚不回收编号。
 */
public class OrderNoGenerator {
    private final JdbcTemplate db;
    public OrderNoGenerator(JdbcTemplate db) { this.db = db; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        String day = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        db.update("INSERT INTO order_number_sequence(business_day,seq_value) VALUES(?,0) ON DUPLICATE KEY UPDATE business_day=business_day", day);
        Long current = db.queryForObject("SELECT seq_value FROM order_number_sequence WHERE business_day=? FOR UPDATE", Long.class, day);
        if (current == null || current >= 999_999_999_999L) throw new IllegalStateException("订单号序列不可用或已耗尽");
        long next = current + 1;
        db.update("UPDATE order_number_sequence SET seq_value=? WHERE business_day=?", next, day);
        // OD distinguishes the durable sequence namespace from the previous Redis GAS sequence.
        return "OD" + day + String.format(java.util.Locale.ROOT, "%012d", next);
    }
}
