package com.gas.ecommerce.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * 应用配置类，负责向 Spring 注册和配置基础设施组件。
 */
public class RabbitMQConfig {
    /** 使用 Jackson 把 Java 对象序列化为 JSON 消息，接收方也应按相同消息格式解析。 */
    @Bean org.springframework.amqp.support.converter.MessageConverter rabbitMessageConverter(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter(mapper);
    }
    public static final String ORDER_EXCHANGE = "gas.order.exchange";
    public static final String ORDER_DELAY = "gas.order.timeout.delay";
    public static final String ORDER_CANCEL = "gas.order.timeout.cancel";
    @Bean DirectExchange orderExchange() { return new DirectExchange(ORDER_EXCHANGE, true, false); }
    /** 订单超时消息先在延迟队列等待，到期后由死信配置转发到取消队列。 */
    @Bean Queue orderTimeoutDelayQueue() { return QueueBuilder.durable(ORDER_DELAY).withArgument("x-message-ttl", 1_800_000).withArgument("x-dead-letter-exchange", ORDER_EXCHANGE).withArgument("x-dead-letter-routing-key", "order.cancel").build(); }
    @Bean Queue orderCancelQueue() { return QueueBuilder.durable(ORDER_CANCEL).build(); }
    /** 将 order.timeout 路由键绑定到延迟队列。 */
    @Bean Binding orderTimeoutBinding(Queue orderTimeoutDelayQueue, DirectExchange orderExchange) { return BindingBuilder.bind(orderTimeoutDelayQueue).to(orderExchange).with("order.timeout"); }
    /** 超时消息死信转发时会使用 order.cancel 路由键到达该队列。 */
    @Bean Binding orderCancelBinding(Queue orderCancelQueue, DirectExchange orderExchange) { return BindingBuilder.bind(orderCancelQueue).to(orderExchange).with("order.cancel"); }
    /** 短信发送任务队列；当前消费者需要对接具体短信服务商。 */
    @Bean Queue orderCreatedQueue() { return QueueBuilder.durable("gas.order.created").build(); }
    @Bean Queue orderCompensationQueue() { return QueueBuilder.durable("gas.order.compensate").build(); }
    @Bean Binding orderCompensationBinding(Queue orderCompensationQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCompensationQueue).to(orderExchange).with("order.compensate");
    }
    @Bean Binding orderCreatedBinding(Queue orderCreatedQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(orderExchange).with("order.created");
    }
    @Bean Queue smsQueue() { return QueueBuilder.durable("gas.notification.sms").build(); }
    /** 通用通知队列，供后续通知消费者使用。 */
    @Bean Queue notificationQueue() { return QueueBuilder.durable("gas.notification.general").build(); }
    @Bean Queue paymentStockQueue() { return QueueBuilder.durable("gas.payment.stock").build(); }
    @Bean Queue paymentPointsQueue() { return QueueBuilder.durable("gas.payment.points").build(); }
    @Bean Queue paymentNotificationQueue() { return QueueBuilder.durable("gas.payment.notification").build(); }
    @Bean Binding paymentStockBinding(Queue paymentStockQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(paymentStockQueue).to(orderExchange).with("payment.stock");
    }
    @Bean Binding paymentPointsBinding(Queue paymentPointsQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(paymentPointsQueue).to(orderExchange).with("payment.points");
    }
    @Bean Binding paymentNotificationBinding(Queue paymentNotificationQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(paymentNotificationQueue).to(orderExchange).with("payment.notification");
    }
}
