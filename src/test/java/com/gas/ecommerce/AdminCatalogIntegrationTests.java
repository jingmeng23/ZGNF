package com.gas.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.security.JwtTokenProvider;
import com.gas.ecommerce.common.utils.OrderNoGenerator;
import com.gas.ecommerce.modules.order.service.OrderService;
import com.gas.ecommerce.modules.auth.service.AdminBootstrap;
import com.gas.ecommerce.modules.auth.mapper.AdminAccessMapper;
import com.gas.ecommerce.modules.system.mapper.EmployeeMapper;
import com.gas.ecommerce.modules.system.mapper.RoleMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Sql(statements = "DROP ALL OBJECTS")
@Sql(scripts = {"/db/migration/V1.0__init_schema.sql", "/db/migration/V1.1__admin_roles.sql",
        "/db/migration/V1.2__order_reservations.sql", "/db/migration/V1.3__aftersale_workflow.sql",
        "/db/migration/V1.4__operation_management.sql", "/db/migration/V1.5__payment_callback.sql",
        "/db/migration/V1.6__organization_management.sql", "/db/migration/V1.7__coupon_workflow.sql", "/db/migration/V1.8__work_order_workflow.sql", "/db/migration/V1.9__inventory_management.sql", "/db/migration/V1.10__business_statistics.sql", "/db/migration/V1.11__cart_price_snapshot.sql", "/db/migration/V1.12__product_details.sql", "/db/migration/V1.13__mall_catalog.sql", "/db/migration/V1.14__mall_cache_invalidation_retry.sql", "/db/migration/V1.15__order_checkout_outbox.sql", "/db/migration/V1.16__checkout_orchestration.sql", "/db/migration/V1.17__payment_recovery.sql", "/db/migration/V1.18__order_close_release.sql", "/db/migration/V1.19__order_read_timeline.sql", "/db/migration/V1.20__address_assistance.sql", "/db/migration/V1.21__admin_order_operations.sql","/db/migration/V1.22__available_sku_display_price.sql", "/db/migration/V1.23__aftersale_management.sql"})
class AdminCatalogIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtTokenProvider tokens;
    @Autowired EmployeeMapper employees;
    @Autowired RoleMapper roles;
    @Autowired AdminAccessMapper access;
    @Autowired OrderService orders;
    @Autowired com.gas.ecommerce.modules.order.service.CheckoutSettlementService checkoutSettlement;
    @Autowired com.gas.ecommerce.modules.order.service.OrderSubmissionStore orderSubmissions;
    @Autowired com.gas.ecommerce.modules.order.service.CheckoutCompensationService checkoutCompensation;
    @Autowired com.gas.ecommerce.modules.order.service.OrderInventoryService checkoutInventory;
    @Autowired com.gas.ecommerce.modules.marketing.service.CheckoutMarketingService checkoutMarketing;
    @MockitoBean com.gas.ecommerce.modules.order.service.CheckoutPointsGateway checkoutPoints;
    @Autowired com.gas.ecommerce.modules.payment.service.PaymentService payments;
    @MockitoBean com.gas.ecommerce.modules.order.service.OrderLogisticsGateway orderLogistics;
    @MockitoBean OrderNoGenerator orderNumbers;
    @MockitoBean com.gas.ecommerce.common.utils.PaymentNoGenerator paymentNumbers;
    String bearer;
    String userBearer;
    String secondUserBearer;

    @BeforeEach void seed() {
        AtomicLong sequence = new AtomicLong();
        org.mockito.Mockito.when(orderNumbers.next()).thenAnswer(invocation -> "TEST" + sequence.incrementAndGet());
        db.update("INSERT INTO employee(id, username, password_hash, status) VALUES(7, 'operator', ?, 'active')",
                passwords.encode("a-strong-password"));
        db.update("INSERT INTO employee_role(employee_id, role_id) SELECT 7, id FROM role WHERE role_key='admin'");
        db.update("INSERT INTO `user`(id, status) VALUES(100, 'active'), (101, 'active')");
        bearer = "Bearer " + tokens.generateAccessToken(7L, "admin");
        userBearer = "Bearer " + tokens.generateAccessToken(100L, "user");
        secondUserBearer = "Bearer " + tokens.generateAccessToken(101L, "user");
    }

    @Test void loginChecksPasswordStatusAndRoles() throws Exception {
        var response = mvc.perform(post("/api/v1/admin/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", "operator", "password", "a-strong-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.admin.username").value("operator"))
                .andReturn();
        String token = json.readTree(response.getResponse().getContentAsString()).at("/data/accessToken").asText();
        mvc.perform(get("/api/v1/admin/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions[0]").exists());
        mvc.perform(post("/api/v1/admin/auth/login").contentType("application/json")
                        .content("{\"username\":\"operator\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        db.update("UPDATE employee SET status='inactive' WHERE id=7");
        mvc.perform(get("/api/v1/admin/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void paymentCreationCallbackAndOwnershipAreEnforced() throws Exception {
        org.mockito.Mockito.when(paymentNumbers.next()).thenReturn("PAY-TEST-1");
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount,expire_time) VALUES(500,'PAY-ORDER',100,'pending_payment',50,50,?)",java.time.LocalDateTime.now().plusMinutes(30));
        String create="{\"orderId\":500,\"channel\":\"test\"}";
        var result=mvc.perform(post("/api/v1/payments").header("Authorization",userBearer).contentType("application/json").content(create))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(50)).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
        mvc.perform(post("/api/v1/payments").header("Authorization",userBearer).contentType("application/json").content(create))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM payment",Integer.class));
        mvc.perform(get("/api/v1/payments").header("Authorization",userBearer).param("status","pending"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/payments").header("Authorization",secondUserBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/payments/"+id).header("Authorization",secondUserBearer)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/payments").header("Authorization",bearer).contentType("application/json").content(create)).andExpect(status().isForbidden());
        String body="{\"paymentNo\":\"PAY-TEST-1\",\"transactionNo\":\"TX-1\",\"amount\":50,\"status\":\"success\",\"eventTime\":\"2026-10-22 12:00:00\"}";
        mvc.perform(post("/api/v1/payment/callback/test").contentType("application/json").content(body)).andExpect(status().isForbidden());
        signedPaymentCallback(body.replace("\"amount\":50","\"amount\":49"),400);
        signedPaymentCallback(body,200);
        signedPaymentCallback(body,200);
        assertEquals("paid",db.queryForObject("SELECT status FROM `order` WHERE id=500",String.class));
        assertEquals("success",db.queryForObject("SELECT status FROM payment WHERE id=?",String.class,id));
        db.update("UPDATE payment SET status='refunded' WHERE id=?",id);
        signedPaymentCallback(body,200);
        assertEquals("refunded",db.queryForObject("SELECT status FROM payment WHERE id=?",String.class,id));
    }

    @Test void expiredCreationIsRejectedAndCancelledPaymentIsRefunded() throws Exception {
        org.mockito.Mockito.when(paymentNumbers.next()).thenReturn("PAY-TEST-2");
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount,expire_time) VALUES(501,'EXPIRED-ORDER',100,'pending_payment',50,50,?)",java.time.LocalDateTime.now().minusMinutes(1));
        mvc.perform(post("/api/v1/payments").header("Authorization",userBearer).contentType("application/json").content("{\"orderId\":501,\"channel\":\"test\"}"))
                .andExpect(status().isBadRequest());
        db.update("UPDATE `order` SET expire_time=? WHERE id=501",java.time.LocalDateTime.now().plusMinutes(30));
        mvc.perform(post("/api/v1/payments").header("Authorization",userBearer).contentType("application/json").content("{\"orderId\":501,\"channel\":\"test\"}"))
                .andExpect(status().isOk());
        db.update("UPDATE `order` SET status='cancelled' WHERE id=501");
        signedPaymentCallback("{\"paymentNo\":\"PAY-TEST-2\",\"transactionNo\":\"TX-2\",\"amount\":50,\"status\":\"success\",\"eventTime\":\"2026-10-22 12:00:00\"}",200);
        assertEquals("cancelled",db.queryForObject("SELECT status FROM `order` WHERE id=501",String.class));
    }

    @Test void failedPaymentCanRetryWithoutReusingProviderTransaction() throws Exception {
        org.mockito.Mockito.when(paymentNumbers.next()).thenReturn("PAY-FAIL-1","PAY-RETRY-2");
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount,expire_time) VALUES(502,'RETRY-ORDER',100,'pending_payment',50,50,?)",java.time.LocalDateTime.now().plusMinutes(30));
        var request=new com.gas.ecommerce.modules.payment.dto.PaymentCreateRequest(502L,"test");
        payments.create(100L,request);
        String body="{\"paymentNo\":\"PAY-FAIL-1\",\"transactionNo\":\"TX-FAIL\",\"amount\":50,\"status\":\"failed\",\"eventTime\":\"2026-10-22 12:00:00\"}";
        signedPaymentCallback(body,200);
        assertEquals("pending_payment",db.queryForObject("SELECT status FROM `order` WHERE id=502",String.class));
        payments.create(100L,request);
        String retry=body.replace("PAY-FAIL-1","PAY-RETRY-2").replace("\"failed\"","\"success\"");
        signedPaymentCallback(retry,400);
        assertEquals("pending_payment",db.queryForObject("SELECT status FROM `order` WHERE id=502",String.class));
        signedPaymentCallback(retry.replace("TX-FAIL","TX-RETRY"),200);
        signedPaymentCallback(body,200);
        assertEquals("paid",db.queryForObject("SELECT status FROM `order` WHERE id=502",String.class));
    }

    @Test void concurrentPaymentCreationReturnsOnePendingRecord() throws Exception {
        org.mockito.Mockito.when(paymentNumbers.next()).thenReturn("PAY-CONCURRENT");
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount,expire_time) VALUES(503,'CONCURRENT-PAY',100,'pending_payment',50,50,?)",java.time.LocalDateTime.now().plusMinutes(30));
        var request=new com.gas.ecommerce.modules.payment.dto.PaymentCreateRequest(503L,"test");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->payments.create(100L,request));
            var second=pool.submit(()->payments.create(100L,request));
            assertEquals(first.get(10,java.util.concurrent.TimeUnit.SECONDS).id(),second.get(10,java.util.concurrent.TimeUnit.SECONDS).id());
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM payment WHERE order_id=503",Integer.class));
    }

    private void signedPaymentCallback(String body,int expected) throws Exception {
        body=body.replace("2026-10-22 12:00:00",java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        String timestamp=Long.toString(java.time.Instant.now().getEpochSecond());
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-only-payment-callback-secret-2026".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=java.util.HexFormat.of().formatHex(mac.doFinal(("test\n"+timestamp+"\n"+body).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        mvc.perform(post("/api/v1/payment/callback/test").header("X-Payment-Timestamp",timestamp).header("X-Payment-Signature",signature)
                .contentType("application/json").content(body)).andExpect(status().is(expected));
    }

    @Test void operationManagementPersistsCouponsConfigAndHotwordsWithPermissions() throws Exception {
        String coupon = "{\"name\":\"Spring Sale\",\"amount\":10.00,\"minimumAmount\":50.00,\"startTime\":\"2026-01-01 00:00:00\",\"endTime\":\"2026-12-31 23:59:59\",\"quantity\":100,\"status\":\"active\"}";
        mvc.perform(post("/api/v1/admin/coupons").header("Authorization", bearer).contentType("application/json").content(coupon))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Spring Sale"));
        mvc.perform(get("/api/v1/admin/coupons").header("Authorization", bearer).param("keyword", "Spring"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(put("/api/v1/admin/config").header("Authorization", bearer).contentType("application/json")
                        .content("{\"configKey\":\"shop.notice\",\"configValue\":\"Welcome\",\"description\":\"Store notice\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/config").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].configKey").value("shop.notice"));
        mvc.perform(post("/api/v1/admin/hotwords").header("Authorization", bearer).contentType("application/json")
                        .content("{\"word\":\"gas stove\",\"searchCount\":12,\"status\":\"active\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/hotwords").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].word").value("gas stove"));
        mvc.perform(get("/api/v1/admin/logs").header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/coupons").header("Authorization", userBearer).contentType("application/json").content(coupon))
                .andExpect(status().isForbidden());
    }

    @Test void revokedRolesAndPermissionsAreEffectiveImmediately() throws Exception {
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='product:write')");
        mvc.perform(post("/api/v1/admin/products").header("Authorization", bearer).contentType("application/json")
                        .content("{\"categoryId\":1,\"name\":\"stove\",\"price\":100}"))
                .andExpect(status().isForbidden());
        db.update("DELETE FROM employee_role WHERE employee_id=7");
        mvc.perform(get("/api/v1/admin/products").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/auth/me").header("Authorization", "Bearer " + tokens.generateRefreshToken(7L)))
                .andExpect(status().isUnauthorized());
    }

    @Test void productEditorOptionsUseProductReadPermissionAndPreserveIds() throws Exception {
        long category=category(0,"Editor category");
        db.update("INSERT INTO brand(id,name,status,is_deleted) VALUES(9007199254740993,'Editor brand','active',0)");
        mvc.perform(get("/api/v1/admin/products/options").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.categories[0].id").value(String.valueOf(category)))
                .andExpect(jsonPath("$.data.brands[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.data.brands[0].name").value("Editor brand"));
        mvc.perform(get("/api/v1/admin/products/options")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/products/options").header("Authorization",userBearer)).andExpect(status().isForbidden());
    }

    @Test void productImagesRejectUnsafeUrlsAndStatusFiltersReflectChanges() throws Exception {
        long category=category(0,"Product validation");
        mvc.perform(post("/api/v1/admin/products").header("Authorization",bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("categoryId",category,"name","Unsafe cover","price",10,"coverUrl","javascript:alert(1)"))))
                .andExpect(status().isBadRequest());
        long product=product(category);
        mvc.perform(get("/api/v1/admin/products").header("Authorization",bearer).param("status","draft").param("categoryId",String.valueOf(category)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        setProductStatus(product,"on_sale",200);
        mvc.perform(get("/api/v1/admin/products").header("Authorization",bearer).param("status","draft"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        setProductStatus(product,"off_sale",200);
        mvc.perform(get("/api/v1/admin/products").header("Authorization",bearer).param("status","off_sale"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].id").value(String.valueOf(product)));
        mvc.perform(put("/api/v1/admin/products/"+product).header("Authorization",bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("categoryId",category,"name","Unsafe update","price",10,"coverUrl","//untrusted.example/a.png"))))
                .andExpect(status().isBadRequest());
    }

    @Test void catalogLifecyclePersistsDataAndSupportsPagination() throws Exception {
        long category = category(0, "Appliances");
        long product = product(category);
        mvc.perform(get("/api/v1/admin/products").param("keyword", "stove").param("page", "1").param("size", "1")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].category").value("Appliances"))
                .andExpect(jsonPath("$.data.list[0].stock").value(0));
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,10,3)", product);
        mvc.perform(get("/api/v1/admin/products/" + product).header("Authorization", bearer))
                .andExpect(jsonPath("$.data.stock").value(7));
        mvc.perform(put("/api/v1/admin/products/" + product).header("Authorization", bearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("categoryId", category, "name", "New stove", "price", 99,
                                "description", "Reliable stove details", "detailImages", java.util.List.of("https://img.example/detail-1.jpg")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("New stove"))
                .andExpect(jsonPath("$.data.description").value("Reliable stove details"))
                .andExpect(jsonPath("$.data.detailImages[0]").value("https://img.example/detail-1.jpg"))
                .andExpect(jsonPath("$.data.originalPrice").doesNotExist());
        setProductStatus(product, "on_sale", 200);
        mvc.perform(get("/api/v1/products/" + product))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.description").value("Reliable stove details"))
                .andExpect(jsonPath("$.data.detailImages[0]").value("https://img.example/detail-1.jpg"));
        mvc.perform(get("/api/v1/products").param("keyword", "New stove"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].description").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].detailImages.length()").value(0));
        mvc.perform(delete("/api/v1/admin/products/" + product).header("Authorization", bearer)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/categories/" + category).header("Authorization", bearer)).andExpect(status().isBadRequest());
        setProductStatus(product, "off_sale", 200);
        mvc.perform(delete("/api/v1/admin/products/" + product).header("Authorization", bearer)).andExpect(status().isBadRequest());
        db.update("UPDATE inventory SET quantity=0,locked_quantity=0 WHERE product_id=?",product);
        mvc.perform(delete("/api/v1/admin/products/" + product).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/categories/" + category).header("Authorization", bearer)).andExpect(status().isOk());
        assertEquals(1, db.queryForObject("SELECT is_deleted FROM product WHERE id=?", Integer.class, product));
    }

    @Test void categoryHierarchyRejectsCyclesDuplicatesAndInvalidDeactivation() throws Exception {
        long root = category(0, "Root"); long child = category(root, "Child");
        mvc.perform(put("/api/v1/admin/categories/" + root).header("Authorization", bearer).contentType("application/json")
                        .content(categoryBody(child, "Root", "active"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", bearer).contentType("application/json")
                        .content(categoryBody(root, "Child", "active"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/categories/" + root).header("Authorization", bearer)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/categories/" + root).header("Authorization", bearer).contentType("application/json")
                        .content(categoryBody(0, "Root", "inactive"))).andExpect(status().isBadRequest());
        long product = product(child); setProductStatus(product, "on_sale", 200);
        mvc.perform(put("/api/v1/admin/categories/" + child).header("Authorization", bearer).contentType("application/json")
                        .content(categoryBody(root, "Child", "inactive"))).andExpect(status().isBadRequest());
    }

    @Test void categoryManagementCountsOnlyDirectUndeletedReferences() throws Exception {
        long root = category(0, "Management root"), child = category(root, "Management child");
        product(root); product(child);
        long deletedProduct = product(root);
        db.update("UPDATE product SET is_deleted=1 WHERE id=?", deletedProduct);
        long deletedChild = category(root, "Deleted child");
        mvc.perform(delete("/api/v1/admin/categories/" + deletedChild).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/categories/management").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(root)))
                .andExpect(jsonPath("$.data[0].childCount").value(1))
                .andExpect(jsonPath("$.data[0].productCount").value(1))
                .andExpect(jsonPath("$.data[1].childCount").value(0))
                .andExpect(jsonPath("$.data[1].productCount").value(1));
    }

    @Test void categoryStatusPreservesMetadataAndEnforcesHierarchyAndPublicProductGuards() throws Exception {
        long root = category(0, "Status root"), child = category(root, "Status child");
        String url = "/api/v1/admin/categories/" + child + "/status";
        mvc.perform(put("/api/v1/admin/categories/" + child).header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Sorted child", "parentId", root, "sort", 17, "status", "active"))))
                .andExpect(status().isOk());
        long p = product(child); setProductStatus(p, "on_sale", 200);
        mvc.perform(put(url).header("Authorization", bearer).contentType("application/json").content("{\"status\":\"inactive\"}"))
                .andExpect(status().isBadRequest());
        setProductStatus(p, "off_sale", 200);
        mvc.perform(put(url).header("Authorization", bearer).contentType("application/json").content("{\"status\":\"inactive\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Sorted child"))
                .andExpect(jsonPath("$.data.parentId").value(String.valueOf(root))).andExpect(jsonPath("$.data.sort").value(17));
        mvc.perform(put("/api/v1/admin/categories/" + root + "/status").header("Authorization", bearer).contentType("application/json").content("{\"status\":\"inactive\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories/tree")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(put(url).header("Authorization", bearer).contentType("application/json").content("{\"status\":\"active\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/categories/" + root + "/status").header("Authorization", bearer).contentType("application/json").content("{\"status\":\"active\"}"))
                .andExpect(status().isOk());
        mvc.perform(put(url).header("Authorization", bearer).contentType("application/json").content("{\"status\":\"active\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories/tree")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].children[0].name").value("Sorted child"));
        mvc.perform(delete("/api/v1/admin/categories/" + child).header("Authorization", bearer)).andExpect(status().isBadRequest());
    }

    @Test void categoryMoveSortValidationAndDeletionRemainConsistent() throws Exception {
        long root = category(0, "First root"), other = category(0, "Other root"), child = category(root, "Moving child");
        mvc.perform(put("/api/v1/admin/categories/" + child).header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", " Renamed ", "parentId", other, "sort", 9, "status", "active"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Renamed"));
        mvc.perform(get("/api/v1/categories/tree")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].children.length()").value(0))
                .andExpect(jsonPath("$.data[1].children[0].id").value(String.valueOf(child)));
        mvc.perform(put("/api/v1/admin/categories/" + other).header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Other root", "parentId", 0, "sort", 20, "status", "active"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/categories/management").header("Authorization", bearer))
                .andExpect(jsonPath("$.data[1].id").value(String.valueOf(child))).andExpect(jsonPath("$.data[1].sort").value(9));
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", bearer).contentType("application/json")
                .content(categoryBody(999999, "Missing parent", "active"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", bearer).contentType("application/json")
                .content("{\"name\":\"Bad sort\",\"parentId\":0,\"sort\":-1,\"status\":\"active\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/categories/" + child + "/status").header("Authorization", bearer).contentType("application/json")
                .content("{\"status\":\"on_sale\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/categories/" + child).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/categories/" + child).header("Authorization", bearer)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/categories/management").header("Authorization", bearer)).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test void categoryManagementRequiresSeparateReadAndWritePermissions() throws Exception {
        long id = category(0, "Permissions");
        mvc.perform(get("/api/v1/admin/categories/management")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/categories/management").header("Authorization", userBearer)).andExpect(status().isForbidden());
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='category:write')");
        mvc.perform(get("/api/v1/admin/categories/management").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/categories/" + id + "/status").header("Authorization", bearer).contentType("application/json")
                .content("{\"status\":\"inactive\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/categories/" + id).header("Authorization", bearer)).andExpect(status().isForbidden());
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='category:read')");
        mvc.perform(get("/api/v1/admin/categories/management").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test void invalidInputReturns400AndInactiveCategoryCannotPublish() throws Exception {
        long category = id(post("/api/v1/admin/categories"), categoryBody(0, "Inactive", "inactive"));
        long product = product(category); setProductStatus(product, "on_sale", 400);
        mvc.perform(post("/api/v1/admin/products").header("Authorization", bearer).contentType("application/json")
                        .content("{\"categoryId\":1,\"name\":\"bad\",\"price\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/products").param("size", "101").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/products").param("sort", "price;DROP TABLE product").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/products").header("Authorization", bearer).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test void bootstrapCreatesHashedAccountAndDoesNotResetExistingPassword() throws Exception {
        var bootstrap = new AdminBootstrap(employees, roles, access, passwords, "first-admin", "bootstrap-pass-2026");
        bootstrap.run(null); bootstrap.run(null);
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM employee WHERE username='first-admin'", Integer.class));
        String hash = db.queryForObject("SELECT password_hash FROM employee WHERE username='first-admin'", String.class);
        assertTrue(passwords.matches("bootstrap-pass-2026", hash));
        new AdminBootstrap(employees, roles, access, passwords, "first-admin", "other-password-2026").run(null);
        assertEquals(hash, db.queryForObject("SELECT password_hash FROM employee WHERE username='first-admin'", String.class));
    }

    @Test void publicCatalogShowsOnlyOnSaleProductsInActiveCategories() throws Exception {
        long root = category(0, "Home");
        long child = category(root, "Cookers");
        long visible = product(child);
        long draft = product(child);
        setProductStatus(visible, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,8,2)", visible);

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(root)).param("keyword", "stove"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(visible))
                .andExpect(jsonPath("$.data.list[0].categoryId").value(child))
                .andExpect(jsonPath("$.data.list[0].availableStock").value(6));
        mvc.perform(get("/api/v1/products/" + visible))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("stove"));
        mvc.perform(get("/api/v1/products/" + draft)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404001));
        setProductStatus(visible, "off_sale", 200);
        mvc.perform(get("/api/v1/products/" + visible)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products")).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test void publicCatalogPaginatesSortsAndHidesInactiveHierarchy() throws Exception {
        long root = category(0, "Home");
        long child = category(root, "Cookers");
        long cheap = product(child);
        long expensive = product(child);
        db.update("UPDATE product SET name='Cheap stove', price=80 WHERE id=?", cheap);
        db.update("UPDATE product SET name='Expensive stove', price=200, original_price=250 WHERE id=?", expensive);
        setProductStatus(cheap, "on_sale", 200);
        setProductStatus(expensive, "on_sale", 200);

        mvc.perform(get("/api/v1/products").param("sort", "price").param("order", "asc")
                        .param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.list[0].id").value(expensive));
        mvc.perform(get("/api/v1/products").param("categoryId", "99999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));

        db.update("UPDATE category SET status='inactive' WHERE id=?", root);
        mvc.perform(get("/api/v1/categories")).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/products")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/products/" + cheap)).andExpect(status().isNotFound());
    }

    @Test void publicCatalogRejectsInvalidQueriesAndDoesNotExposeWrites() throws Exception {
        mvc.perform(get("/api/v1/products").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("sort", "price;DROP TABLE product"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("categoryId", "abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/products").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/products")).andExpect(status().isUnauthorized());
    }

    @Test void addressesEncryptPhonesAndMaintainOneDefaultAddress() throws Exception {
        long first = createAddress(userBearer, "13800138000", false);
        assertEquals(1, db.queryForObject("SELECT is_default FROM user_address WHERE id=?", Integer.class, first));
        long second = createAddress(userBearer, "13900139000", true);
        assertEquals(0, db.queryForObject("SELECT is_default FROM user_address WHERE id=?", Integer.class, first));
        assertEquals(1, db.queryForObject("SELECT is_default FROM user_address WHERE id=?", Integer.class, second));
        String encrypted = db.queryForObject("SELECT phone FROM user_address WHERE id=?", String.class, second);
        assertNotEquals("13900139000", encrypted);
        assertEquals("9000", db.queryForObject("SELECT phone_last4 FROM user_address WHERE id=?", String.class, second));
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(second)).andExpect(jsonPath("$.data[0].phone").value("13900139000"));

        mvc.perform(put("/api/v1/users/me/addresses/" + second).header("Authorization", userBearer)
                        .contentType("application/json").content(addressBody("13900139000", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isDefault").value(false));
        assertEquals(1, db.queryForObject("SELECT is_default FROM user_address WHERE id=?", Integer.class, first));
        mvc.perform(delete("/api/v1/users/me/addresses/" + first).header("Authorization", userBearer))
                .andExpect(status().isOk());
        assertEquals(1, db.queryForObject("SELECT is_default FROM user_address WHERE id=?", Integer.class, second));
        mvc.perform(put("/api/v1/users/me/addresses/" + second + "/default").header("Authorization", userBearer))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/users/me/addresses/" + second).header("Authorization", userBearer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", userBearer))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test void addressOperationsArePrivateAndValidateOwnerAndPayload() throws Exception {
        long id = createAddress(secondUserBearer, "13700137000", true);
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(put("/api/v1/users/me/addresses/" + id + "/default").header("Authorization", userBearer))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/users/me/addresses/" + id).header("Authorization", userBearer))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users/me/addresses").header("Authorization", userBearer)
                        .contentType("application/json").content(addressBody("123", false)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/me/addresses")).andExpect(status().isUnauthorized());
    }

    @Test void cartMergesItemsTracksSelectionAndRestoresDeletedRows() throws Exception {
        long category = category(0, "Cart category");
        long product = product(category);
        setProductStatus(product, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,8,2)", product);

        mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", product, "quantity", 2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].availableStock").value(6))
                .andExpect(jsonPath("$.data.selectedAmount").value(200));
        var response = mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", product, "quantity", 3))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5))
                .andExpect(jsonPath("$.data.totalQuantity").value(5))
                .andExpect(jsonPath("$.data.selectedAmount").value(500)).andReturn();
        long itemId = json.readTree(response.getResponse().getContentAsString()).at("/data/items/0/id").asLong();

        mvc.perform(put("/api/v1/cart/items/" + itemId + "/selection").header("Authorization", userBearer)
                        .contentType("application/json").content("{\"selected\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.selectedQuantity").value(0))
                .andExpect(jsonPath("$.data.selectedAmount").value(0));
        mvc.perform(put("/api/v1/cart/items/" + itemId).header("Authorization", userBearer)
                        .contentType("application/json").content("{\"quantity\":7}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/cart/items/" + itemId).header("Authorization", userBearer))
                .andExpect(status().isOk());
        assertEquals(1, db.queryForObject("SELECT is_deleted FROM cart_item WHERE id=?", Integer.class, itemId));
        mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", product, "quantity", 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(1));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM cart_item WHERE user_id=100 AND product_id=? AND is_deleted=0",
                Integer.class, product));
    }

    @Test void cartIsPrivateAndRejectsInvalidOrUnavailableProducts() throws Exception {
        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content("{\"productId\":-1,\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        long category = category(0, "Private cart category");
        long product = product(category);
        setProductStatus(product, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,1,0)", product);
        var response = mvc.perform(post("/api/v1/cart/items").header("Authorization", secondUserBearer)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", product, "quantity", 1))))
                .andExpect(status().isOk()).andReturn();
        long itemId = json.readTree(response.getResponse().getContentAsString()).at("/data/items/0/id").asLong();
        mvc.perform(get("/api/v1/cart").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(delete("/api/v1/cart/items/" + itemId).header("Authorization", userBearer))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/cart/items/" + itemId).header("Authorization", userBearer)
                        .contentType("application/json").content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/cart/items/" + itemId + "/selection").header("Authorization", userBearer)
                        .contentType("application/json").content("{\"selected\":false}"))
                .andExpect(status().isBadRequest());
        // Carts do not reserve stock: another user can still add this product.
        mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", product, "quantity", 1))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", product, "quantity", 1))))
                .andExpect(status().isBadRequest());
        setProductStatus(product, "off_sale", 200);
        mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", product, "quantity", 1))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/cart").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.invalidItems[0].available").value(false))
                .andExpect(jsonPath("$.data.selectedAmount").value(0));
        db.update("INSERT INTO " + "\u0060user\u0060" + "(id,status) VALUES(7,'active')");
        mvc.perform(get("/api/v1/cart").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test void cartBulkOperationsRespectOwnershipAndUseCurrentPriceAndStock() throws Exception {
        long category = category(0, "Bulk cart");
        long product = product(category);
        long otherProduct = product(category);
        setProductStatus(product, "on_sale", 200);
        setProductStatus(otherProduct, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,10,1),(?,10,0)",
                product, otherProduct);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,2,1),(100,?,1,0),(101,?,1,1)",
                product, otherProduct, product);
        long itemId = db.queryForObject("SELECT id FROM cart_item WHERE user_id=100 AND product_id=?", Long.class, product);
        mvc.perform(put("/api/v1/cart/items/" + itemId).header("Authorization", userBearer)
                        .contentType("application/json").content("{\"quantity\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.selectedAmount").value(300));
        db.update("UPDATE product SET price=80 WHERE id=?", product);
        mvc.perform(put("/api/v1/cart/selection").header("Authorization", userBearer)
                        .contentType("application/json").content("{\"selected\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.selectedQuantity").value(4))
                .andExpect(jsonPath("$.data.selectedAmount").value(340));
        db.update("UPDATE inventory SET locked_quantity=8 WHERE product_id=?", product);
        mvc.perform(get("/api/v1/cart").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.selectedQuantity").value(1))
                .andExpect(jsonPath("$.data.selectedAmount").value(100));
        mvc.perform(put("/api/v1/cart/items/" + itemId + "/selection").header("Authorization", userBearer)
                        .contentType("application/json").content("{\"selected\":false}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/cart/selected").header("Authorization", userBearer)).andExpect(status().isOk());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM cart_item WHERE user_id=100 AND is_deleted=0", Integer.class));
        mvc.perform(delete("/api/v1/cart").header("Authorization", userBearer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cart").header("Authorization", userBearer))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM cart_item WHERE user_id=101 AND is_deleted=0", Integer.class));
    }

    @Test void cartRejectsAccumulatedLimitHiddenCategoryAndInactiveUser() throws Exception {
        long category = category(0, "Cart limits");
        long product = product(category);
        setProductStatus(product, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,2000,0)", product);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,999,1)", product);
        mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", product, "quantity", 1))))
                .andExpect(status().isBadRequest());
        db.update("UPDATE category SET status='inactive' WHERE id=?", category);
        mvc.perform(post("/api/v1/cart/items").header("Authorization", secondUserBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", product, "quantity", 1))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/cart").header("Authorization", userBearer))
                .andExpect(jsonPath("$.data.invalidItems[0].available").value(false))
                .andExpect(jsonPath("$.data.selectedAmount").value(0));
        mvc.perform(put("/api/v1/cart/selection").header("Authorization", userBearer)
                        .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        db.update("UPDATE " + "\u0060user\u0060" + " SET status='inactive' WHERE id=100");
        mvc.perform(get("/api/v1/cart").header("Authorization", userBearer)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/cart").header("Authorization", userBearer)).andExpect(status().isBadRequest());
    }

    @Test void cartPaginatesMarksPriceChangesSeparatesInvalidItemsAndDeletesByIds() throws Exception {
        long category = category(0, "Cart page");
        long first = product(category), second = product(category);
        setProductStatus(first, "on_sale", 200);
        setProductStatus(second, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,10,0),(?,10,0)", first, second);
        mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", first, "quantity", 2))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("productId", second, "quantity", 1))))
                .andExpect(status().isOk());
        db.update("UPDATE cart_item SET price_snapshot=100 WHERE user_id=100 AND product_id=?", first);
        db.update("UPDATE product SET price=75 WHERE id=?", first);
        long firstItem = db.queryForObject("SELECT id FROM cart_item WHERE user_id=100 AND product_id=?", Long.class, first);
        long secondItem = db.queryForObject("SELECT id FROM cart_item WHERE user_id=100 AND product_id=?", Long.class, second);
        mvc.perform(get("/api/v1/cart?page=1&size=1").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.selectedQuantity").value(3))
                .andExpect(jsonPath("$.data.selectedAmount").value(250));
        mvc.perform(get("/api/v1/cart?page=2&size=1").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].priceChanged").value(true));
        mvc.perform(delete("/api/v1/cart/items").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("ids", java.util.List.of(firstItem, secondItem)))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/cart").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalQuantity").value(0));
    }

    @Test void failedSubmissionIsImmediatelyCompensatableWithCoarseDatabaseTimestamps() {
        // Reproduce a DATETIME(0) backend: rounding must not defer an immediate release into the future.
        db.execute("ALTER TABLE order_submission MODIFY COLUMN lease_until DATETIME(0)");
        db.execute("ALTER TABLE order_submission MODIFY COLUMN next_attempt_time DATETIME(0)");
        for (int i=0;i<8;i++) {
            var request=new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(1L,null,"precision-"+i);
            var claim=orderSubmissions.claim(100L,request);
            assertTrue(orderSubmissions.fail(claim,new IllegalStateException("failed before resources")));
            checkoutCompensation.compensate(claim.orderNo());
            assertEquals("FAILED",db.queryForObject("SELECT phase FROM order_submission WHERE order_no=?",String.class,claim.orderNo()));
        }
    }

    @Test void checkoutFreightFullReductionAndCouponAreCalculatedByOwnerServices() throws Exception {
        long c=category(0,"Freight checkout"), product=product(c);
        setProductStatus(product,"on_sale",200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0)",product);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1)",product);
        long address=createAddress(userBearer,"13800138000",true), owned=claimCoupon(coupon(2));
        db.update("INSERT INTO checkout_delivery_rule(province,city,district,reachable,freight_amount) VALUES('*','*','*',FALSE,7.50)");
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->orders.preview(100L,new com.gas.ecommerce.modules.order.dto.OrderPreviewRequest(address,owned)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_checkout_ticket",Integer.class));
        db.update("UPDATE checkout_delivery_rule SET reachable=TRUE");
        db.update("INSERT INTO checkout_full_reduction(minimum_amount,discount_amount,start_time,end_time) VALUES(100,10,?,?)",java.time.LocalDateTime.now().minusDays(1),java.time.LocalDateTime.now().plusDays(1));
        var quote=orders.preview(100L,new com.gas.ecommerce.modules.order.dto.OrderPreviewRequest(address,owned));
        assertEquals(0,new java.math.BigDecimal("77.50").compareTo(quote.payAmount()));
        assertEquals(0,new java.math.BigDecimal("10").compareTo(quote.fullReductionAmount()));
        assertNotEquals(quote.checkoutToken(),db.queryForObject("SELECT token_hash FROM order_checkout_ticket",String.class));
        var request=new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,null,owned,quote.orderNo(),quote.checkoutToken());
        var order=orders.create(100L,request);
        assertEquals(0,quote.payAmount().compareTo(order.payAmount()));
        assertEquals(0,quote.freightAmount().compareTo(order.freightAmount()));
        assertEquals("locked",couponState(owned));
        assertEquals("SUCCEEDED",db.queryForObject("SELECT phase FROM order_submission",String.class));
        checkoutCompensation.compensate(order.orderNo());
        assertEquals(1,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
    }

    @Test void checkoutTokenSupportsClickRetriesAndRejectsDifferentPayloadOrHeader() throws Exception {
        long c=category(0,"Token checkout"), product=product(c);
        setProductStatus(product,"on_sale",200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0)",product);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1)",product);
        long address=createAddress(userBearer,"13800138000",true);
        var quote=orders.preview(100L,new com.gas.ecommerce.modules.order.dto.OrderPreviewRequest(address,null));
        String body=json.writeValueAsString(Map.of("addressId",address,"orderNo",quote.orderNo(),"checkoutToken",quote.checkoutToken(),"payAmount",0.01));
        mvc.perform(post("/api/v1/orders").header("Authorization",userBearer).header("Idempotency-Key","wrong").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        for (int i=0;i<3;i++) mvc.perform(post("/api/v1/orders").header("Authorization",userBearer).header("Idempotency-Key",quote.checkoutToken()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderNo").value(quote.orderNo())).andExpect(jsonPath("$.data.payAmount").value(100));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM `order`",Integer.class));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM order_outbox",Integer.class));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->orders.create(100L,
                new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,"different remark",null,null,quote.orderNo(),quote.checkoutToken())));
    }

    @Test void expiredSubmissionIsCompensatedAfterCrashAndLateReservationsAreFenced() throws Exception {
        long c=category(0,"Crash recovery"), product=product(c);
        setProductStatus(product,"on_sale",200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0)",product);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1)",product);
        long address=createAddress(userBearer,"13800138000",true), owned=claimCoupon(coupon(2));
        var preview=orders.preview(100L,new com.gas.ecommerce.modules.order.dto.OrderPreviewRequest(address,owned));
        var request=new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,null,owned,preview.orderNo(),preview.checkoutToken());
        var claim=orderSubmissions.claim(100L,request);
        var quote=checkoutSettlement.settle(100L,address,owned);
        checkoutInventory.lockCheckout(claim.orderNo(),quote.items());
        checkoutInventory.lockCheckout(claim.orderNo(),quote.items()); // Lost reservation reply, same command retried.
        checkoutMarketing.reserve(claim.orderNo(),100L,owned,quote.total(),quote.marketing());
        checkoutMarketing.reserve(claim.orderNo(),100L,owned,quote.total(),quote.marketing());
        assertEquals(1,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        db.update("UPDATE order_submission SET lease_until=?,next_attempt_time=?",java.time.LocalDateTime.now().minusMinutes(1),java.time.LocalDateTime.now().minusMinutes(1));
        checkoutCompensation.recover(); checkoutCompensation.recover();
        assertEquals("FAILED",db.queryForObject("SELECT phase FROM order_submission",String.class));
        assertEquals("unused",couponState(owned));
        assertEquals(0,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM `order`",Integer.class));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->checkoutInventory.lockCheckout(claim.orderNo(),quote.items()));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->checkoutMarketing.reserve(claim.orderNo(),100L,owned,quote.total(),quote.marketing()));
        assertThrows(com.gas.ecommerce.modules.order.service.CheckoutConflictException.class,()->orders.create(100L,request));
    }

    @Test void uncertainPointsOutcomeAndFailedReleaseAreRetriedWithoutLeakingOtherResources() throws Exception {
        long c=category(0,"Compensation retry"), product=product(c);
        setProductStatus(product,"on_sale",200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0)",product);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1)",product);
        long address=createAddress(userBearer,"13800138000",true), owned=claimCoupon(coupon(2));
        var preview=orders.preview(100L,new com.gas.ecommerce.modules.order.dto.OrderPreviewRequest(address,owned));
        var request=new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,null,owned,preview.orderNo(),preview.checkoutToken());
        org.mockito.Mockito.doThrow(new IllegalStateException("reply lost")).when(checkoutPoints).reserve(preview.orderNo(),100L);
        org.mockito.Mockito.doThrow(new IllegalStateException("points offline")).when(checkoutPoints).release(preview.orderNo());
        assertThrows(IllegalStateException.class,()->orders.create(100L,request));
        assertEquals("COMPENSATING",db.queryForObject("SELECT phase FROM order_submission",String.class));
        assertEquals("unused",couponState(owned));
        assertEquals(0,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        org.mockito.Mockito.doNothing().when(checkoutPoints).release(preview.orderNo());
        db.update("UPDATE order_submission SET lease_until=?,next_attempt_time=?",java.time.LocalDateTime.now().minusMinutes(1),java.time.LocalDateTime.now().minusMinutes(1));
        checkoutCompensation.recover();
        assertEquals("FAILED",db.queryForObject("SELECT phase FROM order_submission",String.class));
        org.mockito.Mockito.verify(checkoutPoints,org.mockito.Mockito.times(2)).release(preview.orderNo());
    }

    @Test void checkoutPreviewHasNoResourceSideEffectsAndAllocatesCoupon() throws Exception {
        long c=category(0,"Preview"), first=product(c), second=product(c);
        setProductStatus(first,"on_sale",200); setProductStatus(second,"on_sale",200);
        db.update("UPDATE product SET price=33.33 WHERE id=?",first);
        db.update("UPDATE product SET price=66.67 WHERE id=?",second);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,3,0),(?,3,0)",first,second);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1),(100,?,1,1)",first,second);
        long address=createAddress(userBearer,"13800138000",true), owned=claimCoupon(coupon(2));
        var result=mvc.perform(post("/api/v1/orders/preview").header("Authorization",userBearer)
                .contentType("application/json").content(json.writeValueAsString(Map.of("addressId",address,"userCouponId",owned,"payAmount",0.01))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(100))
                .andExpect(jsonPath("$.data.discountAmount").value(20)).andExpect(jsonPath("$.data.payAmount").value(80))
                .andExpect(jsonPath("$.data.items[0].discountAmount").value(6.66))
                .andExpect(jsonPath("$.data.items[1].discountAmount").value(13.34)).andReturn();
        String number=json.readTree(result.getResponse().getContentAsString()).at("/data/orderNo").asText();
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM `order`",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_outbox",Integer.class));
        assertEquals(0,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals("unused",couponState(owned));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM cart_item WHERE is_deleted=0",Integer.class));
        var order=orders.create(100L,new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,null,owned,number,json.readTree(result.getResponse().getContentAsString()).at("/data/checkoutToken").asText()));
        assertEquals(number,order.orderNo());
        assertEquals(0,order.payAmount().compareTo(new java.math.BigDecimal("80")));
        assertEquals(0,order.items().stream().map(i->i.payAmount()).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).compareTo(order.payAmount()));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM order_outbox",Integer.class));
    }

    @Test void previewOrderNumberIsOwnedExpiresAndRejectsChangedPrices() throws Exception {
        long c=category(0,"Preview validation"), product=product(c);
        setProductStatus(product,"on_sale",200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0)",product);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1),(101,?,1,1)",product,product);
        long address=createAddress(userBearer,"13800138000",true), otherAddress=createAddress(secondUserBearer,"13800138001",true);
        var quote=orders.preview(100L,new com.gas.ecommerce.modules.order.dto.OrderPreviewRequest(address,null));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->orders.create(101L,
                new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(otherAddress,null,null,null,quote.orderNo(),quote.checkoutToken())));
        db.update("UPDATE product SET price=101 WHERE id=?",product);
        var request=new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,null,null,quote.orderNo(),quote.checkoutToken());
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->orders.create(100L,request));
        db.update("UPDATE product SET price=100 WHERE id=?",product);
        db.update("UPDATE order_checkout_ticket SET expire_time=? WHERE order_no=?",java.time.LocalDateTime.now().minusMinutes(1),quote.orderNo());
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->orders.create(100L,request));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM `order`",Integer.class));
        mvc.perform(post("/api/v1/orders").header("Authorization",userBearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("addressId",address)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders/preview").contentType("application/json").content(json.writeValueAsString(Map.of("addressId",address))))
                .andExpect(status().isUnauthorized());
    }

    @Test void concurrentPreviewSubmissionCreatesOneOrderAndRetrySurvivesExpiry() throws Exception {
        long c=category(0,"Concurrent checkout"), product=product(c);
        setProductStatus(product,"on_sale",200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0)",product);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,2,1)",product);
        long address=createAddress(userBearer,"13800138000",true);
        var quote=orders.preview(100L,new com.gas.ecommerce.modules.order.dto.OrderPreviewRequest(address,null));
        var request=new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,null,null,quote.orderNo(),quote.checkoutToken());
        try (var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> submit=()->{start.await();return orders.create(100L,request).id();};
            var first=executor.submit(submit); var second=executor.submit(submit); start.countDown();
            assertEquals(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM `order`",Integer.class));
        assertEquals(2,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM order_outbox",Integer.class));
        db.update("UPDATE order_checkout_ticket SET expire_time=?",java.time.LocalDateTime.now().minusDays(1));
        assertEquals(quote.orderNo(),orders.create(100L,request).orderNo());
    }

    @Test void failureWritingOutboxRollsBackOrderCouponStockAndCartThenAllowsRetry() throws Exception {
        long c=category(0,"Checkout rollback"), product=product(c);
        setProductStatus(product,"on_sale",200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0)",product);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1)",product);
        long address=createAddress(userBearer,"13800138000",true), owned=claimCoupon(coupon(2));
        var quote=orders.preview(100L,new com.gas.ecommerce.modules.order.dto.OrderPreviewRequest(address,owned));
        var request=new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,null,owned,quote.orderNo(),quote.checkoutToken());
        db.update("INSERT INTO order_outbox(order_no,event_type,status,attempts,next_attempt_time) VALUES(?,'order.created','pending',0,CURRENT_TIMESTAMP)",quote.orderNo());
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->orders.create(100L,request));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM `order`",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_item",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_stock_reservation WHERE status='reserved'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals("unused",couponState(owned));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM cart_item WHERE is_deleted=0",Integer.class));
        db.update("DELETE FROM order_outbox");
        var retry=orders.preview(100L,new com.gas.ecommerce.modules.order.dto.OrderPreviewRequest(address,owned));
        assertEquals(retry.orderNo(),orders.create(100L,new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,null,owned,retry.orderNo(),retry.checkoutToken())).orderNo());
    }

    @Test void orderCreationSnapshotsDataReservesStockAndIsIdempotent() throws Exception {
        long category = category(0, "Order category");
        long product = product(category);
        setProductStatus(product, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0),(?,4,0)", product, product);
        long address = createAddress(userBearer, "13800138000", true);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,7,1)", product);
        String body = json.writeValueAsString(Map.of("addressId", address, "requestId", "create-1", "remark", "Please call"));
        var response = mvc.perform(post("/api/v1/orders").header("Authorization", userBearer)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("pending_payment"))
                .andExpect(jsonPath("$.data.totalAmount").value(700))
                .andExpect(jsonPath("$.data.items[0].quantity").value(7))
                .andExpect(jsonPath("$.data.address.phone").value("13800138000")).andReturn();
        long orderId = json.readTree(response.getResponse().getContentAsString()).at("/data/id").asLong();
        assertEquals(7, db.queryForObject("SELECT SUM(locked_quantity) FROM inventory WHERE product_id=?", Integer.class, product));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM order_stock_reservation WHERE order_id=?", Integer.class, orderId));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM cart_item WHERE user_id=100 AND is_deleted=0", Integer.class));
        assertFalse(db.queryForObject("SELECT address_snapshot FROM `order` WHERE id=?", String.class, orderId)
                .contains("13800138000"));
        db.update("UPDATE product SET price=80, name='Changed' WHERE id=?", product);
        mvc.perform(post("/api/v1/orders").header("Authorization", userBearer)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.items[0].productName").value("stove"))
                .andExpect(jsonPath("$.data.totalAmount").value(700));
        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.address.phone").value("13800138000"));
    }

    @Test void orderCancellationReleasesStockAndPreventsCrossUserAccess() throws Exception {
        long category = category(0, "Cancel category");
        long product = product(category);
        setProductStatus(product, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0)", product);
        long address = createAddress(userBearer, "13800138000", true);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,2,1)", product);
        long orderId = createOrder(address, "cancel-1");
        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", secondUserBearer))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", secondUserBearer))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/orders/" + orderId + "/confirm-receipt").header("Authorization", userBearer))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("cancelled"));
        mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", userBearer))
                .andExpect(status().isOk());
        assertEquals(0, db.queryForObject("SELECT locked_quantity FROM inventory WHERE product_id=?", Integer.class, product));
        assertEquals(5, db.queryForObject("SELECT quantity FROM inventory WHERE product_id=?", Integer.class, product));
        assertEquals("released", db.queryForObject("SELECT status FROM order_stock_reservation WHERE order_id=?", String.class, orderId));
        mvc.perform(get("/api/v1/orders").header("Authorization", secondUserBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test void invalidOrderRollsBackAndPaidOrderConsumesStock() throws Exception {
        long category = category(0, "Process category");
        long product = product(category);
        setProductStatus(product, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,3,0)", product);
        long address = createAddress(userBearer, "13800138000", true);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,4,1)", product);
        mvc.perform(post("/api/v1/orders").header("Authorization", userBearer)
                .contentType("application/json").content(orderBody(address, "too-many")))
                .andExpect(status().isBadRequest());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM `order`", Integer.class));
        assertEquals(0, db.queryForObject("SELECT locked_quantity FROM inventory WHERE product_id=?", Integer.class, product));
        db.update("UPDATE cart_item SET quantity=2 WHERE user_id=100 AND product_id=?", product);
        long orderId = createOrder(address, "process-1");
        mvc.perform(put("/api/v1/admin/orders/" + orderId + "/process").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        db.update("UPDATE `order` SET status='paid' WHERE id=?", orderId);
        mvc.perform(put("/api/v1/admin/orders/" + orderId + "/process").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("processing"));
        assertEquals(1, db.queryForObject("SELECT quantity FROM inventory WHERE product_id=?", Integer.class, product));
        assertEquals(0, db.queryForObject("SELECT locked_quantity FROM inventory WHERE product_id=?", Integer.class, product));
        mvc.perform(put("/api/v1/orders/" + orderId + "/cancel").header("Authorization", userBearer))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/orders/" + orderId + "/confirm-receipt").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("completed"));
        mvc.perform(get("/api/v1/admin/orders").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test void adminOrderOperationsExposeShipmentNotesAndSafePendingClose() throws Exception {
        long category = category(0, "Admin order operations");
        long product = product(category); setProductStatus(product, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,2,0)", product);
        long address = createAddress(userBearer, "13800138000", true);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1)", product);
        long pending = createOrder(address, "admin-close");
        mvc.perform(put("/api/v1/admin/orders/" + pending + "/cancel").header("Authorization", bearer)
                .contentType("application/json").content("{\"remark\":\"超时未付款\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("cancelled"));
        mvc.perform(get("/api/v1/admin/orders/" + pending + "/operations").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].action").value("cancel"));
        mvc.perform(put("/api/v1/admin/orders/" + pending + "/cancel").header("Authorization", bearer)
                .contentType("application/json").content("{\"remark\":\"重复关闭\"}"))
                .andExpect(status().isOk());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_admin_operation WHERE order_id=? AND action='cancel'", Integer.class, pending));
        assertEquals(3, db.queryForObject("SELECT step FROM order_close_release WHERE order_id=?", Integer.class, pending));
        mvc.perform(post("/api/v1/admin/orders/" + pending + "/notes").header("Authorization", bearer)
                .contentType("application/json").content("{\"remark\":\"已通知客户\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].action").value("note"));

        db.update("UPDATE cart_item SET is_deleted=0,quantity=1,selected=1 WHERE user_id=100 AND product_id=?", product);
        long shipment = createOrder(address, "admin-ship");
        db.update("UPDATE `order` SET status='paid' WHERE id=?", shipment);
        mvc.perform(put("/api/v1/admin/orders/" + shipment + "/shipment").header("Authorization", bearer)
                .contentType("application/json").content("{\"carrier\":\"测试物流\",\"trackingNo\":\"ABC-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("processing"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_shipment WHERE order_id=?", Integer.class, shipment));
        mvc.perform(put("/api/v1/admin/orders/" + shipment + "/shipment").header("Authorization", bearer)
                .contentType("application/json").content("{\"carrier\":\"测试物流\",\"trackingNo\":\"ABC-123\"}"))
                .andExpect(status().isOk());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM order_admin_operation WHERE order_id=? AND action='ship'", Integer.class, shipment));
        assertEquals(1, db.queryForObject("SELECT quantity FROM inventory WHERE product_id=?", Integer.class, product));
        mvc.perform(get("/api/v1/orders/" + shipment).header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.logistics.trackingNo").value("ABC-123"));
        org.mockito.Mockito.when(orderLogistics.find(100L,orders.getById(shipment).getOrderNo()))
                .thenReturn(new com.gas.ecommerce.modules.order.vo.OrderLogisticsVO("测试物流","ABC-123","旧物流记录"));
        mvc.perform(put("/api/v1/admin/orders/" + shipment + "/shipment").header("Authorization", bearer)
                .contentType("application/json").content("{\"carrier\":\"新物流\",\"trackingNo\":\"XYZ-789\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.logistics.carrier").value("新物流"));
        assertEquals(0, db.queryForObject("SELECT locked_quantity FROM inventory WHERE product_id=?", Integer.class, product));
        assertEquals(1, db.queryForObject("SELECT quantity FROM inventory WHERE product_id=?", Integer.class, product));
        mvc.perform(get("/api/v1/orders/" + shipment).header("Authorization",secondUserBearer)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/orders/" + shipment + "/confirm-receipt").header("Authorization",userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("completed"));
        mvc.perform(put("/api/v1/admin/orders/" + shipment + "/shipment").header("Authorization",bearer)
                .contentType("application/json").content("{\"carrier\":\"Test\",\"trackingNo\":\"ZZZ-123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void adminOrderFiltersRejectInvalidRangesAndKeepUserScope() throws Exception {
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount,create_time) VALUES(701,'FILTER-A',100,'paid',10,10,'2026-10-01 08:00:00'),(702,'FILTER-B',101,'pending_payment',20,20,'2026-10-02 08:00:00')");
        mvc.perform(get("/api/v1/admin/orders").header("Authorization",bearer).param("userId","100").param("status","paid")
                .param("startTime","2026-10-01 00:00:00").param("endTime","2026-10-01 23:59:59"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.list[0].orderNo").value("FILTER-A"));
        mvc.perform(get("/api/v1/orders").header("Authorization",userBearer).param("userId","101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.list[0].userId").value("100"));
        mvc.perform(get("/api/v1/admin/orders").header("Authorization",bearer)
                .param("startTime","2026-10-02 00:00:00").param("endTime","2026-10-01 00:00:00"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/orders").header("Authorization",bearer).param("userId","-1"))
                .andExpect(status().isBadRequest());
    }

    @Test void adminOrderNotesAndFulfillmentArePermissionAndStateProtected() throws Exception {
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount) VALUES(703,'GUARD',100,'pending_payment',10,10)");
        mvc.perform(put("/api/v1/admin/orders/703/shipment").header("Authorization",bearer).contentType("application/json")
                .content("{\"carrier\":\"Test\",\"trackingNo\":\"ABC-123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/orders/703/notes").header("Authorization",userBearer).contentType("application/json").content("{\"remark\":\"private\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/orders/703/operations").header("Authorization",userBearer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/orders/703/cancel").header("Authorization",bearer).contentType("application/json").content("{\"remark\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/orders/703/notes").header("Authorization",bearer).contentType("application/json").content("{\"remark\":\"private internal note\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/703").header("Authorization",userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.operations").doesNotExist()).andExpect(jsonPath("$.data.remark").doesNotExist());
        db.update("UPDATE `order` SET status='paid' WHERE id=703");
        mvc.perform(put("/api/v1/admin/orders/703/cancel").header("Authorization",bearer).contentType("application/json").content("{\"remark\":\"close paid\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_shipment",Integer.class));
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='order:write')");
        mvc.perform(get("/api/v1/admin/orders/703/operations").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/orders/703/notes").header("Authorization",bearer).contentType("application/json").content("{\"remark\":\"read only\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void orderExpiryAndInputValidation() throws Exception {
        long category = category(0, "Expiry category");
        long product = product(category);
        setProductStatus(product, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,2,0)", product);
        long address = createAddress(userBearer, "13800138000", true);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1)", product);
        long id = createOrder(address, "expire-1");
        mvc.perform(get("/api/v1/orders").header("Authorization", userBearer).param("page", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").header("Authorization", userBearer)
                .contentType("application/json").content("{\"addressId\":1,\"requestId\":\"bad id\"}"))
                .andExpect(status().isBadRequest());
        db.update("UPDATE `order` SET expire_time=DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE id=?", id);
        orders.cancelExpired("TEST1");
        assertEquals("cancelled", db.queryForObject("SELECT status FROM `order` WHERE id=?", String.class, id));
        assertEquals(0, db.queryForObject("SELECT locked_quantity FROM inventory WHERE product_id=?", Integer.class, product));
    }

    private long applyAftersale(long orderId,String key) throws Exception {
        var result=mvc.perform(post("/api/v1/aftersales").header("Authorization",userBearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("orderId",orderId,"reason","申请退款","requestId",key))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
    }
    @Test void aftersaleAuditsAreScopedAndRefundReleasesOnlyUnfulfilledReservationsOnce() throws Exception {
        long order=createPaidOrder(userBearer,100,"audit-stock","audit-order","success",200);
        long id=applyAftersale(order,"audit-apply");
        mvc.perform(get("/api/v1/aftersales/"+id+"/operations").header("Authorization",secondUserBearer)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/aftersales/"+id+"/review").header("Authorization",bearer).contentType("application/json")
                .content("{\"status\":\"approved\",\"remark\":\"核实通过\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewedBy").value("7"));
        for(int i=0;i<2;i++) mvc.perform(put("/api/v1/admin/aftersales/"+id+"/complete").header("Authorization",bearer).contentType("application/json")
                .content("{\"refundReference\":\"  REF-AUDIT  \",\"remark\":\"渠道确认\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refundReference").value("REF-AUDIT"))
                .andExpect(jsonPath("$.data.completionRemark").value("渠道确认")).andExpect(jsonPath("$.data.completedTime").isNotEmpty());
        mvc.perform(get("/api/v1/admin/aftersales/"+id+"/operations").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].action").value("apply")).andExpect(jsonPath("$.data[1].action").value("approve"))
                .andExpect(jsonPath("$.data[2].action").value("complete")).andExpect(jsonPath("$.data[2].operatorId").value("7"));
        mvc.perform(get("/api/v1/aftersales/"+id+"/operations").header("Authorization",userBearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));
        assertEquals(20,db.queryForObject("SELECT SUM(quantity) FROM inventory",Integer.class));
        assertEquals(0,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM inventory_log WHERE change_type='order_release'",Integer.class));
        assertEquals("released",db.queryForObject("SELECT status FROM order_stock_reservation WHERE order_id=?",String.class,order));
    }
    @Test void aftersaleRequestKeyCannotBeReusedWithDifferentPayload() throws Exception {
        long order=createPaidOrder(userBearer,100,"key-stock","key-order","success",100);
        long id=applyAftersale(order,"same-key");
        for(var input:java.util.List.of(Map.of("orderId",order,"reason","不同原因","requestId","same-key"),Map.of("orderId",999999L,"reason","申请退款","requestId","same-key"))) {
            mvc.perform(post("/api/v1/aftersales").header("Authorization",userBearer).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isBadRequest());
        }
        assertEquals(id,applyAftersale(order,"same-key"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM aftersale_operation",Integer.class));
    }
    @Test void aftersaleFiltersPermissionsAndReviewRetriesAreEnforced() throws Exception {
        long order=createPaidOrder(userBearer,100,"filter-stock","filter-order","success",100);
        long id=applyAftersale(order,"filter-apply");
        String url="/api/v1/admin/aftersales";
        for(int i=0;i<2;i++) mvc.perform(put(url+"/"+id+"/review").header("Authorization",bearer).contentType("application/json").content("{\"status\":\"rejected\",\"remark\":\"不符合退款条件\"}")).andExpect(status().isOk());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM aftersale_operation",Integer.class));
        mvc.perform(get(url).header("Authorization",bearer).param("orderNo","TEST1").param("status","rejected")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get(url).header("Authorization",bearer).param("orderNo","MISSING")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/aftersales").header("Authorization",secondUserBearer).param("orderNo","TEST1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        for(var params:java.util.List.of(Map.of("status","invalid"),Map.of("size","101"),Map.of("sort","id;DROP TABLE aftersale"))) {
            var request=get(url).header("Authorization",bearer);params.forEach(request::param);mvc.perform(request).andExpect(status().isBadRequest());
        }
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='aftersale:write')");
        mvc.perform(get(url+"/"+id+"/operations").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(put(url+"/"+id+"/review").header("Authorization",bearer).contentType("application/json").content("{\"status\":\"approved\",\"remark\":\"attempt\"}")).andExpect(status().isForbidden());
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='aftersale:read')");
        mvc.perform(get(url+"/"+id+"/operations").header("Authorization",bearer)).andExpect(status().isForbidden());
    }
    @Test void aftersaleBlocksFulfillmentAndRejectedApplicationsAllowItAgain() throws Exception {
        long order=createPaidOrder(userBearer,100,"ship-stock","ship-order","success",100);
        long id=applyAftersale(order,"ship-apply");
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->orders.process(order));
        mvc.perform(put("/api/v1/admin/orders/"+order+"/shipment").header("Authorization",bearer).contentType("application/json").content("{\"carrier\":\"顺丰\",\"trackingNo\":\"SF123456\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/aftersales/"+id+"/review").header("Authorization",bearer).contentType("application/json").content("{\"status\":\"rejected\",\"remark\":\"核实后拒绝\"}")).andExpect(status().isOk());
        assertEquals("processing",orders.process(order).status());
    }
    @Test void aftersaleRefundForFulfilledGoodsDoesNotInventPhysicalReturnStock() throws Exception {
        long order=createPaidOrder(userBearer,100,"fulfilled-stock","fulfilled-order","success",100);
        orders.process(order);
        long id=applyAftersale(order,"fulfilled-apply");
        mvc.perform(put("/api/v1/admin/aftersales/"+id+"/review").header("Authorization",bearer).contentType("application/json").content("{\"status\":\"approved\",\"remark\":\"通过\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/aftersales/"+id+"/complete").header("Authorization",bearer).contentType("application/json").content("{\"refundReference\":\"REF-FULFILLED\"}")).andExpect(status().isOk());
        assertEquals(19,db.queryForObject("SELECT SUM(quantity) FROM inventory",Integer.class));
        assertEquals(0,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals("consumed",db.queryForObject("SELECT status FROM order_stock_reservation WHERE order_id=?",String.class,order));
    }
    @Test void aftersaleRefundFailureRollsBackPaymentOrderStockAndAudit() throws Exception {
        long order=createPaidOrder(userBearer,100,"rollback-stock","rollback-order","success",100);
        long id=applyAftersale(order,"rollback-apply");
        mvc.perform(put("/api/v1/admin/aftersales/"+id+"/review").header("Authorization",bearer).contentType("application/json").content("{\"status\":\"approved\",\"remark\":\"通过\"}")).andExpect(status().isOk());
        db.update("UPDATE payment SET amount=99 WHERE order_id=?",order);
        mvc.perform(put("/api/v1/admin/aftersales/"+id+"/complete").header("Authorization",bearer).contentType("application/json").content("{\"refundReference\":\"REF-BAD\"}")).andExpect(status().isBadRequest());
        assertEquals("approved",db.queryForObject("SELECT status FROM aftersale WHERE id=?",String.class,id));
        assertEquals("paid",db.queryForObject("SELECT status FROM `order` WHERE id=?",String.class,order));
        assertEquals(1,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM aftersale_operation",Integer.class));
        db.update("UPDATE payment SET amount=100 WHERE order_id=?",order);
        db.execute("ALTER TABLE aftersale_operation ADD CONSTRAINT test_no_completion CHECK (action<>'complete')");
        mvc.perform(put("/api/v1/admin/aftersales/"+id+"/complete").header("Authorization",bearer).contentType("application/json").content("{\"refundReference\":\"REF-ROLLBACK\"}")).andExpect(status().isInternalServerError());
        assertEquals("approved",db.queryForObject("SELECT status FROM aftersale WHERE id=?",String.class,id));
        assertEquals("success",db.queryForObject("SELECT status FROM payment WHERE order_id=?",String.class,order));
        assertEquals("paid",db.queryForObject("SELECT status FROM `order` WHERE id=?",String.class,order));
        assertEquals(1,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals("reserved",db.queryForObject("SELECT status FROM order_stock_reservation WHERE order_id=?",String.class,order));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM aftersale_operation",Integer.class));
    }
    @Test void aftersaleCancellationAuditDoesNotDuplicateAndApplicationCanBeResubmitted() throws Exception {
        long order=createPaidOrder(userBearer,100,"cancel-stock","cancel-order","success",100);
        long id=applyAftersale(order,"cancel-apply");
        for(int i=0;i<2;i++) mvc.perform(put("/api/v1/aftersales/"+id+"/cancel").header("Authorization",userBearer)).andExpect(status().isOk());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM aftersale_operation",Integer.class));
        assertEquals("cancel",db.queryForObject("SELECT action FROM aftersale_operation WHERE aftersale_id=? ORDER BY id DESC LIMIT 1",String.class,id));
        assertNotEquals(id,applyAftersale(order,"resubmit-apply"));
    }
    @Autowired com.gas.ecommerce.modules.aftersale.service.AftersaleService aftersaleService;
    @Test void aftersaleConcurrentRefundRetriesOnlyReleaseAndAuditOnce() throws Exception {
        long order=createPaidOrder(userBearer,100,"race-refund-stock","race-refund-order","success",100);
        long id=applyAftersale(order,"race-refund-apply");
        aftersaleService.review(id,7L,new com.gas.ecommerce.modules.aftersale.dto.AftersaleReviewRequest("approved","通过"));
        var request=new com.gas.ecommerce.modules.aftersale.dto.AftersaleCompleteRequest("REF-RACE","渠道已退款");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();return aftersaleService.complete(id,7L,request);});
            var b=pool.submit(()->{start.await();return aftersaleService.complete(id,7L,request);});
            start.countDown();
            assertEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS).id(),b.get(15,java.util.concurrent.TimeUnit.SECONDS).id());
        }
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM aftersale_operation",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM inventory_log WHERE change_type='order_release'",Integer.class));
    }
    @Test void aftersaleApplicationReviewAndRefundCompletionAreScopedAndIdempotent() throws Exception {
        long orderId = createPaidOrder(userBearer, 100, "as-order-1", "after-1", "success", 200);
        String request = json.writeValueAsString(Map.of("orderId", orderId, "reason", "商品存在质量问题", "requestId", "as-request-1"));
        var response = mvc.perform(post("/api/v1/aftersales").header("Authorization", userBearer)
                        .contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.amount").value(200)).andExpect(jsonPath("$.data.orderNo").value("TEST1"))
                .andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).at("/data/id").asLong();
        mvc.perform(post("/api/v1/aftersales").header("Authorization", userBearer)
                        .contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(get("/api/v1/aftersales/" + id).header("Authorization", secondUserBearer))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/aftersales/" + id + "/review").header("Authorization", bearer)
                        .contentType("application/json").content("{\"status\":\"approved\",\"remark\":\"同意退款\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("approved"));
        mvc.perform(put("/api/v1/admin/aftersales/" + id + "/complete").header("Authorization", bearer)
                        .contentType("application/json").content("{\"refundReference\":\"WX-REF-1\",\"remark\":\"退款完成\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.refundReference").value("WX-REF-1"));
        mvc.perform(put("/api/v1/admin/aftersales/" + id + "/complete").header("Authorization", bearer)
                        .contentType("application/json").content("{\"refundReference\":\"WX-REF-1\"}"))
                .andExpect(status().isOk());
        assertEquals("refunded", db.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, orderId));
        assertEquals("refunded", db.queryForObject("SELECT status FROM `order` WHERE id=?", String.class, orderId));
        mvc.perform(get("/api/v1/aftersales").header("Authorization", userBearer).param("status", "completed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM aftersale WHERE order_id=? AND is_deleted=0", Integer.class, orderId));
    }

    @Test void aftersaleRejectCancelAndBusinessRulesAreEnforced() throws Exception {
        long orderId = createPaidOrder(userBearer, 100, "as-order-2", "after-2", "success", 100);
        mvc.perform(post("/api/v1/aftersales").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("orderId", orderId, "reason", "退款申请", "requestId", "as-reject"))))
                .andExpect(status().isOk());
        var result = mvc.perform(get("/api/v1/aftersales").header("Authorization", userBearer))
                .andExpect(status().isOk()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).at("/data/list/0/id").asLong();
        mvc.perform(put("/api/v1/aftersales/" + id + "/cancel").header("Authorization", userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("rejected"));
        mvc.perform(put("/api/v1/admin/aftersales/" + id + "/review").header("Authorization", bearer)
                        .contentType("application/json").content("{\"status\":\"approved\",\"remark\":\"late\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/aftersales/" + id + "/complete").header("Authorization", bearer)
                        .contentType("application/json").content("{\"refundReference\":\"REF\"}"))
                .andExpect(status().isBadRequest());
        long unpaid = createPaidOrder(userBearer, 100, "as-order-unpaid", "after-unpaid", "pending", 100);
        mvc.perform(post("/api/v1/aftersales").header("Authorization", userBearer).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("orderId", unpaid, "reason", "申请退款", "requestId", "as-unpaid"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/aftersales").header("Authorization", userBearer).contentType("application/json")
                        .content("{\"orderId\":-1,\"reason\":\"\",\"requestId\":\"bad id\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/aftersales").header("Authorization", userBearer))
                .andExpect(status().isForbidden());
    }

    long createPaidOrder(String authorization, long userId, String cartSuffix, String orderRequest,
            String paymentStatus, int price) throws Exception {
        long category = category(0, "After sale " + cartSuffix);
        long product = product(category);
        mvc.perform(put("/api/v1/admin/products/" + product).header("Authorization", bearer)
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "categoryId", category, "name", "item-" + cartSuffix,
                                "price", price, "originalPrice", price))))
                .andExpect(status().isOk());
        setProductStatus(product, "on_sale", 200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,20,0)", product);
        long address = createAddress(authorization, "13800138000", true);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(?,?,1,1)", userId, product);
        var orderResponse = mvc.perform(post("/api/v1/orders").header("Authorization", authorization)
                        .contentType("application/json").content(orderBody(address, orderRequest)))
                .andExpect(status().isOk()).andReturn();
        long orderId = json.readTree(orderResponse.getResponse().getContentAsString()).at("/data/id").asLong();
        db.update("UPDATE `order` SET status=? WHERE id=?", "pending".equals(paymentStatus) ? "pending_payment" : "paid", orderId);
        db.update("INSERT INTO payment(payment_no,order_id,user_id,amount,channel,status,paid_time) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                "PAY-" + cartSuffix, orderId, userId, price, "test", paymentStatus);
        return orderId;
    }

    long createOrder(long address, String requestId) throws Exception {
        var response = mvc.perform(post("/api/v1/orders").header("Authorization", userBearer)
                        .contentType("application/json").content(orderBody(address, requestId)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/id").asLong();
    }
    String orderBody(long address, String requestId) throws Exception {
        return json.writeValueAsString(Map.of("addressId", address, "requestId", requestId));
    }

    long category(long parent, String name) throws Exception { return id(post("/api/v1/admin/categories"), categoryBody(parent, name, "active")); }
    long createAddress(String authorization, String phone, boolean makeDefault) throws Exception {
        var response = mvc.perform(post("/api/v1/users/me/addresses").header("Authorization", authorization)
                        .contentType("application/json").content(addressBody(phone, makeDefault)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/id").asLong();
    }
    String addressBody(String phone, boolean makeDefault) throws Exception {
        return json.writeValueAsString(Map.of("receiver", "张三", "phone", phone, "province", "四川省",
                "city", "自贡市", "district", "自流井区", "detail", "汇东路 1 号", "isDefault", makeDefault, "allowDuplicate", true));
    }
    String categoryBody(long parent, String name, String state) throws Exception {
        return json.writeValueAsString(Map.of("parentId", parent, "name", name, "sort", 0, "status", state));
    }
    long product(long category) throws Exception {
        return id(post("/api/v1/admin/products"), json.writeValueAsString(Map.of("categoryId", category, "name", "stove", "price", 100, "originalPrice", 120)));
    }
    long id(MockHttpServletRequestBuilder request, String body) throws Exception {
        var response = mvc.perform(request.header("Authorization", bearer).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/id").asLong();
    }
    void setProductStatus(long id, String state, int expected) throws Exception {
        mvc.perform(put("/api/v1/admin/products/" + id + "/status").header("Authorization", bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("status", state)))).andExpect(status().is(expected));
    }
    @Autowired com.gas.ecommerce.modules.coupon.service.CouponService couponService;

    long coupon(int quantity) throws Exception {
        return id(post("/api/v1/admin/coupons"), couponBody(quantity,20));
    }
    String couponBody(int quantity,int amount) throws Exception {
        return json.writeValueAsString(Map.of("name","满五十减二十","amount",amount,"minimumAmount",50,
                "startTime",java.time.LocalDateTime.now().minusDays(1).withNano(0),
                "endTime",java.time.LocalDateTime.now().plusDays(1).withNano(0),"quantity",quantity,"status","active"));
    }
    long claimCoupon(long template) throws Exception {
        var r=mvc.perform(post("/api/v1/coupons/"+template+"/claim").header("Authorization",userBearer))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).at("/data/id").asLong();
    }
    long couponOrder(long owned,String request,int quantity) throws Exception {
        long c=category(0,"Coupon "+request), p=product(c);
        setProductStatus(p,"on_sale",200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,?,0)",p,quantity);
        long address=createAddress(userBearer,"13800138000",true);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1)",p);
        return orders.create(100L,new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,request,owned)).id();
    }
    String couponState(long id) {return db.queryForObject("SELECT status FROM user_coupon WHERE id=?",String.class,id);}

    @Test void couponClaimAndAccessRules() throws Exception {
        long template=coupon(1), owned=claimCoupon(template);
        assertEquals(owned,claimCoupon(template));
        mvc.perform(post("/api/v1/coupons/"+template+"/claim").header("Authorization",secondUserBearer)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/coupons/"+template).header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.claimedQuantity").value(1)).andExpect(jsonPath("$.data.remainingQuantity").value(0));
        mvc.perform(get("/api/v1/coupons").header("Authorization",userBearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",secondUserBearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",userBearer).param("orderAmount","100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/user-coupons")).andExpect(status().isUnauthorized());
        db.update("UPDATE `user` SET status='inactive' WHERE id=100");
        mvc.perform(post("/api/v1/coupons/"+template+"/claim").header("Authorization",userBearer)).andExpect(status().isBadRequest());
    }
    @Test void couponCenterHidesOwnedCouponsButKeepsThemAvailableToOtherUsers() throws Exception {
        long template=coupon(10);
        claimCoupon(template);
        mvc.perform(get("/api/v1/coupons").header("Authorization",userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/coupons").header("Authorization",secondUserBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",userBearer).param("status","unused"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        assertEquals(claimCoupon(template),claimCoupon(template));
    }
    @Test void couponAdminAcceptsFrontendDateFormatAndNormalizesName() throws Exception {
        String body=json.writeValueAsString(Map.of("name","  页面优惠券  ","amount",10,"minimumAmount",100,
                "startTime","2026-10-28 08:30:00","endTime","2026-11-28 08:30:00","quantity",20,"status","active"));
        mvc.perform(post("/api/v1/admin/coupons").header("Authorization",bearer).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("页面优惠券"))
                .andExpect(jsonPath("$.data.startTime").value("2026-10-28 08:30:00"))
                .andExpect(jsonPath("$.data.remainingQuantity").value(20));
    }
    @Test void checkoutCouponListFiltersThresholdFaceValueAndStartTime() throws Exception {
        long owned=claimCoupon(coupon(2));
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",userBearer)
                .param("status","unused").param("orderAmount","49.99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",userBearer)
                .param("status","unused").param("orderAmount","50.00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        db.update("UPDATE user_coupon SET amount=50 WHERE id=?",owned);
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",userBearer).param("orderAmount","50.00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        db.update("UPDATE user_coupon SET amount=20,start_time=? WHERE id=?",java.time.LocalDateTime.now().plusHours(1),owned);
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",userBearer).param("orderAmount","100.00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",userBearer).param("orderAmount","-1"))
                .andExpect(status().isBadRequest());
    }
    @Test void couponClaimedTermsAreFrozenAndDeletionPreservesSnapshots() throws Exception {
        long template=coupon(2), owned=claimCoupon(template);
        mvc.perform(put("/api/v1/admin/coupons/"+template).header("Authorization",bearer).contentType("application/json").content(couponBody(2,30)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/coupons/"+template).header("Authorization",bearer).contentType("application/json").content(couponBody(0,20)))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/coupons/"+template).header("Authorization",bearer)).andExpect(status().isOk());
        long order=couponOrder(owned,"snapshot",10);
        assertEquals(0,new java.math.BigDecimal("80").compareTo(orders.detail(100L,order).payAmount()));
        assertEquals("locked",couponState(owned));
        orders.cancel(100L,order); orders.cancel(100L,order);
        assertEquals("unused",couponState(owned));
        assertNull(db.queryForObject("SELECT used_order_id FROM user_coupon WHERE id=?",Long.class,owned));
    }
    @Test void couponConcurrentClaimsRespectTotalAndPerUserLimits() throws Exception {
        long template=coupon(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(long u:new long[]{100L,101L}) futures.add(pool.submit(()->{start.await();try {couponService.claim(u,template);return true;}catch(com.gas.ecommerce.common.exception.BusinessException ex){return false;}}));
            start.countDown(); int successes=0;
            for(var f:futures) if(f.get(15,java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
            long winner=db.queryForObject("SELECT user_id FROM user_coupon WHERE coupon_id=?",Long.class,template);
            var a=pool.submit(()->couponService.claim(winner,template).id());
            var b=pool.submit(()->couponService.claim(winner,template).id());
            assertEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user_coupon",Integer.class));
    }
    @Test void couponStockFailureRollsBackReservationAndOrder() throws Exception {
        long owned=claimCoupon(coupon(1));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->couponOrder(owned,"stock-failure",0));
        assertEquals("unused",couponState(owned));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM `order`",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM cart_item WHERE user_id=100 AND is_deleted=0",Integer.class));
    }
    @Test void couponExpiryOwnershipAndMinimumAreCheckedOnServer() throws Exception {
        long template=coupon(2), owned=claimCoupon(template);
        var another=couponService.claim(101L,template);
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->couponOrder(another.id(),"other-owner",10));
        db.update("UPDATE user_coupon SET minimum_amount=1000 WHERE id=?",owned);
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->couponOrder(owned,"below-minimum",10));
        db.update("UPDATE user_coupon SET minimum_amount=50,expire_time=? WHERE id=?",java.time.LocalDateTime.now().minusMinutes(1),owned);
        mvc.perform(get("/api/v1/user-coupons").header("Authorization",userBearer).param("status","expired"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->couponOrder(owned,"expired-coupon",10));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM `order`",Integer.class));
    }
    @Test void couponPaymentIsIdempotentAndRefundUsesNetAmount() throws Exception {
        long owned=claimCoupon(coupon(1)), order=couponOrder(owned,"coupon-payment",10);
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->couponOrder(owned,"double-spend",10));
        org.mockito.Mockito.when(paymentNumbers.next()).thenReturn("COUPON-PAY");
        var p=payments.create(100L,new com.gas.ecommerce.modules.payment.dto.PaymentCreateRequest(order,"test"));
        assertEquals(0,new java.math.BigDecimal("80").compareTo(p.amount()));
        var callback=new com.gas.ecommerce.modules.payment.dto.PaymentCallbackRequest("COUPON-PAY","COUPON-TX",p.amount(),"success",java.time.LocalDateTime.now());
        db.update("UPDATE user_coupon SET expire_time=? WHERE id=?",java.time.LocalDateTime.now().minusMinutes(1),owned);
        payments.callback("test",callback); payments.callback("test",callback);
        assertEquals("used",couponState(owned));
        var r=mvc.perform(post("/api/v1/aftersales").header("Authorization",userBearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("orderId",order,"reason","退款","requestId","coupon-refund"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(80)).andReturn();
        long refund=json.readTree(r.getResponse().getContentAsString()).at("/data/id").asLong();
        mvc.perform(put("/api/v1/admin/aftersales/"+refund+"/review").header("Authorization",bearer).contentType("application/json").content("{\"status\":\"approved\",\"remark\":\"approved refund\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/aftersales/"+refund+"/complete").header("Authorization",bearer).contentType("application/json").content("{\"refundReference\":\"COUPON-REF\"}")).andExpect(status().isOk());
        assertEquals("used",couponState(owned));
    }
    @Test void couponTimeoutReleasesExpiredCouponWithoutMakingItReusable() throws Exception {
        long owned=claimCoupon(coupon(1)), order=couponOrder(owned,"timeout-coupon",10);
        db.update("UPDATE user_coupon SET expire_time=? WHERE id=?",java.time.LocalDateTime.now().minusMinutes(1),owned);
        db.update("UPDATE `order` SET expire_time=? WHERE id=?",java.time.LocalDateTime.now().minusMinutes(1),order);
        orders.cancelExpired(orders.detail(100L,order).orderNo());
        orders.cancelExpired(orders.detail(100L,order).orderNo());
        assertEquals("expired",couponState(owned));
        assertNull(db.queryForObject("SELECT used_order_id FROM user_coupon WHERE id=?",Long.class,owned));
    }

    @Test void couponConcurrentReservationsHaveOnlyOneWinner() throws Exception {
        long owned=claimCoupon(coupon(1));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(long order:new long[]{500L,501L}) futures.add(pool.submit(()->{start.await();try {
                couponService.reserve(100L,owned,order,new java.math.BigDecimal("100"));return true;
            }catch(com.gas.ecommerce.common.exception.BusinessException ex){return false;}}));
            start.countDown(); int successes=0;
            for(var f:futures) if(f.get(15,java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
        }
        assertEquals("locked",couponState(owned));
    }

    @Test void couponAdminCanIncreaseQuantityButCannotChangeClaimedTerms() throws Exception {
        long template=coupon(1); claimCoupon(template);
        var c=couponService.detail(template);
        var r=new com.gas.ecommerce.modules.system.dto.CouponRequest("new name",c.getAmount(),c.getMinimumAmount(),c.getStartTime(),c.getEndTime(),2,"inactive");
        var updated=couponService.saveCoupon(template,r);
        assertEquals(1,updated.getRemainingQuantity());
        mvc.perform(post("/api/v1/coupons/"+template+"/claim").header("Authorization",secondUserBearer)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/coupons").header("Authorization",bearer).param("sort","amount").param("order","asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Autowired com.gas.ecommerce.modules.workorder.service.WorkOrderService workOrders;

    long workStore() {
        db.update("INSERT INTO store(id,name,status) VALUES(900,'Service store','active'),(901,'Other store','active')");
        db.update("UPDATE employee SET store_id=900 WHERE id=7");
        return 900;
    }
    String workBody(long address,String request) throws Exception {
        return json.writeValueAsString(Map.of("addressId",address,"requestId",request,"type","repair","title","Repair stove",
                "description","Ignition fails","appointmentTime",java.time.LocalDateTime.now().plusDays(1).withNano(0),"storeId",900));
    }
    long createWork(String request) throws Exception {
        long address=createAddress(userBearer,"13800138000",true);
        var r=mvc.perform(post("/api/v1/work-orders").header("Authorization",userBearer).contentType("application/json").content(workBody(address,request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("pending")).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).at("/data/id").asLong();
    }
    void assignWork(long id) throws Exception {
        mvc.perform(put("/api/v1/admin/work-orders/"+id+"/assign").header("Authorization",bearer).contentType("application/json")
                .content("{\"storeId\":900,\"employeeId\":7,\"remark\":\"dispatch\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("accepted"));
    }
    void workAction(long id,String action,String token,int expected) throws Exception {
        mvc.perform(put("/api/v1/admin/work-orders/"+id+"/"+action).header("Authorization",token)
                .contentType("application/json").content("{\"remark\":\"service result\"}")).andExpect(status().is(expected));
    }
    @Test void workOrderLifecycleSnapshotsAndLogsAreConsistent() throws Exception {
        workStore(); long id=createWork("work-flow");
        String snapshot=db.queryForObject("SELECT address_snapshot FROM work_order WHERE id=?",String.class,id);
        assertFalse(snapshot.contains("13800138000"));
        db.update("UPDATE user_address SET detail='changed' WHERE user_id=100");
        mvc.perform(get("/api/v1/work-orders/"+id).header("Authorization",userBearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address.detail").value("汇东路 1 号")).andExpect(jsonPath("$.data.address.phone").value("13800138000"));
        assignWork(id); assignWork(id);
        workAction(id,"complete",bearer,400);
        workAction(id,"start",bearer,200); workAction(id,"start",bearer,200);
        mvc.perform(put("/api/v1/work-orders/"+id+"/cancel").header("Authorization",userBearer)
                .contentType("application/json").content("{\"remark\":\"cancel\"}")).andExpect(status().isBadRequest());
        workAction(id,"complete",bearer,200); workAction(id,"complete",bearer,200);
        mvc.perform(get("/api/v1/work-orders/"+id+"/logs").header("Authorization",userBearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4)).andExpect(jsonPath("$.data.list[0].operatorType").value("user"))
                .andExpect(jsonPath("$.data.list[1].operatorType").value("employee"));
        assertNotNull(workOrders.detail(100L,id).completedTime());
        workAction(id,"start",bearer,400);
    }
    @Test void workOrderOwnershipAndInputValidationAreEnforced() throws Exception {
        workStore(); long id=createWork("work-access");
        for(String path:new String[]{"/api/v1/work-orders/"+id,"/api/v1/work-orders/"+id+"/logs"})
            mvc.perform(get(path).header("Authorization",secondUserBearer)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/work-orders").header("Authorization",secondUserBearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/work-orders").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/work-orders").header("Authorization",userBearer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/work-orders").header("Authorization",userBearer).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/work-orders").header("Authorization",userBearer).param("status","bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/work-orders").header("Authorization",bearer).param("sort","appointmentTime").param("keyword","Repair").param("storeId","900"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        long otherAddress=createAddress(secondUserBearer,"13800138001",false);
        mvc.perform(post("/api/v1/work-orders").header("Authorization",userBearer).contentType("application/json").content(workBody(otherAddress,"bad-address"))).andExpect(status().isBadRequest());
        db.update("UPDATE `user` SET status='inactive' WHERE id=100");
        mvc.perform(get("/api/v1/work-orders").header("Authorization",userBearer)).andExpect(status().isBadRequest());
    }
    @Test void workOrderCancellationIsIdempotentAndTerminal() throws Exception {
        workStore(); long id=createWork("work-cancel"); assignWork(id);
        for(int i=0;i<2;i++) mvc.perform(put("/api/v1/work-orders/"+id+"/cancel").header("Authorization",userBearer)
                .contentType("application/json").content("{\"remark\":\"cancel\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("cancelled"));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM work_order_log WHERE work_order_id=?",Integer.class,id));
        workAction(id,"start",bearer,400); workAction(id,"complete",bearer,400);
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->workOrders.assign(7L,id,
                new com.gas.ecommerce.modules.workorder.dto.WorkOrderAssignRequest(900L,7L,"late dispatch")));
    }
    @Test void workOrderAssignmentRejectsWrongStoreAndUnauthorizedEmployee() throws Exception {
        workStore(); long id=createWork("work-assignment");
        db.update("INSERT INTO employee(id,username,password_hash,status,store_id) VALUES(8,'tech8','unused','active',900)");
        var r=new com.gas.ecommerce.modules.workorder.dto.WorkOrderAssignRequest(901L,7L,"wrong store");
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->workOrders.assign(7L,id,r));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->workOrders.assign(7L,id,
                new com.gas.ecommerce.modules.workorder.dto.WorkOrderAssignRequest(900L,8L,"no permission")));
        assignWork(id);
        db.update("INSERT INTO employee_role(employee_id,role_id) SELECT 8,id FROM role WHERE role_key='admin'");
        String tech="Bearer "+tokens.generateAccessToken(8L,"admin");
        workAction(id,"start",tech,403);
        workOrders.assign(7L,id,new com.gas.ecommerce.modules.workorder.dto.WorkOrderAssignRequest(900L,8L,"reassign"));
        workAction(id,"start",bearer,403); workAction(id,"start",tech,200); workAction(id,"complete",tech,200);
        mvc.perform(put("/api/v1/admin/work-orders/"+id+"/complete").header("Authorization",tech)
                .contentType("application/json").content("{\"remark\":\"different result\"}")).andExpect(status().isBadRequest());
    }
    @Test void workOrderConcurrentSubmissionCreatesOneRecordAndOneLog() throws Exception {
        workStore(); long address=createAddress(userBearer,"13800138000",true);
        var r=new com.gas.ecommerce.modules.workorder.dto.WorkOrderCreateRequest("inspection","check","annual check",900L,address,java.time.LocalDateTime.now().plusDays(1),"concurrent-work");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();return workOrders.create(100L,r).id();});
            var b=pool.submit(()->{start.await();return workOrders.create(100L,r).id();}); start.countDown();
            assertEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order_log",Integer.class));
    }
    @Test void workOrderConcurrentStartAndCancellationCannotBothSucceed() throws Exception {
        workStore(); long id=createWork("work-race"); assignWork(id);
        var remark=new com.gas.ecommerce.modules.workorder.dto.WorkOrderActionRequest("race");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();try{workOrders.start(7L,id,remark);return true;}catch(com.gas.ecommerce.common.exception.BusinessException e){return false;}});
            var b=pool.submit(()->{start.await();try{workOrders.cancel(100L,id,remark);return true;}catch(com.gas.ecommerce.common.exception.BusinessException e){return false;}});start.countDown();
            assertNotEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM work_order_log WHERE work_order_id=?",Integer.class,id));
    }

    @Test void workOrderPermissionsAndOrganizationChangesRespectOpenAssignments() throws Exception {
        workStore(); long id=createWork("work-organization");
        db.update("INSERT INTO employee(id,username,password_hash,status,store_id) VALUES(8,'technician','unused','active',900)");
        db.update("INSERT INTO role(id,name,role_key,status) VALUES(900,'Technician','technician','active')");
        db.update("INSERT INTO employee_role(employee_id,role_id) VALUES(8,900)");
        db.update("INSERT INTO role_permission(role_id,permission_id) SELECT 900,id FROM permission WHERE permission_key='workorder:process'");
        workOrders.assign(7L,id,new com.gas.ecommerce.modules.workorder.dto.WorkOrderAssignRequest(900L,8L,"dispatch"));
        String tech="Bearer "+tokens.generateAccessToken(8L,"admin");
        mvc.perform(get("/api/v1/admin/work-orders").header("Authorization",tech)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/work-orders/"+id+"/assign").header("Authorization",tech).contentType("application/json")
                .content("{\"storeId\":900,\"employeeId\":8,\"remark\":\"dispatch\"}")).andExpect(status().isForbidden());
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->workManagement.assignPermissions(900L,
                new com.gas.ecommerce.modules.system.dto.AssignmentRequest(java.util.List.of())));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->workManagement.updateEmployee(8L,
                new com.gas.ecommerce.modules.system.dto.EmployeeRequest("technician","Tech","13800138000",901L,null,null,"active")));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->workManagement.deleteEmployee(8L));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->workManagement.saveStore(900L,
                new com.gas.ecommerce.modules.system.dto.StoreRequest("Service store","13800138000","P","C","D","A",null,"inactive")));
        assertEquals(900L,db.queryForObject("SELECT store_id FROM employee WHERE id=8",Long.class));
        assertEquals("active",db.queryForObject("SELECT status FROM store WHERE id=900",String.class));
        workAction(id,"start",tech,200); workAction(id,"complete",tech,200);
        workManagement.assignPermissions(900L,new com.gas.ecommerce.modules.system.dto.AssignmentRequest(java.util.List.of()));
        workManagement.updateEmployee(8L,new com.gas.ecommerce.modules.system.dto.EmployeeRequest("technician","Tech","13800138000",901L,null,null,"active"));
    }
    @Autowired com.gas.ecommerce.modules.system.service.OrganizationManagementService workManagement;

    private void seedStatistics() {
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount,create_time,is_deleted) VALUES(700,'STAT-OLD',100,'completed',50,50,'2026-03-01 10:00:00',0),(701,'STAT-REFUND',100,'refunded',100,90,'2026-03-01 11:00:00',0),(702,'STAT-PENDING',100,'pending_payment',40,40,'2026-03-02 12:00:00',0),(703,'STAT-NEXT',100,'paid',30,30,'2026-03-03 00:00:00',0),(704,'STAT-DELETED',100,'paid',999,999,'2026-03-02 12:00:00',1)");
        db.update("INSERT INTO payment(id,payment_no,order_id,user_id,amount,status,paid_time) VALUES(700,'SP-OLD',700,100,50,'success','2026-03-01 10:00:00'),(701,'SP-REFUND',701,100,90,'refunded','2026-03-02 00:00:00'),(702,'SP-DUP',701,100,90,'success','2026-03-02 01:00:00'),(703,'SP-NEXT',703,100,30,'success','2026-03-03 00:00:00'),(704,'SP-DELETED',704,100,999,'success','2026-03-02 12:00:00'),(705,'SP-FAIL',702,100,40,'failed','2026-03-02 12:00:00')");
        db.update("INSERT INTO aftersale(aftersale_no,order_id,user_id,amount,status,completed_time) VALUES('SA-1',701,100,90,'completed','2026-03-03 00:00:00'),('SA-REJECT',702,100,40,'rejected','2026-03-02 12:00:00')");
        db.update("INSERT INTO order_item(order_id,product_id,product_name,quantity,unit_price,amount) VALUES(701,800,'Snapshot name',2,50,100),(703,801,'Next product',1,30,30),(704,800,'Deleted order',99,10,990),(702,800,'Unpaid',4,10,40)");
        db.update("INSERT INTO `user`(id,status,create_time,is_deleted) VALUES(700,'active','2026-03-02 23:59:59',0),(701,'active','2026-03-02 12:00:00',1)");
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder statisticsGet(String path,String start,String end) {
        return get("/api/v1/admin/statistics"+path).header("Authorization",bearer).param("startDate",start).param("endDate",end);
    }
    @Test void statisticsOverviewUsesPaymentDatesAndDoesNotDoubleCountRefundedReceipts() throws Exception {
        seedStatistics();
        mvc.perform(statisticsGet("/overview","2026-03-02","2026-03-02")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.createdOrders").value(1))
                .andExpect(jsonPath("$.data.current.paidOrders").value(1))
                .andExpect(jsonPath("$.data.current.receivedAmount").value(90))
                .andExpect(jsonPath("$.data.current.refundAmount").value(0))
                .andExpect(jsonPath("$.data.current.newCustomers").value(1))
                .andExpect(jsonPath("$.data.previous.receivedAmount").value(50))
                .andExpect(jsonPath("$.data.receivedGrowthPercent").value(80))
                .andExpect(jsonPath("$.data.orderStatuses.pending_payment").value(1))
                .andExpect(jsonPath("$.data.recentOrders[0].orderNo").value("STAT-PENDING"));
        mvc.perform(statisticsGet("/overview","2026-03-03","2026-03-03")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.receivedAmount").value(30))
                .andExpect(jsonPath("$.data.current.refundAmount").value(90))
                .andExpect(jsonPath("$.data.current.netReceivedAmount").value(-60));
    }
    @Test void statisticsTrendFillsEmptyDatesAndReportPaginates() throws Exception {
        seedStatistics();
        mvc.perform(statisticsGet("/trend","2026-03-01","2026-03-04")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[1].metrics.receivedAmount").value(90))
                .andExpect(jsonPath("$.data[2].metrics.refundAmount").value(90))
                .andExpect(jsonPath("$.data[3].metrics.createdOrders").value(0));
        mvc.perform(statisticsGet("/reports/daily","2026-03-01","2026-03-04").param("size","2").param("page","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4)).andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.list[0].date").value("2026-03-02"));
        mvc.perform(statisticsGet("/reports/daily","2026-03-01","2026-03-04").param("page","2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list.length()").value(0));
    }
    @Test void statisticsProductRankingUsesPaidItemSnapshotsWithoutMultiplyingPaymentRows() throws Exception {
        seedStatistics();
        mvc.perform(statisticsGet("/products","2026-03-02","2026-03-02")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].productId").value("800"))
                .andExpect(jsonPath("$.data[0].productName").value("Snapshot name"))
                .andExpect(jsonPath("$.data[0].quantity").value(2)).andExpect(jsonPath("$.data[0].paidOrders").value(1))
                .andExpect(jsonPath("$.data[0].merchandiseAmount").value(100));
    }
    @Test void statisticsStoresUseCreationAndCompletionDatesAndCurrentBacklog() throws Exception {
        db.update("INSERT INTO store(id,name) VALUES(700,'Statistics store')");
        db.update("INSERT INTO work_order(work_order_no,user_id,store_id,type,status,title,create_time,completed_time,appointment_time) VALUES('SW-1',100,700,'inspection','completed','done','2026-03-01 10:00:00','2026-03-02 12:00:00',null),('SW-2',100,700,'inspection','accepted','open','2026-03-02 10:00:00',null,'2020-01-01 10:00:00'),('SW-3',100,null,'inspection','pending','unassigned','2026-03-02 10:00:00',null,null)");
        mvc.perform(statisticsGet("/stores","2026-03-02","2026-03-02")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].storeId").value("700"))
                .andExpect(jsonPath("$.data[0].createdWorkOrders").value(1))
                .andExpect(jsonPath("$.data[0].completedWorkOrders").value(1))
                .andExpect(jsonPath("$.data[0].openWorkOrders").value(1));
        mvc.perform(statisticsGet("/overview","2026-03-02","2026-03-02")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingWorkOrders").value(2)).andExpect(jsonPath("$.data.overdueWorkOrders").value(1));
    }
    @Test void statisticsExportContainsAllDatesAndMatchesAmounts() throws Exception {
        seedStatistics();
        var response=mvc.perform(statisticsGet("/reports/daily/export","2026-03-02","2026-03-04").param("size","1"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=business-daily.csv"))
                .andReturn().getResponse();
        String csv=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFdate,"));assertTrue(csv.contains("2026-03-02,1,1,90.00,0,0.00,90.00,1\r\n"));
        assertTrue(csv.contains("2026-03-03,1,1,30.00,1,90.00,-60.00,0\r\n"));assertEquals(4,csv.lines().count());
    }
    @Test void statisticsRejectsInvalidDatesAndSeparatesReadFromExportPermission() throws Exception {
        mvc.perform(statisticsGet("/overview","2026-03-03","2026-03-02")).andExpect(status().isBadRequest());
        mvc.perform(statisticsGet("/trend","2025-01-01","2026-03-02")).andExpect(status().isBadRequest());
        mvc.perform(statisticsGet("/overview","invalid","2026-03-02")).andExpect(status().isBadRequest());
        mvc.perform(statisticsGet("/products","2026-03-02","2026-03-02").param("limit","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/statistics/overview").header("Authorization",userBearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/statistics/overview")).andExpect(status().isUnauthorized());
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='statistics:export')");
        mvc.perform(statisticsGet("/reports/daily/export","2026-03-02","2026-03-02")).andExpect(status().isForbidden());
        mvc.perform(statisticsGet("/overview","2026-02-01","2026-02-01")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current.receivedAmount").value(0)).andExpect(jsonPath("$.data.receivedGrowthPercent").isEmpty());
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='statistics:read')");
        mvc.perform(statisticsGet("/overview","2026-03-02","2026-03-02")).andExpect(status().isForbidden());
    }

    @Autowired com.gas.ecommerce.modules.inventory.service.InventoryService inventoryService;
    @Test void inventorySelectorsUseInventoryPermissionAndExcludeUnavailableReferences() throws Exception {
        long p=product(category(0,"Inventory selectors"));
        db.update("INSERT INTO store(id,name,status,is_deleted) VALUES(700,'Active store','active',0),(701,'Disabled store','inactive',0),(702,'Deleted store','active',1)");
        long deleted=product(category(0,"Deleted selector"));
        db.update("UPDATE product SET is_deleted=1 WHERE id=?",deleted);
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key IN ('product:read','store:read'))");
        mvc.perform(get("/api/v1/admin/inventory/options").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.products.length()").value(1))
                .andExpect(jsonPath("$.data.products[0].id").value(Long.toString(p)))
                .andExpect(jsonPath("$.data.stores.length()").value(1))
                .andExpect(jsonPath("$.data.stores[0].id").value("700"));
        mvc.perform(get("/api/v1/admin/inventory/options").header("Authorization",userBearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/inventory/options")).andExpect(status().isUnauthorized());
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='inventory:read')");
        mvc.perform(get("/api/v1/admin/inventory/options").header("Authorization",bearer)).andExpect(status().isForbidden());
    }
    private long inventoryStock(int quantity) throws Exception {
        long p=product(category(0,"Stock"));
        return inventoryService.create(7L,new com.gas.ecommerce.modules.inventory.dto.InventoryCreateRequest(p,null,quantity,"stock-create","opening")).inventoryId();
    }
    private com.gas.ecommerce.modules.inventory.dto.InventoryChangeRequest stockChange(int quantity,String key) {
        return new com.gas.ecommerce.modules.inventory.dto.InventoryChangeRequest(quantity,key,"stock operation");
    }
    @Test void inventoryChangesAreAuditedAndIdempotent() throws Exception {
        long id=inventoryStock(10);
        var r=stockChange(5,"stock-in");var first=inventoryService.inbound(7L,id,r);
        assertEquals(first,inventoryService.inbound(7L,id,r));
        assertEquals(10,first.quantityBefore());assertEquals(15,first.quantityAfter());
        inventoryService.outbound(7L,id,stockChange(3,"stock-out"));
        var adjusted=inventoryService.adjust(7L,id,new com.gas.ecommerce.modules.inventory.dto.InventoryAdjustRequest(8,"stock-count","count"));
        assertEquals(-4,adjusted.changeQuantity());assertEquals(8,inventoryService.detail(id).availableQuantity());
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->inventoryService.inbound(7L,id,stockChange(6,"stock-in")));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->inventoryService.outbound(7L,id,r));
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM inventory_log",Integer.class));
    }
    @Test void inventoryCannotReduceLockedStockAndFailureRollsBack() throws Exception {
        long id=inventoryStock(10);db.update("UPDATE inventory SET locked_quantity=7 WHERE id=?",id);
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->inventoryService.outbound(7L,id,stockChange(4,"too-many")));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->inventoryService.adjust(7L,id,new com.gas.ecommerce.modules.inventory.dto.InventoryAdjustRequest(6,"bad-count","count")));
        assertEquals(10,inventoryService.detail(id).quantity());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM inventory_log",Integer.class));
        inventoryService.outbound(7L,id,stockChange(3,"all-available"));
        assertEquals(0,inventoryService.detail(id).availableQuantity());
    }
    @Test void inventoryRejectsOverflowDuplicateLocationAndInvalidReferences() throws Exception {
        long id=inventoryStock(Integer.MAX_VALUE);long p=inventoryService.detail(id).productId();
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->inventoryService.inbound(7L,id,stockChange(1,"overflow")));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->inventoryService.create(7L,new com.gas.ecommerce.modules.inventory.dto.InventoryCreateRequest(p,null,0,"duplicate","opening")));
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->inventoryService.create(7L,new com.gas.ecommerce.modules.inventory.dto.InventoryCreateRequest(p,999L,0,"missing-store","opening")));
        mvc.perform(delete("/api/v1/admin/products/"+p).header("Authorization",bearer)).andExpect(status().isBadRequest());
        assertEquals(Integer.MAX_VALUE,inventoryService.detail(id).quantity());
    }
    @Test void inventoryEndpointsValidatePermissionsFiltersAndRequests() throws Exception {
        long id=inventoryStock(10);String url="/api/v1/admin/inventory";
        mvc.perform(get(url).header("Authorization",userBearer)).andExpect(status().isForbidden());
        db.update("DELETE FROM role_permission WHERE permission_id IN (SELECT id FROM permission WHERE permission_key='inventory:write')");
        mvc.perform(post(url+"/"+id+"/inbound").header("Authorization",bearer).contentType("application/json").content("{\"quantity\":1,\"requestId\":\"http\",\"remark\":\"receive\"}")).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization",bearer).param("headquarters","true").param("availableMax","10")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get(url).header("Authorization",bearer).param("availableMax","9")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(url).header("Authorization",bearer).param("headquarters","true").param("storeId","1")).andExpect(status().isBadRequest());
        mvc.perform(get(url).header("Authorization",bearer).param("sort","bad SQL")).andExpect(status().isBadRequest());
        mvc.perform(get(url+"/logs").header("Authorization",bearer).param("inventoryId",Long.toString(id)).param("changeType","initial")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void inventoryConcurrentRetriesOnlyApplyOnce() throws Exception {
        long id=inventoryStock(0);var r=stockChange(5,"concurrent-in");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();return inventoryService.inbound(7L,id,r);});
            var b=pool.submit(()->{start.await();return inventoryService.inbound(7L,id,r);});start.countDown();
            assertEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(5,inventoryService.detail(id).quantity());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM inventory_log",Integer.class));
    }
    @Test void inventoryOrderLogsCaptureReservationAndReleaseSnapshots() throws Exception {
        long id=inventoryStock(10);long p=inventoryService.detail(id).productId();setProductStatus(p,"on_sale",200);
        long address=createAddress(userBearer,"13800138000",true);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,2,1)",p);
        long order=createOrder(address,"stock-order");
        assertEquals(2,inventoryService.detail(id).lockedQuantity());
        assertEquals(10,db.queryForObject("SELECT quantity_after FROM inventory_log WHERE change_type='order_lock'",Integer.class));
        assertEquals(2,db.queryForObject("SELECT locked_after FROM inventory_log WHERE change_type='order_lock'",Integer.class));
        mvc.perform(put("/api/v1/orders/"+order+"/cancel").header("Authorization",userBearer)).andExpect(status().isOk());
        assertEquals(0,inventoryService.detail(id).lockedQuantity());
        assertEquals(10,db.queryForObject("SELECT quantity_after FROM inventory_log WHERE change_type='order_release'",Integer.class));
        assertEquals(id,db.queryForObject("SELECT inventory_id FROM inventory_log WHERE change_type='order_release'",Long.class));
    }
    @Test void inventoryHttpWritesValidateAndReturnSnapshots() throws Exception {
        long p=product(category(0,"HTTP stock"));String url="/api/v1/admin/inventory";
        var result=mvc.perform(post(url).header("Authorization",bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("productId",p,"quantity",0,"requestId","http-create","remark","opening"))))
                .andExpect(status().isOk()).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).at("/data/inventoryId").asLong();
        for(String action:java.util.List.of("inbound","outbound"))
            mvc.perform(post(url+"/"+id+"/"+action).header("Authorization",bearer).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("quantity",3,"requestId","http-"+action,"remark","movement"))))
                    .andExpect(status().isOk());
        mvc.perform(put(url+"/"+id+"/adjust").header("Authorization",bearer).contentType("application/json")
                .content("{\"quantity\":2,\"requestId\":\"http-adjust\",\"remark\":\"count\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.quantityAfter").value(2));
        mvc.perform(post(url+"/"+id+"/inbound").header("Authorization",bearer).contentType("application/json")
                .content("{\"quantity\":0,\"requestId\":\"invalid\",\"remark\":\"receive\"}")).andExpect(status().isBadRequest());
    }
    @Test void inventoryConcurrentCreationProducesOneHeadquartersRow() throws Exception {
        long p=product(category(0,"Concurrent stock"));
        var r=new com.gas.ecommerce.modules.inventory.dto.InventoryCreateRequest(p,null,0,"race-create","opening");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();return inventoryService.create(7L,r);});
            var b=pool.submit(()->{start.await();return inventoryService.create(7L,r);});start.countDown();
            assertEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM inventory WHERE product_id=?",Integer.class,p));
    }
    @Test void inventoryOutboundAndOrderReservationCannotOversell() throws Exception {
        long id=inventoryStock(2);long p=inventoryService.detail(id).productId();setProductStatus(p,"on_sale",200);
        long address=createAddress(userBearer,"13800138000",true);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,2,1)",p);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();try{inventoryService.outbound(7L,id,stockChange(2,"race-out"));return true;}catch(com.gas.ecommerce.common.exception.BusinessException e){return false;}});
            var b=pool.submit(()->{start.await();try{orders.create(100L,new com.gas.ecommerce.modules.order.dto.OrderCreateRequest(address,null,"stock-race"));return true;}catch(com.gas.ecommerce.common.exception.BusinessException e){return false;}});start.countDown();
            assertNotEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(0,inventoryService.detail(id).availableQuantity());
    }


    private void readOrder(long id,long user,String state) {
        db.update("INSERT INTO `order`(id,order_no,user_id,status,total_amount,pay_amount,create_time,expire_time) VALUES(?,?,?,?,50,50,?,?)",
                id,"READ-"+id,user,state,java.time.LocalDateTime.now().minusMinutes(2).withNano(0),java.time.LocalDateTime.now().plusMinutes(28));
    }
    @Test void orderTabsPaginateAndNeverTrustUserIdFromRequest() throws Exception {
        readOrder(900,100,"paid");readOrder(901,100,"pending_payment");readOrder(902,101,"paid");readOrder(903,100,"paid");
        mvc.perform(get("/api/v1/orders").header("Authorization",userBearer).param("status","paid").param("size","1").param("page","1").param("userId","101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.list[0].id").value(903));
        mvc.perform(get("/api/v1/orders").header("Authorization",userBearer).param("status","paid").param("size","1").param("page","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].id").value(900));
        mvc.perform(get("/api/v1/orders").header("Authorization",secondUserBearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.list[0].id").value(902));
        mvc.perform(get("/api/v1/orders").header("Authorization",userBearer).param("status","bogus")).andExpect(status().isBadRequest());
    }
    @Test void orderCursorHandlesEqualTimestampsAndRejectsForeignAnchorAndDeepOffset() throws Exception {
        readOrder(900,100,"paid");readOrder(901,100,"paid");readOrder(902,101,"paid");
        db.update("UPDATE `order` SET create_time='2026-10-01 12:00:00'");
        mvc.perform(get("/api/v1/orders/scroll").header("Authorization",userBearer).param("size","1").param("status","paid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].id").value(901)).andExpect(jsonPath("$.data.nextCursor").value(901));
        mvc.perform(get("/api/v1/orders/scroll").header("Authorization",userBearer).param("size","1").param("afterId","901").param("status","paid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].id").value(900)).andExpect(jsonPath("$.data.hasMore").value(false));
        mvc.perform(get("/api/v1/orders/scroll").header("Authorization",userBearer).param("afterId","902")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/scroll").header("Authorization",userBearer).param("afterId","901").param("status","completed")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders").header("Authorization",userBearer).param("page","502")).andExpect(status().isBadRequest());
    }
    @Test void orderHorizontalAuthorizationCoversDetailCancelAndReceipt() throws Exception {
        readOrder(900,101,"pending_payment");readOrder(901,101,"processing");
        mvc.perform(get("/api/v1/orders/900").header("Authorization",userBearer)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/orders/900/cancel").header("Authorization",userBearer)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/orders/901/confirm-receipt").header("Authorization",userBearer)).andExpect(status().isBadRequest());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_status_log",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM order_close_release",Integer.class));
        assertEquals("pending_payment",db.queryForObject("SELECT status FROM `order` WHERE id=900",String.class));
        assertEquals("processing",db.queryForObject("SELECT status FROM `order` WHERE id=901",String.class));
    }
    @Test void orderDetailAggregatesTimelinePaymentsAndHidesUnavailableLogistics() throws Exception {
        readOrder(900,100,"paid");
        db.update("INSERT INTO order_item(order_id,product_id,product_name,quantity,unit_price,amount) VALUES(900,1,'Snapshot',1,50,50)");
        db.update("INSERT INTO order_status_log(order_id,from_status,to_status) VALUES(900,NULL,'pending_payment'),(900,'pending_payment','paid')");
        db.update("INSERT INTO payment(payment_no,order_id,user_id,amount,channel,status,provider_transaction_no) VALUES('READ-P',900,100,50,'test','success','PRIVATE-TRADE'),('FOREIGN-P',900,101,50,'test','failed',NULL)");
        org.mockito.Mockito.when(orderLogistics.find(100L,"READ-900")).thenThrow(new IllegalStateException("timeout"));
        mvc.perform(get("/api/v1/orders/900").header("Authorization",userBearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productName").value("Snapshot"))
                .andExpect(jsonPath("$.data.timeline.length()").value(2)).andExpect(jsonPath("$.data.timeline[1].toStatus").value("paid"))
                .andExpect(jsonPath("$.data.payments.length()").value(1)).andExpect(jsonPath("$.data.payments[0].paymentNo").value("READ-P"))
                .andExpect(jsonPath("$.data.payments[0].providerTransactionNo").doesNotExist()).andExpect(jsonPath("$.data.logistics").doesNotExist());
        org.mockito.Mockito.doReturn(new com.gas.ecommerce.modules.order.vo.OrderLogisticsVO("Carrier","TRACK","In transit")).when(orderLogistics).find(100L,"READ-900");
        mvc.perform(get("/api/v1/orders/900").header("Authorization",userBearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.logistics.trackingNo").value("TRACK"));
    }
    @Test void orderCancelIsIdempotentAndOnlyPendingCanBeCancelled() throws Exception {
        readOrder(900,100,"pending_payment");readOrder(901,100,"paid");
        for(int i=0;i<2;i++) mvc.perform(put("/api/v1/orders/900/cancel").header("Authorization",userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("cancelled"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_status_log WHERE order_id=900 AND to_status='cancelled'",Integer.class));
        assertEquals(3,db.queryForObject("SELECT step FROM order_close_release WHERE order_id=900",Integer.class));
        org.mockito.Mockito.verify(checkoutPoints,org.mockito.Mockito.times(1)).release("READ-900");
        mvc.perform(put("/api/v1/orders/901/cancel").header("Authorization",userBearer)).andExpect(status().isBadRequest());
    }
    @Test void orderReceiptConcurrentRetriesProduceOneTransition() throws Exception {
        readOrder(900,100,"processing");readOrder(901,100,"pending_payment");
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return orders.confirmReceipt(100L,900L);});
            var b=pool.submit(()->{start.await();return orders.confirmReceipt(100L,900L);});start.countDown();
            assertEquals("completed",a.get(10,java.util.concurrent.TimeUnit.SECONDS).status());assertEquals("completed",b.get(10,java.util.concurrent.TimeUnit.SECONDS).status());
        }
        mvc.perform(put("/api/v1/orders/900/confirm-receipt").header("Authorization",userBearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("completed"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM order_status_log WHERE order_id=900 AND to_status='completed'",Integer.class));
        mvc.perform(put("/api/v1/orders/901/confirm-receipt").header("Authorization",userBearer)).andExpect(status().isBadRequest());
    }
    @Test void orderOperationsRequireActiveAuthenticatedUser() throws Exception {
        readOrder(900,100,"processing");
        mvc.perform(get("/api/v1/orders/900")).andExpect(status().isUnauthorized());
        db.update("UPDATE `user` SET status='inactive' WHERE id=100");
        mvc.perform(get("/api/v1/orders").header("Authorization",userBearer)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/scroll").header("Authorization",userBearer)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/900").header("Authorization",userBearer)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/orders/900/cancel").header("Authorization",userBearer)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/orders/900/confirm-receipt").header("Authorization",userBearer)).andExpect(status().isBadRequest());
    }

    private void addressRegions() throws Exception {
        var rows=java.util.List.of(
            new com.gas.ecommerce.modules.user.service.AddressRegionService.Region("510000",null,"\u56db\u5ddd\u7701",1,true,null,"v1"),
            new com.gas.ecommerce.modules.user.service.AddressRegionService.Region("510300","510000","\u81ea\u8d21\u5e02",2,true,null,"v1"),
            new com.gas.ecommerce.modules.user.service.AddressRegionService.Region("510302","510300","\u81ea\u6d41\u4e95\u533a",3,true,null,"v1"));
        mvc.perform(put("/api/v1/admin/address-regions").header("Authorization",bearer).contentType("application/json").content(json.writeValueAsString(rows))).andExpect(status().isOk());
    }
    private com.fasterxml.jackson.databind.node.ObjectNode assistedAddress() throws Exception {
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(addressBody("13800138000",true));
        body.put("label","\u5bb6").put("provinceCode","510000").put("cityCode","510300").put("districtCode","510302").put("latitude",29.34).put("longitude",104.78).put("allowDuplicate",false);return body;
    }
    @Test void addressAssistanceReturnsDraftsWithoutPersistenceAndEnforcesRoles() throws Exception {
        addressRegions();
        mvc.perform(post("/api/v1/users/me/addresses/parse").header("Authorization",userBearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("text","\u5f20\u4e09 13800138000 \u56db\u5ddd\u7701\u81ea\u8d21\u5e02\u81ea\u6d41\u4e95\u533a\u6c47\u4e1c\u8def1\u53f7"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.needsConfirmation").value(true)).andExpect(jsonPath("$.data.districtCode").value("510302"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_address",Integer.class));
        mvc.perform(get("/api/v1/users/me/addresses/regions").header("Authorization",userBearer).param("parentCode","510300"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("510302"));
        mvc.perform(put("/api/v1/admin/address-regions").header("Authorization",userBearer).contentType("application/json").content("[]")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/me/addresses/reverse-geocode").header("Authorization",userBearer).contentType("application/json").content("{\"latitude\":29.34,\"longitude\":104.78,\"consent\":false}")).andExpect(status().isBadRequest());
    }
    @Test void addressDuplicatesRequireExplicitChoiceAndRemainUserScoped() throws Exception {
        addressRegions();var body=assistedAddress();
        var created=mvc.perform(post("/api/v1/users/me/addresses").header("Authorization",userBearer).contentType("application/json").content(body.toString())).andExpect(status().isOk()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();
        mvc.perform(put("/api/v1/users/me/addresses/"+id).header("Authorization",userBearer).contentType("application/json").content(body.toString())).andExpect(status().isOk());
        body.put("detail","\u6c47\u4e1c\u8def  1  \u53f7");
        mvc.perform(post("/api/v1/users/me/addresses").header("Authorization",userBearer).contentType("application/json").content(body.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409002));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user_address WHERE user_id=100",Integer.class));
        mvc.perform(post("/api/v1/users/me/addresses").header("Authorization",secondUserBearer).contentType("application/json").content(body.toString())).andExpect(status().isOk());
        body.put("allowDuplicate",true);
        mvc.perform(post("/api/v1/users/me/addresses").header("Authorization",userBearer).contentType("application/json").content(body.toString())).andExpect(status().isOk());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM user_address WHERE user_id=100",Integer.class));
    }
    @Test void addressLabelsCoordinatesAndNullableUpdatesArePersistedWithValidation() throws Exception {
        addressRegions();var body=assistedAddress();
        var response=mvc.perform(post("/api/v1/users/me/addresses").header("Authorization",userBearer).contentType("application/json").content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.needsRegionCompletion").value(false)).andExpect(jsonPath("$.data.coordinateSystem").value("WGS84")).andReturn();
        long id=json.readTree(response.getResponse().getContentAsString()).at("/data/id").asLong();
        assertEquals("\u5bb6",db.queryForObject("SELECT label FROM user_address WHERE id=?",String.class,id));
        body.putNull("label").putNull("latitude").putNull("longitude");
        mvc.perform(put("/api/v1/users/me/addresses/"+id).header("Authorization",userBearer).contentType("application/json").content(body.toString())).andExpect(status().isOk());
        assertNull(db.queryForObject("SELECT latitude FROM user_address WHERE id=?",java.math.BigDecimal.class,id));
        assertNull(db.queryForObject("SELECT label FROM user_address WHERE id=?",String.class,id));
        body.put("latitude",91).put("longitude",104);
        mvc.perform(put("/api/v1/users/me/addresses/"+id).header("Authorization",userBearer).contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        body.putNull("latitude");
        mvc.perform(put("/api/v1/users/me/addresses/"+id).header("Authorization",userBearer).contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
    }
    @Test void obsoleteAddressRemainsVisibleButCheckoutRejectsItAndOrderSnapshotNeverChanges() throws Exception {
        addressRegions();var body=assistedAddress();
        var response=mvc.perform(post("/api/v1/users/me/addresses").header("Authorization",userBearer).contentType("application/json").content(body.toString())).andExpect(status().isOk()).andReturn();
        long address=json.readTree(response.getResponse().getContentAsString()).at("/data/id").asLong();
        long c=category(0,"Address snapshot"),p=product(c);setProductStatus(p,"on_sale",200);
        db.update("INSERT INTO inventory(product_id,quantity,locked_quantity) VALUES(?,5,0)",p);
        db.update("INSERT INTO cart_item(user_id,product_id,quantity,selected) VALUES(100,?,1,1)",p);
        long order=createOrder(address,"address-snapshot");
        String snapshot=db.queryForObject("SELECT address_snapshot FROM `order` WHERE id=?",String.class,order);
        db.update("UPDATE `order` SET status='paid' WHERE id=?",order);
        body.put("detail","Another building 2");
        mvc.perform(put("/api/v1/users/me/addresses/"+address).header("Authorization",userBearer).contentType("application/json").content(body.toString())).andExpect(status().isOk());
        assertEquals(snapshot,db.queryForObject("SELECT address_snapshot FROM `order` WHERE id=?",String.class,order));
        assertEquals(0,new java.math.BigDecimal("29.34").compareTo(orders.detail(100L,order).address().latitude()));
        db.update("UPDATE address_region SET active=FALSE WHERE code='510302'");
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization",userBearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].needsRegionCompletion").value(true));
        db.update("UPDATE cart_item SET is_deleted=0,selected=1,quantity=1 WHERE user_id=100 AND product_id=?",p);
        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->checkoutSettlement.settle(100L,address,null));
        assertEquals(snapshot,db.queryForObject("SELECT address_snapshot FROM `order` WHERE id=?",String.class,order));
    }
}

