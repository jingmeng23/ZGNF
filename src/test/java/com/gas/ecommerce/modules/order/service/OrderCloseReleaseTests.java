package com.gas.ecommerce.modules.order.service;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.coupon.service.CouponService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderCloseReleaseTests {
    JdbcTemplate db; OrderCloseReleaseService service;
    OrderInventoryService stock=mock(OrderInventoryService.class);
    CouponService coupons=mock(CouponService.class);
    CheckoutPointsGateway points=mock(CheckoutPointsGateway.class);
    Order order=new Order(); SimpleMeterRegistry metrics=new SimpleMeterRegistry();
    @BeforeEach void setup() {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:close"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(source);
        db.execute("CREATE TABLE order_close_release(order_id BIGINT PRIMARY KEY,step INT DEFAULT 0,attempts INT DEFAULT 0,next_attempt_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,last_error VARCHAR(255))");
        db.update("INSERT INTO order_close_release(order_id) VALUES(1)");
        order.setId(1L);order.setOrderNo("ORDER-1");order.setStatus("cancelled");
        var orders=mock(OrderMapper.class);when(orders.lockById(1L)).thenReturn(order);
        service=new OrderCloseReleaseService(db,orders,stock,coupons,points,new DataSourceTransactionManager(source),metrics);
    }
    int step(){return db.queryForObject("SELECT step FROM order_close_release",Integer.class);}
    @Test void orderedReleaseAndDuplicateScanHaveOneEffect() {
        service.scan();service.scan();assertEquals(3,step());
        var sequence=inOrder(stock,coupons,points);sequence.verify(stock).release(order);sequence.verify(coupons).release(order);sequence.verify(points).release("ORDER-1");
        verifyNoMoreInteractions(stock,coupons,points);
    }
    @Test void couponFailureRetriesFromCouponAndAlerts() {
        doThrow(new IllegalStateException("offline")).doNothing().when(coupons).release(order);
        service.scan();assertEquals(1,step());verifyNoInteractions(points);
        assertEquals(1,metrics.counter("order.close.release.failures").count());
        service.scan();verify(coupons,times(1)).release(order);
        db.update("UPDATE order_close_release SET next_attempt_time=?",java.time.LocalDateTime.now().minusSeconds(1));
        service.scan();assertEquals(3,step());verify(stock,times(1)).release(order);verify(coupons,times(2)).release(order);verify(points).release("ORDER-1");
    }
    @Test void stockFailurePreventsLaterSteps() {
        doThrow(new IllegalStateException()).when(stock).release(order);service.scan();
        assertEquals(0,step());verifyNoInteractions(coupons,points);
    }
    @Test void pointsFailureKeepsEarlierStepsCommitted() {
        doThrow(new IllegalStateException()).doNothing().when(points).release("ORDER-1");service.scan();assertEquals(2,step());
        db.update("UPDATE order_close_release SET next_attempt_time=?",java.time.LocalDateTime.now().minusSeconds(1));service.scan();
        assertEquals(3,step());verify(stock,times(1)).release(order);verify(coupons,times(1)).release(order);
    }
}
