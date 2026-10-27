package com.gas.ecommerce.modules.order.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.order.annotation.CheckoutIdempotentAspect;
import com.gas.ecommerce.modules.order.dto.OrderCreateRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutIdempotentAspectTests {
    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }
    @Test void modernCheckoutRequiresTokenAndDoesNotGenerateRandomFallbackKeys() throws Throwable {
        var invocation=mock(ProceedingJoinPoint.class);
        when(invocation.getArgs()).thenReturn(new Object[]{1L,new OrderCreateRequest(1L,null,null,null,"order")});
        assertThrows(BusinessException.class,()->new CheckoutIdempotentAspect().guard(invocation));
        verify(invocation,never()).proceed();
    }
    @Test void mismatchedHeaderIsRejectedBeforeCallingService() throws Throwable {
        var invocation=mock(ProceedingJoinPoint.class); String token="a".repeat(64);
        when(invocation.getArgs()).thenReturn(new Object[]{1L,new OrderCreateRequest(1L,null,null,null,"order",token)});
        MockHttpServletRequest request=new MockHttpServletRequest(); request.addHeader("Idempotency-Key","wrong");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThrows(BusinessException.class,()->new CheckoutIdempotentAspect().guard(invocation));
        verify(invocation,never()).proceed();
    }
    @Test void validRetriesAlwaysReachDurableReplayLogic() throws Throwable {
        var invocation=mock(ProceedingJoinPoint.class);
        when(invocation.getArgs()).thenReturn(new Object[]{1L,new OrderCreateRequest(1L,null,null,null,"order","a".repeat(64))});
        when(invocation.proceed()).thenReturn("same-order");
        var aspect=new CheckoutIdempotentAspect();
        assertEquals("same-order",aspect.guard(invocation)); assertEquals("same-order",aspect.guard(invocation));
        verify(invocation,times(2)).proceed();
    }
}
