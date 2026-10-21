package com.gas.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gas.ecommerce.modules.auth.service.AuthService;
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
}
