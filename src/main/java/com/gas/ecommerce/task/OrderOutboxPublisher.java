package com.gas.ecommerce.task;
import com.gas.ecommerce.config.RabbitMQConfig;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
/** At-least-once delivery: consumers deduplicate by messageId/orderNo + eventType. */
@Component
@ConditionalOnProperty(name="app.orders.outbox-enabled", havingValue="true", matchIfMissing=true)
public class OrderOutboxPublisher {
    private final JdbcTemplate db;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;
    @org.springframework.beans.factory.annotation.Autowired
    public OrderOutboxPublisher(JdbcTemplate db, ConnectionFactory factory, PlatformTransactionManager manager) {
        this.db = db;
        if (!(factory instanceof CachingConnectionFactory caching))
            throw new IllegalStateException("Order outbox requires publisher confirms");
        caching.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        caching.setPublisherReturns(true);
        this.rabbit = new RabbitTemplate(factory);
        this.rabbit.setMandatory(true);
        this.transactions = new TransactionTemplate(manager);
    }
    OrderOutboxPublisher(JdbcTemplate db, RabbitTemplate rabbit, PlatformTransactionManager manager) {
        this.db = db; this.rabbit = rabbit;
        this.transactions = new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString="${app.orders.outbox-delay-ms:10000}")
    public void publish() {
        for (Long id : db.queryForList("SELECT id FROM order_outbox WHERE status='pending' AND next_attempt_time<=CURRENT_TIMESTAMP ORDER BY id LIMIT 100", Long.class)) {
            transactions.executeWithoutResult(status -> {
                var rows = db.queryForList("SELECT order_no,event_type,attempts FROM order_outbox WHERE id=? AND status='pending' AND next_attempt_time<=CURRENT_TIMESTAMP FOR UPDATE", id);
                if (rows.isEmpty()) return;
                var row = rows.getFirst();
                String number = (String) row.get("order_no"), type = (String) row.get("event_type");
                CorrelationData correlation = new CorrelationData("order-event-" + id);
                try {
                    rabbit.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE, type, number, message -> {
                        message.getMessageProperties().setMessageId("order-event-" + id);
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    }, correlation);
                    var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                    if (!confirm.isAck() || correlation.getReturned() != null) throw new IllegalStateException("Broker rejected or returned order event");
                    db.update("UPDATE order_outbox SET status='sent',sent_time=CURRENT_TIMESTAMP WHERE id=?", id);
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    int attempts = ((Number) row.get("attempts")).intValue() + 1;
                    db.update("UPDATE order_outbox SET attempts=?,next_attempt_time=?,last_error=? WHERE id=?", attempts,
                            java.time.LocalDateTime.now().plusSeconds(Math.min(300L, 5L * attempts)),
                            ex.getClass().getSimpleName(), id);
                }
            });
        }
    }
}
