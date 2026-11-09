package com.gas.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.security.JwtTokenProvider;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
@Sql(statements="DROP ALL OBJECTS")
@Sql(scripts={"/db/migration/V1.0__init_schema.sql","/db/migration/V1.1__admin_roles.sql",
        "/db/migration/V1.2__order_reservations.sql","/db/migration/V1.3__aftersale_workflow.sql",
        "/db/migration/V1.4__operation_management.sql","/db/migration/V1.5__payment_callback.sql",
        "/db/migration/V1.6__organization_management.sql", "/db/migration/V1.7__coupon_workflow.sql", "/db/migration/V1.8__work_order_workflow.sql", "/db/migration/V1.9__inventory_management.sql", "/db/migration/V1.10__business_statistics.sql", "/db/migration/V1.28__remove_store_management.sql"})
class OrganizationManagementIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtTokenProvider tokens;
    @Autowired com.gas.ecommerce.modules.system.service.OrganizationManagementService management;
    String admin;
    @BeforeEach void seed() {
        db.update("INSERT INTO employee(id,username,password_hash,status) VALUES(7,'root',?,'active')",passwords.encode("original-password"));
        db.update("INSERT INTO employee_role(employee_id,role_id) SELECT 7,id FROM role WHERE role_key='admin'");
        admin="Bearer "+tokens.generateAccessToken(7L,"admin");
    }
    Map<String,Object> employeeBody(String status) {
        Map<String,Object> r=new HashMap<>(Map.of("username","clerk","name","店员","phone","13800138000","status",status));
        return r;
    }
    long create(MockHttpServletRequestBuilder request,Object body) throws Exception {
        var result=mvc.perform(request.header("Authorization",admin).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
    }
    void putOk(String path,Object body) throws Exception { create(put(path),body); }
    long newEmployee() throws Exception {
        return create(post("/api/v1/admin/employees"),Map.of("employee",employeeBody("active"),"password","a-strong-password"));
    }
    long newRole() throws Exception {
        return create(post("/api/v1/admin/roles"),Map.of("name","员工只读","roleKey","employee_reader","status","active"));
    }
    long permission(String key) { return db.queryForObject("SELECT id FROM permission WHERE permission_key=?",Long.class,key); }
    @Test void employeeCrudEncryptsSecretsAndChecksReferences() throws Exception {
        long employee=newEmployee();
        assertNotEquals("13800138000",db.queryForObject("SELECT phone FROM employee WHERE id=?",String.class,employee));
        assertTrue(passwords.matches("a-strong-password",db.queryForObject("SELECT password_hash FROM employee WHERE id=?",String.class,employee)));
        mvc.perform(get("/api/v1/admin/employees/"+employee).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist()).andExpect(jsonPath("$.data.storeId").doesNotExist());
        mvc.perform(post("/api/v1/admin/employees").header("Authorization",admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("employee",employeeBody("active"),"password","a-strong-password"))))
                .andExpect(status().isBadRequest());
        var invalid=employeeBody("active"); invalid.put("departmentId",99999);
        mvc.perform(put("/api/v1/admin/employees/"+employee).header("Authorization",admin).contentType("application/json")
                .content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        putOk("/api/v1/admin/employees/"+employee,employeeBody("inactive"));
        mvc.perform(delete("/api/v1/admin/employees/"+employee).header("Authorization",admin)).andExpect(status().isOk());
    }
    @Test void unreadableHistoricalPhoneDoesNotBreakListOrEmployeeCreation() throws Exception {
        String oldPhone=com.gas.ecommerce.common.utils.EncryptUtils.encryptAes("13800135141","previous-encryption-key");
        db.update("UPDATE employee SET phone=?,phone_last4='5141' WHERE id=7",oldPhone);
        mvc.perform(get("/api/v1/admin/employees").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].phone").value("*******5141"))
                .andExpect(jsonPath("$.data.list[0].phoneUnavailable").value(true));
        long employee=newEmployee();
        mvc.perform(get("/api/v1/admin/employees").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/v1/admin/employees/"+employee).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("13800138000"));
        assertEquals(oldPhone,db.queryForObject("SELECT phone FROM employee WHERE id=7",String.class));
        var repaired=employeeBody("active"); repaired.put("username","root");
        putOk("/api/v1/admin/employees/7",repaired);
        mvc.perform(get("/api/v1/admin/employees/7").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.phoneUnavailable").doesNotExist());
    }
    @Test void customRolesLoginAndPermissionRevocationTakeEffectWithExistingToken() throws Exception {
        long employee=newEmployee(), role=newRole();
        putOk("/api/v1/admin/roles/"+role+"/permissions",Map.of("ids",List.of(permission("employee:read"),permission("employee:write"))));
        putOk("/api/v1/admin/employees/"+employee+"/roles",Map.of("ids",List.of(role)));
        var login=mvc.perform(post("/api/v1/admin/auth/login").contentType("application/json")
                .content("{\"username\":\"clerk\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk()).andReturn();
        String bearer="Bearer "+json.readTree(login.getResponse().getContentAsString()).at("/data/accessToken").asText();
        mvc.perform(get("/api/v1/admin/employees").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/roles").header("Authorization",bearer).contentType("application/json")
                .content("{\"name\":\"Reader\",\"roleKey\":\"other_reader\",\"status\":\"active\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/employees/7/password").header("Authorization",bearer).contentType("application/json")
                .content("{\"password\":\"new-strong-password\"}")).andExpect(status().isForbidden());
        putOk("/api/v1/admin/roles/"+role+"/permissions",Map.of("ids",List.of()));
        mvc.perform(get("/api/v1/admin/employees").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/roles/"+role).header("Authorization",admin)).andExpect(status().isBadRequest());
        putOk("/api/v1/admin/employees/"+employee+"/roles",Map.of("ids",List.of()));
        mvc.perform(get("/api/v1/admin/auth/me").header("Authorization",bearer)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/admin/roles/"+role).header("Authorization",admin)).andExpect(status().isOk());
    }
    @Test void administratorProtectionAndInvalidAssignmentsRollback() throws Exception {
        long role=db.queryForObject("SELECT id FROM role WHERE role_key='admin'",Long.class);
        mvc.perform(put("/api/v1/admin/employees/7/roles").header("Authorization",admin).contentType("application/json").content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM employee_role WHERE employee_id=7",Integer.class));
        mvc.perform(delete("/api/v1/admin/employees/7").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/roles/"+role).header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/roles/"+role+"/permissions").header("Authorization",admin).contentType("application/json").content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest());
        long custom=newRole();
        for(String ids:List.of("[999999]","[1,1]","[null]","[-1]")) {
            mvc.perform(put("/api/v1/admin/roles/"+custom+"/permissions").header("Authorization",admin).contentType("application/json").content("{\"ids\":"+ids+"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/admin/employees").header("Authorization",admin).param("sort","id; DROP TABLE store"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/employees").header("Authorization",admin).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/employees")).andExpect(status().isUnauthorized());
    }
    @Test void passwordResetAndDisabledEmployeeLogin() throws Exception {
        long employee=newEmployee(),role=newRole();
        putOk("/api/v1/admin/employees/"+employee+"/roles",Map.of("ids",List.of(role)));
        mvc.perform(put("/api/v1/admin/employees/"+employee+"/password").header("Authorization",admin).contentType("application/json")
                .content("{\"password\":\"new-strong-password\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/auth/login").contentType("application/json").content("{\"username\":\"clerk\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/auth/login").contentType("application/json").content("{\"username\":\"clerk\",\"password\":\"new-strong-password\"}"))
                .andExpect(status().isOk());
        String bearer="Bearer "+tokens.generateAccessToken(employee,"admin");
        putOk("/api/v1/admin/employees/"+employee,employeeBody("inactive"));
        mvc.perform(get("/api/v1/admin/auth/me").header("Authorization",bearer)).andExpect(status().isUnauthorized());
    }

    @Test void employeeOptionsRespectReadAccessAndExposeOnlyFormMetadata() throws Exception {
        db.update("INSERT INTO department(id,name) VALUES(900,'Operations')");
        db.update("INSERT INTO position(id,name) VALUES(900,'Technician')");
        db.update("INSERT INTO department(id,name,is_deleted) VALUES(901,'Deleted department',1)");
        long employee=newEmployee(),role=newRole();
        putOk("/api/v1/admin/roles/"+role+"/permissions",Map.of("ids",List.of(permission("employee:read"))));
        putOk("/api/v1/admin/employees/"+employee+"/roles",Map.of("ids",List.of(role)));
        String reader="Bearer "+tokens.generateAccessToken(employee,"admin");
        mvc.perform(get("/api/v1/admin/employees/options").header("Authorization",reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canWrite").value(false))
                .andExpect(jsonPath("$.data.canManageRoles").value(false))
                .andExpect(jsonPath("$.data.currentEmployeeId").value(Long.toString(employee)))
                .andExpect(jsonPath("$.data.stores").doesNotExist())
                .andExpect(jsonPath("$.data.departments.length()").value(1))
                .andExpect(jsonPath("$.data.positions[0].name").value("Technician"));
        mvc.perform(get("/api/v1/admin/employees/options").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canWrite").value(true))
                .andExpect(jsonPath("$.data.canManageRoles").value(true));
        mvc.perform(put("/api/v1/admin/employees/"+employee+"/status").header("Authorization",reader)
                .contentType("application/json").content("{\"status\":\"inactive\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/employees/options")).andExpect(status().isUnauthorized());
        putOk("/api/v1/admin/roles/"+role+"/permissions",Map.of("ids",List.of(permission("role:read"))));
        mvc.perform(get("/api/v1/admin/employees/options").header("Authorization",reader)).andExpect(status().isForbidden());
    }

    @Test void employeeStatusEndpointPreservesProfileAndProtectsLastAdministrator() throws Exception {
        long employee=newEmployee(),role=newRole();
        putOk("/api/v1/admin/employees/"+employee+"/roles",Map.of("ids",List.of(role)));
        String staff="Bearer "+tokens.generateAccessToken(employee,"admin");
        String hash=db.queryForObject("SELECT password_hash FROM employee WHERE id=?",String.class,employee);
        putOk("/api/v1/admin/employees/"+employee+"/status",Map.of("status","inactive"));
        mvc.perform(get("/api/v1/admin/employees/"+employee).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("clerk"))
                .andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.status").value("inactive"));
        assertEquals(hash,db.queryForObject("SELECT password_hash FROM employee WHERE id=?",String.class,employee));
        mvc.perform(get("/api/v1/admin/auth/me").header("Authorization",staff)).andExpect(status().isUnauthorized());
        for(String body:List.of("{}","{\"status\":\"deleted\"}")) {
            mvc.perform(put("/api/v1/admin/employees/"+employee+"/status").header("Authorization",admin)
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/admin/employees/7/status").header("Authorization",admin)
                .contentType("application/json").content("{\"status\":\"inactive\"}"))
                .andExpect(status().isBadRequest());
        assertEquals("active",db.queryForObject("SELECT status FROM employee WHERE id=7",String.class));
        putOk("/api/v1/admin/employees/"+employee+"/status",Map.of("status","active"));
        mvc.perform(get("/api/v1/admin/auth/me").header("Authorization",staff)).andExpect(status().isOk());
    }

    @Test void employeeStatusCannotInvalidateInProgressWorkOrder() throws Exception {
        long employee=newEmployee(), role=newRole();
        putOk("/api/v1/admin/roles/"+role+"/permissions",Map.of("ids",List.of(permission("workorder:process"))));
        putOk("/api/v1/admin/employees/"+employee+"/roles",Map.of("ids",List.of(role)));
        db.update("INSERT INTO work_order(work_order_no,user_id,employee_id,type,status,title) VALUES('STAFF-GUARD',100,?,'repair','processing','Repair')",employee);
        mvc.perform(put("/api/v1/admin/employees/"+employee+"/status").header("Authorization",admin)
                .contentType("application/json").content("{\"status\":\"inactive\"}"))
                .andExpect(status().isBadRequest());
        assertEquals("active",db.queryForObject("SELECT status FROM employee WHERE id=?",String.class,employee));
    }

    @Test void concurrentDisableCannotRemoveBothAdministrators() throws Exception {
        long second=newEmployee();
        long role=db.queryForObject("SELECT id FROM role WHERE role_key='admin'",Long.class);
        putOk("/api/v1/admin/employees/"+second+"/roles",Map.of("ids",List.of(role)));
        var firstRequest=new com.gas.ecommerce.modules.system.dto.EmployeeRequest("root","管理员","13900139000",null,null,"inactive");
        var secondRequest=new com.gas.ecommerce.modules.system.dto.EmployeeRequest("clerk","店员","13800138000",null,null,"inactive");
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await(); return disable(7L,firstRequest);});
            var other=pool.submit(()->{start.await(); return disable(second,secondRequest);});
            start.countDown();
            assertNotEquals(first.get(10,java.util.concurrent.TimeUnit.SECONDS),other.get(10,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM employee WHERE status='active' AND is_deleted=0",Integer.class));
    }

    boolean disable(Long id,com.gas.ecommerce.modules.system.dto.EmployeeRequest r) {
        try { management.updateEmployee(id,r); return true; }
        catch(com.gas.ecommerce.common.exception.BusinessException ex) { return false; }
    }
}
