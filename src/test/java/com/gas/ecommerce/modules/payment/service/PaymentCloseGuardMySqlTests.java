package com.gas.ecommerce.modules.payment.service;

import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import java.sql.*;
import java.util.concurrent.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real InnoDB RR regression. Use only a disposable database named order_p1_test_*.
 * ORDER_MYSQL_TEST_URL / ORDER_MYSQL_TEST_USER / ORDER_MYSQL_TEST_PASSWORD enable it. */
@EnabledIfEnvironmentVariable(named="ORDER_MYSQL_TEST_URL",matches=".+")
class PaymentCloseGuardMySqlTests {
    @Test void committedPaymentIsDetectedDespiteAnEarlierRepeatableReadSnapshot() throws Exception {
        var source=new DriverManagerDataSource(System.getenv("ORDER_MYSQL_TEST_URL"),
                System.getenv("ORDER_MYSQL_TEST_USER"),System.getenv("ORDER_MYSQL_TEST_PASSWORD"));
        try(var setup=source.getConnection();var sql=setup.createStatement()) {
            assertTrue(setup.getCatalog().startsWith("order_p1_test_"),"Disposable test schema required");
            sql.execute("CREATE TABLE IF NOT EXISTS `order`(id BIGINT PRIMARY KEY,status VARCHAR(30)) ENGINE=InnoDB");
            sql.execute("CREATE TABLE IF NOT EXISTS payment(id BIGINT PRIMARY KEY,order_id BIGINT,status VARCHAR(30),is_deleted INT DEFAULT 0,INDEX(order_id)) ENGINE=InnoDB");
            sql.execute("DELETE FROM payment");sql.execute("DELETE FROM `order`");
            sql.execute("INSERT INTO `order` VALUES(500,'pending_payment')");
        }
        var configuration=new Configuration(new Environment("mysql-rr",new JdbcTransactionFactory(),source));
        configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(PaymentMapper.class);
        var factory=new SqlSessionFactoryBuilder().build(configuration);
        var snapshotReady=new CountDownLatch(1);var writerReady=new CountDownLatch(1);
        var readerWantsLock=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var reader=pool.submit(()->{
                try(var connection=source.getConnection()) {
                    connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
                    try(var sql=connection.createStatement()) {
                        sql.execute("SET innodb_lock_wait_timeout=5");
                        try(var rows=sql.executeQuery("SELECT COUNT(*) FROM payment")) {rows.next();assertEquals(0,rows.getInt(1));}
                        snapshotReady.countDown();assertTrue(writerReady.await(10,TimeUnit.SECONDS));
                        readerWantsLock.countDown();
                        try(var rows=sql.executeQuery("SELECT id FROM `order` WHERE id=500 FOR UPDATE")) {assertTrue(rows.next());}
                        // Demonstrate that the original ordinary enumeration really would miss this attempt.
                        try(var rows=sql.executeQuery("SELECT COUNT(*) FROM payment")) {rows.next();assertEquals(0,rows.getInt(1));}
                    }
                    try(var session=factory.openSession(connection)) {
                        var gateway=mock(PaymentProviderGateway.class);
                        when(gateway.close(any())).thenReturn(PaymentProviderGateway.Snapshot.unknown());
                        var guard=new PaymentCloseGuard(session.getMapper(PaymentMapper.class),gateway,mock(PaymentOutcomeHandler.class));
                        var order=new Order();order.setId(500L);
                        assertThrows(com.gas.ecommerce.common.exception.BusinessException.class,()->guard.mayClose(order));
                        verify(gateway).close(argThat(p->p.getId()==600L));
                        session.rollback();
                    }
                }
                return null;
            });
            var writer=pool.submit(()->{
                assertTrue(snapshotReady.await(10,TimeUnit.SECONDS));
                try(var connection=source.getConnection();var sql=connection.createStatement()) {
                    connection.setAutoCommit(false);
                    try(var rows=sql.executeQuery("SELECT id FROM `order` WHERE id=500 FOR UPDATE")) {assertTrue(rows.next());}
                    sql.execute("INSERT INTO payment(id,order_id,status) VALUES(600,500,'pending')");
                    writerReady.countDown();assertTrue(readerWantsLock.await(10,TimeUnit.SECONDS));connection.commit();
                }
                return null;
            });
            reader.get(30,TimeUnit.SECONDS);writer.get(30,TimeUnit.SECONDS);
        }
    }
}
