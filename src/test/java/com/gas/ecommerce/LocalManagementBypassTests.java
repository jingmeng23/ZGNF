package com.gas.ecommerce;

import com.gas.ecommerce.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class LocalManagementBypassTests {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void request(boolean enabled, String remote, String path, boolean expected) throws Exception {
        var filter = new JwtAuthenticationFilter(null, null);
        ReflectionTestUtils.setField(filter, "localManagementBypass", enabled);
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setRemoteAddr(remote);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertEquals(expected, auth != null);
            if (expected) assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("employee:write")));
        });
    }
    @Test void localManagementGetsTestIdentity() throws Exception { request(true,"127.0.0.1","/api/v1/admin/employees",true); }
    @Test void remoteCannotBypass() throws Exception { request(true,"192.168.1.20","/api/v1/admin/employees",false); }
    @Test void disabledRestoresLogin() throws Exception { request(false,"127.0.0.1","/api/v1/admin/employees",false); }
    @Test void otherModulesRemainProtected() throws Exception { request(true,"127.0.0.1","/api/v1/admin/orders",false); }
}
