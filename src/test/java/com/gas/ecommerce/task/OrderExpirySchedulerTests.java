package com.gas.ecommerce.task;

import com.gas.ecommerce.modules.order.service.OrderService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderExpirySchedulerTests {
    JdbcTemplate db;
    OrderService orders=mock(OrderService.class);
    OrderExpiryScheduler scheduler;
    @BeforeEach void setup() {
        var source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:expiry_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(source);
        db.execute("CREATE TABLE `order`(id BIGINT PRIMARY KEY,order_no VARCHAR(64),status VARCHAR(30),expire_time TIMESTAMP,is_deleted INT DEFAULT 0)");
        scheduler=new OrderExpiryScheduler(db,orders);
    }
    void expired(long id) {
        db.update("INSERT INTO `order`(id,order_no,status,expire_time) VALUES(?,?,'pending_payment',?)",id,"ORDER-"+id,LocalDateTime.now().minusMinutes(1));
    }
    @Test void firstHundredFailuresCannotStarveLaterOrdersAndAreRetriedNextRun() {
        for(long id=1;id<=205;id++) expired(id);
        doAnswer(call->{
            long id=Long.parseLong(((String)call.getArgument(0)).substring(6));
            if(id<=100) throw new IllegalStateException("Provider UNKNOWN");
            db.update("UPDATE `order` SET status='cancelled' WHERE id=?",id);
            return null;
        }).when(orders).cancelExpired(anyString());
        scheduler.cancelExpiredOrders();
        assertEquals(105,db.queryForObject("SELECT COUNT(*) FROM `order` WHERE status='cancelled'",Integer.class));
        verify(orders).cancelExpired("ORDER-101");verify(orders).cancelExpired("ORDER-205");
        scheduler.cancelExpiredOrders();
        verify(orders,times(2)).cancelExpired("ORDER-1");
        verify(orders,times(1)).cancelExpired("ORDER-101");
    }
    @Test void scanWindowExcludesPaidDeletedFutureAndNewlyInsertedOrders() {
        expired(1);expired(2);expired(3);expired(4);
        db.update("UPDATE `order` SET status='paid' WHERE id=2");
        db.update("UPDATE `order` SET is_deleted=1 WHERE id=3");
        db.update("UPDATE `order` SET expire_time=? WHERE id=4",LocalDateTime.now().plusMinutes(1));
        doAnswer(call->{db.update("UPDATE `order` SET status='cancelled' WHERE id=1");expired(5);return null;}).when(orders).cancelExpired("ORDER-1");
        scheduler.cancelExpiredOrders();
        verify(orders).cancelExpired("ORDER-1");verifyNoMoreInteractions(orders);
        scheduler.cancelExpiredOrders();
        verify(orders).cancelExpired("ORDER-5");
    }
    @Test void emptyScanDoesNothing() { scheduler.cancelExpiredOrders();verifyNoInteractions(orders); }
}
