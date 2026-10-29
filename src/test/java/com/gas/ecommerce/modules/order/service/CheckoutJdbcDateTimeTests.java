package com.gas.ecommerce.modules.order.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.utils.CheckoutDigest;
import com.gas.ecommerce.modules.order.dto.OrderCreateRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckoutJdbcDateTimeTests {
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void ticketAcceptsBothJdbcTimeRepresentationsAndRejectsExpiredTickets(boolean mysqlTime) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var service = new CheckoutTicketService(db);
        var request = new OrderCreateRequest(1L, null, null, null, "CHECK1", "token");
        for (boolean expired : new boolean[]{false, true}) {
            LocalDateTime time = LocalDateTime.now().plusMinutes(expired ? -5 : 5);
            when(db.queryForList(anyString(), eq("CHECK1"), eq(1L))).thenReturn(List.of(Map.of(
                    "expire_time", mysqlTime ? time : Timestamp.valueOf(time),
                    "token_hash", CheckoutDigest.sha256("token"), "fingerprint", "quote")));
            if (expired) assertThrows(BusinessException.class, () -> service.validate(1L, request, "quote"));
            else assertDoesNotThrow(() -> service.validate(1L, request, "quote"));
        }
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void commitLeaseAcceptsBothJdbcTimeRepresentationsAndRejectsExpiredLease(boolean mysqlTime) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var store = new OrderSubmissionStore(db, null, null, null, null, null);
        var claim = new OrderSubmissionStore.Claim("CHECK1", "owner", false, false);
        for (boolean expired : new boolean[]{false, true}) {
            LocalDateTime time = LocalDateTime.now().plusMinutes(expired ? -5 : 5);
            when(db.queryForMap(anyString(), eq("CHECK1"))).thenReturn(Map.of("owner", "owner",
                    "phase", "PROCESSING", "lease_until", mysqlTime ? time : Timestamp.valueOf(time)));
            if (expired) assertThrows(CheckoutConflictException.class, () -> store.lockForCommit(claim));
            else assertDoesNotThrow(() -> store.lockForCommit(claim));
        }
    }
}
