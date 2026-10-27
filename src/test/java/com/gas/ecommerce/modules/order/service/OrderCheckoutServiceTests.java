package com.gas.ecommerce.modules.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.utils.OrderNoGenerator;
import com.gas.ecommerce.modules.coupon.service.CouponService;
import com.gas.ecommerce.modules.marketing.service.CheckoutMarketingService;
import com.gas.ecommerce.modules.order.dto.OrderCreateRequest;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.mapper.OrderItemMapper;
import com.gas.ecommerce.modules.order.service.impl.OrderServiceImpl;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCheckoutServiceTests {
    OrderInventoryService inventory=mock(OrderInventoryService.class);
    CheckoutMarketingService marketing=mock(CheckoutMarketingService.class);
    CheckoutPointsGateway points=mock(CheckoutPointsGateway.class);
    OrderSubmissionStore submissions=mock(OrderSubmissionStore.class);
    CheckoutOrderWriter writer=mock(CheckoutOrderWriter.class);
    CheckoutSettlementService settlement=mock(CheckoutSettlementService.class);
    CheckoutCompensationService compensation=mock(CheckoutCompensationService.class);
    OrderItemMapper items=mock(OrderItemMapper.class);
    OrderCreateRequest request=new OrderCreateRequest(1L,null,"request-1",null);
    OrderSubmissionStore.Claim claim=new OrderSubmissionStore.Claim("ORDER-1","owner",false,false);
    CheckoutSettlementService.Settlement quote;
    OrderServiceImpl service;
    @BeforeEach void setup() {
        quote=new CheckoutSettlementService.Settlement(java.util.List.of(),java.util.List.of(),java.math.BigDecimal.TEN,null,
                new CheckoutMarketingService.Quote(java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO),"fingerprint");
        service=new OrderServiceImpl(mock(CouponService.class),marketing,mock(UserMapper.class),items,inventory,
                mock(OrderNoGenerator.class),new ObjectMapper(),"test-key",new SimpleMeterRegistry(),mock(CheckoutTicketService.class),
                settlement,submissions,writer,compensation,points,
                mock(com.gas.ecommerce.modules.payment.service.PaymentCloseGuard.class),mock(org.springframework.jdbc.core.JdbcTemplate.class),mock(OrderCloseReleaseService.class),mock(OrderDetailAssembler.class));
        when(submissions.claim(10L,request)).thenReturn(claim);
        when(settlement.settle(10L,1L,null)).thenReturn(quote);
        when(submissions.fail(eq(claim),any())).thenReturn(true);
        Order order=new Order(); order.setId(1L); order.setUserId(10L); order.setOrderNo("ORDER-1");
        when(writer.save(10L,request,claim,quote)).thenReturn(order);
        when(items.selectList(any())).thenReturn(java.util.List.of());
    }
    @Test void reserveInventoryThenMarketingThenPointsBeforeWritingOrder() {
        assertEquals("ORDER-1",service.create(10L,request).orderNo());
        var order=inOrder(submissions,inventory,marketing,points,writer);
        order.verify(submissions).claim(10L,request);
        order.verify(submissions).validateQuote(10L,request,"fingerprint");
        order.verify(inventory).lockCheckout("ORDER-1",quote.items());
        order.verify(marketing).reserve("ORDER-1",10L,null,quote.total(),quote.marketing());
        order.verify(points).reserve("ORDER-1",10L);
        order.verify(writer).save(10L,request,claim,quote);
        verifyNoInteractions(compensation);
    }
    @Test void inventoryFailureNeverReservesMarketingOrWritesAnOrder() {
        doThrow(new IllegalStateException("inventory timeout")).when(inventory).lockCheckout(anyString(),anyList());
        assertThrows(IllegalStateException.class,()->service.create(10L,request));
        verifyNoInteractions(marketing,points,writer);
        verify(submissions).fail(eq(claim),any()); verify(compensation).compensate("ORDER-1");
    }
    @Test void unknownPointsOutcomeIsCompensatedEvenWhenReserveThrows() {
        doThrow(new IllegalStateException("reply lost after points reserve")).when(points).reserve(anyString(),anyLong());
        assertThrows(IllegalStateException.class,()->service.create(10L,request));
        verifyNoInteractions(writer); verify(compensation).compensate("ORDER-1");
    }
    @Test void orderWriteFailurePersistsCompensationIntentBeforeCallingRelease() {
        when(writer.save(anyLong(),any(),any(),any())).thenThrow(new IllegalStateException("database write failed"));
        assertThrows(IllegalStateException.class,()->service.create(10L,request));
        var order=inOrder(submissions,compensation);
        order.verify(submissions).fail(eq(claim),any()); order.verify(compensation).compensate("ORDER-1");
    }
    @Test void unavailableCompensationStoreNeverTriggersAnUnrecordedRelease() {
        doThrow(new IllegalStateException("inventory timeout")).when(inventory).lockCheckout(anyString(),anyList());
        when(submissions.fail(eq(claim),any())).thenThrow(new IllegalStateException("database offline"));
        var failure=assertThrows(IllegalStateException.class,()->service.create(10L,request));
        assertEquals(1,failure.getSuppressed().length); verifyNoInteractions(compensation);
    }
}
