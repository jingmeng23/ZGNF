package com.gas.ecommerce.modules.order.service;

import com.gas.ecommerce.modules.marketing.service.CheckoutMarketingService;
import java.util.*;
import org.springframework.stereotype.Service;

/** Each release is independently committed and idempotent; a failed release does not skip the others. */
@Service
public class CheckoutCompensationService {
    private final OrderSubmissionStore submissions;
    private final CheckoutPointsGateway points;
    private final CheckoutMarketingService marketing;
    private final OrderInventoryService inventory;
    public CheckoutCompensationService(OrderSubmissionStore submissions, CheckoutPointsGateway points,
            CheckoutMarketingService marketing, OrderInventoryService inventory) {
        this.submissions=submissions; this.points=points; this.marketing=marketing; this.inventory=inventory;
    }
    public void compensate(String number) {
        String owner=submissions.claimCompensation(number);
        if (owner==null) return;
        List<String> errors=new ArrayList<>();
        release("points",()->points.release(number),errors);
        release("marketing",()->marketing.release(number),errors);
        release("inventory",()->inventory.releaseCheckout(number),errors);
        submissions.compensated(number,owner,errors.isEmpty()?null:String.join(",",errors));
    }
    public void recover() {
        for (String number : submissions.recoverable()) {
            try { compensate(number); }
            catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Checkout compensation retry deferred: {}",number,ex);
            }
        }
    }
    private static void release(String resource, Runnable action, List<String> errors) {
        try { action.run(); } catch (RuntimeException ex) { errors.add(resource+":"+ex.getClass().getSimpleName()); }
    }
}
