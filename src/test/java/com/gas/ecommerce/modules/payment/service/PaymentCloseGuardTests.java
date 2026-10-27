package com.gas.ecommerce.modules.payment.service;

import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentCloseGuardTests {
    @Test void enumerationUsesCurrentReadAndRetainsResourcesForUncertainCommittedAttempt() {
        var mapper=mock(PaymentMapper.class);var gateway=mock(PaymentProviderGateway.class);
        var outcomes=mock(PaymentOutcomeHandler.class);
        var guard=new PaymentCloseGuard(mapper,gateway,outcomes);
        var order=new Order();order.setId(500L);
        var attempt=new Payment();attempt.setId(600L);attempt.setStatus("pending");
        when(mapper.lockAttemptsForOrder(500L)).thenReturn(List.of(attempt));
        when(gateway.close(attempt)).thenReturn(PaymentProviderGateway.Snapshot.unknown());
        assertThrows(RuntimeException.class,()->guard.mayClose(order));
        verify(mapper).lockAttemptsForOrder(500L);verifyNoMoreInteractions(mapper);
        verify(gateway).close(attempt);verifyNoInteractions(outcomes);
    }
}
