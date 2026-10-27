package com.gas.ecommerce.task;

import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderOutboxPublisherTests {
    JdbcTemplate db;
    RabbitTemplate rabbit;
    OrderOutboxPublisher publisher;
    @BeforeEach void setup() {
        JdbcDataSource source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:outbox_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(source);
        db.execute("CREATE TABLE order_outbox(id BIGINT PRIMARY KEY, order_no VARCHAR(64), event_type VARCHAR(32), status VARCHAR(16), attempts INT, next_attempt_time TIMESTAMP, sent_time TIMESTAMP, last_error VARCHAR(255))");
        db.update("INSERT INTO order_outbox VALUES(1,'ORDER-1','order.created','pending',0,CURRENT_TIMESTAMP,NULL,NULL)");
        rabbit=mock(RabbitTemplate.class);
        publisher=new OrderOutboxPublisher(db,rabbit,new DataSourceTransactionManager(source));
    }
    void confirm(boolean ack) {
        doAnswer(invocation->{
            CorrelationData correlation=invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack,ack?null:"rejected"));
            return null;
        }).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
    }
    @Test void brokerAckMarksSentAndDoesNotPublishAgain() {
        confirm(true); publisher.publish(); publisher.publish();
        assertEquals("sent",db.queryForObject("SELECT status FROM order_outbox",String.class));
        assertNotNull(db.queryForObject("SELECT sent_time FROM order_outbox",java.sql.Timestamp.class));
        verify(rabbit,times(1)).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
    }
    @Test void brokerNackIsRetriedWithTheSameMessageId() {
        confirm(false); publisher.publish();
        assertEquals("pending",db.queryForObject("SELECT status FROM order_outbox",String.class));
        assertEquals(1,db.queryForObject("SELECT attempts FROM order_outbox",Integer.class));
        publisher.publish(); // Backoff prevents immediate resending.
        verify(rabbit,times(1)).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
        db.update("UPDATE order_outbox SET next_attempt_time=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        confirm(true); publisher.publish();
        assertEquals("sent",db.queryForObject("SELECT status FROM order_outbox",String.class));
        var ids=org.mockito.ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbit,times(2)).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),ids.capture());
        assertEquals(ids.getAllValues().getFirst().getId(),ids.getAllValues().getLast().getId());
    }
    @Test void connectionFailureLeavesMessagePendingForCompensation() {
        doThrow(new AmqpException("offline")).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
        publisher.publish();
        assertEquals("pending",db.queryForObject("SELECT status FROM order_outbox",String.class));
        assertEquals(1,db.queryForObject("SELECT attempts FROM order_outbox",Integer.class));
        assertNull(db.queryForObject("SELECT sent_time FROM order_outbox",java.sql.Timestamp.class));
    }
}
