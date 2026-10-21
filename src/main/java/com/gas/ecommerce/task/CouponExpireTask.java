package com.gas.ecommerce.task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component public class CouponExpireTask {
    private final JdbcTemplate jdbc;
    public CouponExpireTask(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Shanghai") public void expireCoupons() {
        jdbc.update("UPDATE coupon SET status='expired', update_time=NOW() WHERE status='active' AND end_time<NOW() AND is_deleted=0");
        jdbc.update("UPDATE user_coupon SET status='expired', update_time=NOW() WHERE status='unused' AND expire_time<NOW() AND is_deleted=0");
    }
}