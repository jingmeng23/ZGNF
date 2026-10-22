package com.gas.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gas.ecommerce.modules.auth.service.AuthService;
import com.gas.ecommerce.modules.auth.service.AdminAuthService;
import com.gas.ecommerce.modules.auth.dto.AdminLoginRequest;
import com.gas.ecommerce.modules.auth.dto.AdminLoginResponse;
import com.gas.ecommerce.modules.auth.dto.AdminProfile;
import com.gas.ecommerce.modules.product.service.CatalogManagementService;
import com.gas.ecommerce.modules.product.dto.AdminProductQuery;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.product.vo.AdminProductVO;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.gas.ecommerce.common.security.JwtTokenProvider;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GasEcommerceApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean AuthService authService;
    @MockitoBean AdminAuthService adminAuthService;
    @MockitoBean CatalogManagementService catalog;
    @Test void contextLoads() { }
    @Test void unauthenticatedRequestHas401AndTrace() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401001))
                .andExpect(jsonPath("$.traceId", not(emptyOrNullString())))
                .andExpect(header().exists("X-Request-Id"));
    }
    @Test void refreshTokenCannotAuthenticateRequests() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokens.generateRefreshToken(1L)))
                .andExpect(status().isUnauthorized());
    }
    @Test void smsEndpointReturnsUnifiedResult() throws Exception {
        mvc.perform(post("/api/v1/auth/sms-code").contentType("application/json").content("""
                {"phone":"13800138000"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.timestamp").isNumber()).andExpect(jsonPath("$.traceId").isString());
        org.mockito.Mockito.verify(authService).sendSmsCode("13800138000");
    }
    @Test void invalidPhoneReturnsFieldDetails() throws Exception {
        mvc.perform(post("/api/v1/auth/sms-code").contentType("application/json").content("{\"phone\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400001))
                .andExpect(jsonPath("$.data.phone").exists());
    }
    @Test void swaggerRedirectIsPublic() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
    @Test void openApiDocumentCanBeGenerated() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.openapi").isString());
    }

    @Test void adminLoginIsPublicAndReturnsAdminProfile() throws Exception {
        var profile = new AdminProfile(7L, "operator", List.of("admin"), List.of("product:read"));
        org.mockito.Mockito.when(adminAuthService.login(new AdminLoginRequest("operator", "a-strong-password")))
                .thenReturn(new AdminLoginResponse("admin-token", 7200, profile));
        mvc.perform(post("/api/v1/admin/auth/login").contentType("application/json")
                        .content("{\"username\":\"operator\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").value("admin-token"))
                .andExpect(jsonPath("$.data.admin.employeeId").value(7));
    }

    @Test void adminCatalogRequiresAdminRoleAndSpecificPermission() throws Exception {
        mvc.perform(get("/api/v1/admin/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/products").header("Authorization", "Bearer " + tokens.generateAccessToken(1L, "user")))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.when(adminAuthService.authorities(7L)).thenReturn(List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("product:read")));
        org.mockito.Mockito.when(catalog.products(org.mockito.ArgumentMatchers.any(AdminProductQuery.class)))
                .thenReturn(new PageResult<AdminProductVO>(List.of(), 0, 1, 20, 0));
        mvc.perform(get("/api/v1/admin/products").header("Authorization", "Bearer " + tokens.generateAccessToken(7L, "admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list").isArray());
    }
}
