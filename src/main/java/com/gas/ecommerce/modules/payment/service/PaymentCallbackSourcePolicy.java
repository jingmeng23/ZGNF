package com.gas.ecommerce.modules.payment.service;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/** Match the actual socket peer. Never trust caller-supplied X-Forwarded-For headers. */
@Component
public class PaymentCallbackSourcePolicy {
    private final List<IpAddressMatcher> allowed;
    public PaymentCallbackSourcePolicy(@Value("${app.payments.callback-allowed-peers:}") String peers) {
        allowed=Arrays.stream(peers.split(",")).map(String::trim).filter(s->!s.isEmpty())
                .map(IpAddressMatcher::new).toList();
    }
    public void verify(String peer) {
        if (peer==null || allowed.stream().noneMatch(m->m.matches(peer)))
            throw new AccessDeniedException("Untrusted payment callback peer");
    }
}
