package com.gas.ecommerce.modules.order.service;

import com.gas.ecommerce.modules.marketing.service.CheckoutMarketingService;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class CheckoutCompensationServiceTests {
    @Test void releasesInReverseOrderAndContinuesAfterOneReleaseFails() {
        var submissions=mock(OrderSubmissionStore.class); var points=mock(CheckoutPointsGateway.class);
        var marketing=mock(CheckoutMarketingService.class); var inventory=mock(OrderInventoryService.class);
        when(submissions.claimCompensation("order")).thenReturn("owner");
        doThrow(new IllegalStateException("offline")).when(points).release("order");
        new CheckoutCompensationService(submissions,points,marketing,inventory).compensate("order");
        var order=inOrder(points,marketing,inventory,submissions);
        order.verify(points).release("order"); order.verify(marketing).release("order");
        order.verify(inventory).releaseCheckout("order");
        order.verify(submissions).compensated("order","owner","points:IllegalStateException");
    }
    @Test void successfulOrAlreadyClaimedSubmissionIsNeverReleased() {
        var submissions=mock(OrderSubmissionStore.class); var points=mock(CheckoutPointsGateway.class);
        var marketing=mock(CheckoutMarketingService.class); var inventory=mock(OrderInventoryService.class);
        new CheckoutCompensationService(submissions,points,marketing,inventory).compensate("order");
        verifyNoInteractions(points,marketing,inventory);
    }
}
