package com.gas.ecommerce;

import com.gas.ecommerce.modules.order.service.OrderService;
import com.gas.ecommerce.modules.payment.dto.PaymentCallbackRequest;
import com.gas.ecommerce.modules.payment.service.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Sql(statements="DROP ALL OBJECTS")
@Sql(scripts={"/db/migration/V1.0__init_schema.sql","/db/migration/V1.1__admin_roles.sql",
        "/db/migration/V1.2__order_reservations.sql","/db/migration/V1.3__aftersale_workflow.sql",
        "/db/migration/V1.4__operation_management.sql","/db/migration/V1.5__payment_callback.sql",
        "/db/migration/V1.6__organization_management.sql","/db/migration/V1.7__coupon_workflow.sql",
        "/db/migration/V1.8__work_order_workflow.sql","/db/migration/V1.9__inventory_management.sql",
        "/db/migration/V1.10__business_statistics.sql","/db/migration/V1.11__cart_price_snapshot.sql",
        "/db/migration/V1.12__product_details.sql","/db/migration/V1.13__mall_catalog.sql",
        "/db/migration/V1.14__mall_cache_invalidation_retry.sql","/db/migration/V1.15__order_checkout_outbox.sql",
        "/db/migration/V1.16__checkout_orchestration.sql","/db/migration/V1.17__payment_recovery.sql", "/db/migration/V1.18__order_close_release.sql", "/db/migration/V1.19__order_read_timeline.sql", "/db/migration/V1.20__address_assistance.sql", "/db/migration/V1.26__address_coordinate_system.sql", "/db/migration/V1.21__admin_order_operations.sql","/db/migration/V1.22__available_sku_display_price.sql"})
class PaymentCallbackIntegrationTests {
    @Autowired JdbcTemplate db;
    @Autowired PaymentService payments;
    @Autowired PaymentRecoveryService recovery;
    @Autowired OrderService orders;
    @MockitoBean PaymentProviderGateway gateway;
    @BeforeEach void seed() {
        db.update("INSERT INTO `user`(id,status) VALUES(100,'active')");
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount,expire_time) VALUES(500,'ORDER-P',100,'pending_payment',50,50,?)",LocalDateTime.now().minusSeconds(1));
        db.update("INSERT INTO payment(id,payment_no,order_id,user_id,amount,channel,status) VALUES(600,'PAY-P',500,100,50,'test','pending')");
        db.update("INSERT INTO payment_recovery(payment_id) VALUES(600)");
    }
    PaymentCallbackRequest event(String status) {
        return new PaymentCallbackRequest("PAY-P","TRADE-P",new BigDecimal("50.00"),status,LocalDateTime.now());
    }
    String orderStatus() { return db.queryForObject("SELECT status FROM `order` WHERE id=500",String.class); }
    String paymentStatus() { return db.queryForObject("SELECT status FROM payment WHERE id=600",String.class); }
    int events() { return db.queryForObject("SELECT COUNT(*) FROM order_outbox WHERE event_type LIKE 'payment.%'",Integer.class); }
    void due() { db.update("UPDATE payment_recovery SET next_attempt_time=?",LocalDateTime.now().minusSeconds(2)); }

    @Test void duplicateCallbacksHaveOneEffectEvenWhenConcurrent() throws Exception {
        var event=event("success"); var start=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return payments.callback("test",event);});
            var b=pool.submit(()->{start.await();return payments.callback("test",event);});
            start.countDown(); assertEquals(a.get(10,TimeUnit.SECONDS).id(),b.get(10,TimeUnit.SECONDS).id());
        }
        assertEquals("paid",orderStatus()); assertEquals("success",paymentStatus()); assertEquals(3,events());
        assertEquals("done",db.queryForObject("SELECT status FROM payment_recovery",String.class));
    }
    @Test void failureThenSuccessThenLateFailureIsMonotonic() {
        payments.callback("test",event("failed")); assertEquals("pending_payment",orderStatus());
        payments.callback("test",event("success")); payments.callback("test",event("failed"));
        assertEquals("success",paymentStatus()); assertEquals("paid",orderStatus()); assertEquals(3,events());
    }
    @Test void uniqueTradeCannotBeReusedByAnotherOrder() {
        payments.callback("test",event("success"));
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount) VALUES(501,'ORDER-Q',100,'pending_payment',50,50)");
        db.update("INSERT INTO payment(id,payment_no,order_id,user_id,amount,channel,status) VALUES(601,'PAY-Q',501,100,50,'test','pending')");
        var forged=new PaymentCallbackRequest("PAY-Q","TRADE-P",new BigDecimal("50"),"success",LocalDateTime.now());
        assertThrows(RuntimeException.class,()->payments.callback("test",forged));
        assertEquals("pending",db.queryForObject("SELECT status FROM payment WHERE id=601",String.class)); assertEquals(3,events());
    }
    @Test void deadlinePassingDoesNotRejectCollectedMoney() {
        payments.callback("test",event("success")); orders.cancelExpired("ORDER-P");
        assertEquals("paid",orderStatus()); verifyNoInteractions(gateway);
    }
    @Test void closeDiscoversProviderPaymentAndPrioritizesIt() {
        when(gateway.close(any())).thenReturn(new PaymentProviderGateway.Snapshot(PaymentProviderGateway.State.SUCCESS,event("success")));
        orders.cancelExpired("ORDER-P");
        assertEquals("paid",orderStatus()); assertEquals("success",paymentStatus()); assertEquals(3,events());
    }
    @Test void unknownProviderCloseKeepsOrderAndReservations() {
        when(gateway.close(any())).thenReturn(PaymentProviderGateway.Snapshot.unknown());
        assertThrows(RuntimeException.class,()->orders.cancelExpired("ORDER-P"));
        assertEquals("pending_payment",orderStatus()); assertEquals("pending",paymentStatus()); assertEquals(0,events());
    }
    @Test void erroneousCloseThenLatePaymentGetsDurableRefundWithoutRevivingOrder() {
        when(gateway.close(any())).thenReturn(new PaymentProviderGateway.Snapshot(PaymentProviderGateway.State.CLOSED,null));
        orders.cancelExpired("ORDER-P"); assertEquals("cancelled",orderStatus());
        payments.callback("test",event("success")); payments.callback("test",event("success"));
        assertEquals("cancelled",orderStatus()); assertEquals("success",paymentStatus()); assertEquals(0,events());
        assertEquals("refund",db.queryForObject("SELECT action FROM payment_recovery",String.class));
        when(gateway.refund(any(),eq("LATE-PAY-P"))).thenReturn(true);
        due(); recovery.recover(); recovery.recover();
        assertEquals("refunded",paymentStatus());
        verify(gateway,times(1)).refund(any(),eq("LATE-PAY-P"));
    }
    @Test void concurrentCloseAndCallbackSerializeAndPreserveProviderPayment() throws Exception {
        var entered=new CountDownLatch(1); var finish=new CountDownLatch(1); var r=event("success");
        when(gateway.close(any())).thenAnswer(invocation->{entered.countDown(); assertTrue(finish.await(5,TimeUnit.SECONDS));
            return new PaymentProviderGateway.Snapshot(PaymentProviderGateway.State.SUCCESS,r);});
        try (var pool=Executors.newFixedThreadPool(2)) {
            var closing=pool.submit(()->orders.cancelExpired("ORDER-P")); assertTrue(entered.await(5,TimeUnit.SECONDS));
            var callback=pool.submit(()->payments.callback("test",r)); finish.countDown();
            closing.get(10,TimeUnit.SECONDS); callback.get(10,TimeUnit.SECONDS);
        }
        assertEquals("paid",orderStatus()); assertEquals(3,events());
    }
    @Test void concurrentDefinitiveCloseAndLateCallbackScheduleCompensation() throws Exception {
        var entered=new CountDownLatch(1); var finish=new CountDownLatch(1); var r=event("success");
        when(gateway.close(any())).thenAnswer(invocation->{entered.countDown(); assertTrue(finish.await(5,TimeUnit.SECONDS));
            return new PaymentProviderGateway.Snapshot(PaymentProviderGateway.State.CLOSED,null);});
        try (var pool=Executors.newFixedThreadPool(2)) {
            var closing=pool.submit(()->orders.cancelExpired("ORDER-P")); assertTrue(entered.await(5,TimeUnit.SECONDS));
            var callback=pool.submit(()->payments.callback("test",r)); finish.countDown();
            closing.get(10,TimeUnit.SECONDS); callback.get(10,TimeUnit.SECONDS);
        }
        assertEquals("cancelled",orderStatus()); assertEquals("success",paymentStatus()); assertEquals(0,events());
        assertEquals("refund",db.queryForObject("SELECT action FROM payment_recovery",String.class));
    }
    @Test void missingCallbackIsRecoveredThroughAuthenticatedQuery() {
        when(gateway.query(any())).thenReturn(new PaymentProviderGateway.Snapshot(PaymentProviderGateway.State.SUCCESS,event("success")));
        due(); recovery.recover(); recovery.recover();
        assertEquals("paid",orderStatus()); assertEquals(3,events()); verify(gateway,times(1)).query(any());
    }
    @Test void unconfirmedRefundRetriesTheSameRefundIdentity() {
        db.update("UPDATE `order` SET status='cancelled' WHERE id=500"); payments.callback("test",event("success"));
        when(gateway.refund(any(),eq("LATE-PAY-P"))).thenReturn(false,true);
        due(); recovery.recover(); assertEquals("success",paymentStatus());
        assertEquals("pending",db.queryForObject("SELECT status FROM payment_recovery",String.class));
        due(); recovery.recover(); assertEquals("refunded",paymentStatus()); verify(gateway,times(2)).refund(any(),eq("LATE-PAY-P"));
    }
    @Test void providerUnavailableLeavesRetryWorkAndDoesNotMarkPaymentSuccessful() {
        when(gateway.query(any())).thenReturn(PaymentProviderGateway.Snapshot.unknown());
        due(); recovery.recover();
        assertEquals("pending",paymentStatus()); assertEquals("pending_payment",orderStatus());
        assertEquals(1,db.queryForObject("SELECT attempts FROM payment_recovery",Integer.class));
        assertNotNull(db.queryForObject("SELECT last_error FROM payment_recovery",String.class));
    }
    @Test void secondSuccessfulPaymentIsRefundedRatherThanFulfilledTwice() {
        payments.callback("test",event("success"));
        db.update("INSERT INTO payment(id,payment_no,order_id,user_id,amount,channel,status) VALUES(601,'PAY-Q',500,100,50,'test','pending')");
        payments.callback("test",new PaymentCallbackRequest("PAY-Q","TRADE-Q",new BigDecimal("50"),"success",LocalDateTime.now()));
        assertEquals("paid",orderStatus()); assertEquals(3,events());
        assertEquals("refund",db.queryForObject("SELECT action FROM payment_recovery WHERE payment_id=601",String.class));
    }
}
