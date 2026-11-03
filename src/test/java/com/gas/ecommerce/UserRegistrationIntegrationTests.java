package com.gas.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.utils.EncryptUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
@Sql(statements="DROP ALL OBJECTS")
@Sql(scripts="/db/migration/V1.0__init_schema.sql")
@Sql(statements={"ALTER TABLE `user` ADD COLUMN email VARCHAR(320)",
        "ALTER TABLE `user` ADD COLUMN email_hash VARCHAR(64)",
        "ALTER TABLE `user` ADD COLUMN password_hash VARCHAR(255)",
        "ALTER TABLE `user` ADD COLUMN gender VARCHAR(16)",
        "ALTER TABLE `user` ADD COLUMN birthday DATE",
        "ALTER TABLE `user` ADD COLUMN member_level VARCHAR(32) DEFAULT '普通会员' NOT NULL",
        "ALTER TABLE `user` ADD COLUMN points BIGINT DEFAULT 0 NOT NULL",
        "CREATE TABLE user_login_log (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,login_type VARCHAR(32) NOT NULL,success BOOLEAN NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"})
class UserRegistrationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    @Test void anonymousRegistrationPersistsAccountAndAllowsPasswordLogin() throws Exception {
        String phone = "13912345678";
        String password = "StrongPass123";
        var registration = mvc.perform(post("/api/v1/users/register").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone",phone,"password",password,"nickname","New member"))))
                .andExpect(status().isOk()).andReturn();

        var response = json.readTree(registration.getResponse().getContentAsString());
        assertEquals(200,response.path("code").asInt());
        assertFalse(response.path("data").has("accessToken"));
        String phoneHash = EncryptUtils.fingerprint(phone,"test-only-phone-encryption-key-2026");
        Map<String,Object> user = db.queryForMap("SELECT id,phone,password_hash,status,nickname FROM `user` WHERE phone_hash=?",phoneHash);
        assertEquals(phone,EncryptUtils.decryptAes((String)user.get("phone"),"test-only-phone-encryption-key-2026"));
        assertNotEquals(password,user.get("password_hash"));
        assertEquals("active",user.get("status"));
        assertEquals("New member",user.get("nickname"));

        var login = mvc.perform(post("/api/v1/users/login/password").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("account",phone,"password",password))))
                .andExpect(status().isOk()).andReturn();
        assertTrue(json.readTree(login.getResponse().getContentAsString()).path("data").path("accessToken").asText().length()>20);
    }
}
