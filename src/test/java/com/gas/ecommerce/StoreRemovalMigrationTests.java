package com.gas.ecommerce;

import com.gas.ecommerce.common.security.JwtTokenProvider;
import com.gas.ecommerce.modules.inventory.service.InventoryReservationService;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.workorder.dto.WorkOrderActionRequest;
import com.gas.ecommerce.modules.workorder.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
@Sql(statements="DROP ALL OBJECTS")
@Sql(scripts={"/db/migration/V1.0__init_schema.sql","/db/migration/V1.1__admin_roles.sql",
        "/db/migration/V1.2__order_reservations.sql","/db/migration/V1.3__aftersale_workflow.sql",
        "/db/migration/V1.4__operation_management.sql","/db/migration/V1.5__payment_callback.sql",
        "/db/migration/V1.6__organization_management.sql","/db/migration/V1.7__coupon_workflow.sql",
        "/db/migration/V1.8__work_order_workflow.sql","/db/migration/V1.9__inventory_management.sql",
        "/db/migration/V1.10__business_statistics.sql"})
class StoreRemovalMigrationTests {
    @Autowired JdbcTemplate db;
    @Autowired InventoryReservationService inventory;
    @Autowired WorkOrderService workOrders;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtTokenProvider tokens;
    @Autowired MockMvc mvc;

    @Test void migrationPreservesStockReservationsAndAssignedWorkWhileRemovingStoreData() throws Exception {
        db.update("INSERT INTO store(id,name) VALUES(900,'Old location')");
        db.update("INSERT INTO product(id,category_id,name,price,status) VALUES(10,1,'Product',12,'active')");
        db.update("INSERT INTO employee(id,username,password_hash,store_id) VALUES(7,'operator','unused',900)");
        db.update("INSERT INTO employee_role(employee_id,role_id) SELECT 7,id FROM role WHERE role_key='admin'");
        db.update("INSERT INTO inventory(id,product_id,store_id,quantity,locked_quantity) VALUES(20,10,null,10,2),(21,10,900,15,3)");
        db.update("INSERT INTO order_stock_reservation(order_id,inventory_id,product_id,quantity) VALUES(100,20,10,2),(101,21,10,3)");
        db.update("INSERT INTO inventory_log(inventory_id,product_id,store_id,change_quantity,change_type) VALUES(21,10,900,3,'order_lock')");
        db.update("INSERT INTO work_order(id,work_order_no,user_id,store_id,employee_id,type,status,title) VALUES(30,'MIGRATED',100,900,7,'repair','accepted','Repair')");
        db.update("INSERT INTO work_order_log(work_order_id,action,content) VALUES(30,'assigned','原门店=900, 原员工=null; 新门店=900, 新员工=7; dispatch')");
        db.update("INSERT INTO operation_log(operation,request_uri) VALUES('Store edit','/api/v1/admin/stores/900'),('Product edit','/api/v1/admin/products/10')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1.28__remove_store_management.sql"))
                .execute(db.getDataSource());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)='store'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(column_name)='store_id'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM permission WHERE permission_key LIKE 'store:%'",Integer.class));
        assertEquals(25,db.queryForObject("SELECT SUM(quantity) FROM inventory",Integer.class));
        assertEquals(5,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals("原员工=null; 新员工=7; dispatch",db.queryForObject("SELECT content FROM work_order_log WHERE work_order_id=30",String.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM operation_log",Integer.class));
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            Order cancelled=new Order(); cancelled.setId(100L); cancelled.setOrderNo("CANCEL"); inventory.release(cancelled);
            Order paid=new Order(); paid.setId(101L); paid.setOrderNo("PAID"); inventory.consume(paid);
        });
        assertEquals(10,db.queryForObject("SELECT quantity FROM inventory WHERE id=20",Integer.class));
        assertEquals(12,db.queryForObject("SELECT quantity FROM inventory WHERE id=21",Integer.class));
        assertEquals(0,db.queryForObject("SELECT SUM(locked_quantity) FROM inventory",Integer.class));
        assertEquals("processing",workOrders.start(7L,30L,new WorkOrderActionRequest("Start")).status());
        assertEquals("completed",workOrders.complete(7L,30L,new WorkOrderActionRequest("Done")).status());
        String auth="Bearer "+tokens.generateAccessToken(7L,"admin");
        for(String endpoint:java.util.List.of("/api/v1/admin/stores","/api/v1/admin/statistics/stores"))
            mvc.perform(get(endpoint).header("Authorization",auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/work-orders/stores").header("Authorization","Bearer "+tokens.generateAccessToken(100L,"user")))
                .andExpect(status().isBadRequest()); // No selector route; "stores" is not a numeric work-order ID.
    }
}
