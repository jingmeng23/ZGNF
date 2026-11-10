package com.gas.ecommerce;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.security.JwtTokenProvider;
import com.gas.ecommerce.modules.payment.wechat.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.wechat.enabled=true","app.wechat.app-id=wx-integration","app.orders.expiry-scan-enabled=false"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements="DROP ALL OBJECTS")
@Sql(scripts={"/db/migration/V1.0__init_schema.sql","/db/migration/V1.29__wechat_mini_program.sql"})
class WechatIdentityIntegrationTests {
    @Autowired WechatIdentityService identities;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider tokens;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.gas.ecommerce.modules.payment.service.PaymentProviderGateway gateway;
    @MockitoBean WechatClient client;
    @BeforeEach void seed() {
        db.update("INSERT INTO `user`(id,status) VALUES(100,'active'),(101,'active'),(102,'inactive')");
        when(client.openId("valid-code")).thenReturn("verified-openid");
        when(client.openId("other-code")).thenReturn("other-openid");
    }
    @Test void verifiedWechatLoginReusesExistingMallAccount() {
        identities.bind(100L,"valid-code"); identities.bind(100L,"valid-code");
        var result=identities.login("valid-code");
        assertTrue(tokens.validateAccessToken(result.accessToken()));
        assertEquals(100L,tokens.getUserIdFromToken(result.accessToken()));
        assertEquals("user",tokens.getRoleFromToken(result.accessToken()));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM wechat_mini_binding",Integer.class));
        assertEquals("verified-openid",identities.openId(100L));
    }
    @Test void openidAndMallUserAreBothUniqueAndCannotBeRebound() {
        identities.bind(100L,"valid-code");
        assertThrows(BusinessException.class,()->identities.bind(101L,"valid-code"));
        assertThrows(BusinessException.class,()->identities.bind(100L,"other-code"));
        assertThrows(BusinessException.class,()->identities.login("other-code"));
    }
    @Test void disabledUsersCannotBindLoginOrStartNewPaymentButReceiptsRemainVerifiable() {
        assertThrows(BusinessException.class,()->identities.bind(102L,"valid-code"));
        identities.bind(100L,"valid-code");
        db.update("UPDATE `user` SET status='inactive' WHERE id=100");
        assertThrows(BusinessException.class,()->identities.login("valid-code"));
        assertThrows(BusinessException.class,()->identities.openId(100L));
        assertEquals("verified-openid",identities.paymentOpenId(100L));
    }
    @Test void unboundWechatDoesNotSilentlyCreateAnUnrelatedMallAccount() {
        assertThrows(BusinessException.class,()->identities.login("valid-code"));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM `user`",Integer.class));
        verify(client).openId("valid-code");
    }
    @Test void enabledWechatAdapterReplacesUnavailableFallback() { assertInstanceOf(WechatPayGateway.class,gateway); }
    @Test void bindingAndPrepayRequireUserAuthenticationAndUnboundLoginIsPublic() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users/me/wechat-binding")
                .contentType("application/json").content("{\"code\":\"valid-code\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/payments/wechat/prepay")
                .contentType("application/json").content("{\"orderId\":1}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/wechat/login")
                .contentType("application/json").content("{\"code\":\"valid-code\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
}
