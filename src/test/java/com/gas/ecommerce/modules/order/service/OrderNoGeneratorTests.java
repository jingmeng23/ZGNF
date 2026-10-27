package com.gas.ecommerce.modules.order.service;

import com.gas.ecommerce.common.utils.OrderNoGenerator;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderNoGeneratorTests {
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    static class Config {
        @Bean JdbcTemplate db() {
            JdbcDataSource source=new JdbcDataSource();
            source.setURL("jdbc:h2:mem:sequence_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            JdbcTemplate db=new JdbcTemplate(source);
            db.execute("CREATE TABLE order_number_sequence(business_day CHAR(8) PRIMARY KEY,seq_value BIGINT NOT NULL)");
            return db;
        }
        @Bean PlatformTransactionManager transactionManager(JdbcTemplate db) { return new DataSourceTransactionManager(db.getDataSource()); }
        @Bean OrderNoGenerator first(JdbcTemplate db) { return new OrderNoGenerator(db); }
        @Bean OrderNoGenerator second(JdbcTemplate db) { return new OrderNoGenerator(db); }
    }
    @Test void twoInstancesAllocateUniqueReadableNumbersConcurrently() throws Exception {
        try (var context=new AnnotationConfigApplicationContext(Config.class); var executor=Executors.newFixedThreadPool(8)) {
            var first=context.getBean("first",OrderNoGenerator.class); var second=context.getBean("second",OrderNoGenerator.class);
            List<Callable<String>> jobs=new ArrayList<>();
            for (int i=0;i<80;i++) { var generator=i%2==0?first:second; jobs.add(generator::next); }
            Set<String> numbers=new HashSet<>();
            for (Future<String> result : executor.invokeAll(jobs)) {
                String number=result.get(); assertTrue(number.matches("OD[0-9]{20}")); assertTrue(numbers.add(number));
            }
            assertEquals(80,numbers.size());
        }
    }
    @Test void businessRollbackNeverRecyclesAllocatedNumber() {
        try (var context=new AnnotationConfigApplicationContext(Config.class)) {
            var generator=context.getBean("first",OrderNoGenerator.class);
            String first=new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status->{
                String number=generator.next(); status.setRollbackOnly(); return number;
            });
            String second=generator.next();
            assertNotEquals(first,second); assertTrue(second.endsWith("000000000002"));
        }
    }
}
