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
    @Test void globalBypassWorksForEveryModuleWithoutToken() throws Exception {
        for (String path : java.util.List.of("/api/v1/admin/orders", "/api/v1/users/me/addresses", "/api/v1/cart")) {
            var filter = new JwtAuthenticationFilter(null, null);
            var identity = org.mockito.Mockito.mock(com.gas.ecommerce.common.security.TestIdentityService.class);
            ReflectionTestUtils.setField(filter, "loginDisabled", true);
            ReflectionTestUtils.setField(filter, "testIdentity", identity);
            var request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            request.setRemoteAddr("192.168.1.20");
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                assertEquals(-1L, auth.getPrincipal());
                assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("TEST_LOGIN_BYPASS")));
                assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
                assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
            });
            org.mockito.Mockito.verify(identity).ensureUser();
            SecurityContextHolder.clearContext();
        }
    }
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
