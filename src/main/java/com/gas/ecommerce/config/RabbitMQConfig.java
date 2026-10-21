package com.gas.ecommerce.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Bean org.springframework.amqp.support.converter.MessageConverter rabbitMessageConverter(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter(mapper);
    }
    public static final String ORDER_EXCHANGE = "gas.order.exchange";
    public static final String ORDER_DELAY = "gas.order.timeout.delay";
    public static final String ORDER_CANCEL = "gas.order.timeout.cancel";
    @Bean DirectExchange orderExchange() { return new DirectExchange(ORDER_EXCHANGE, true, false); }
    @Bean Queue orderTimeoutDelayQueue() { return QueueBuilder.durable(ORDER_DELAY).withArgument("x-message-ttl", 1_800_000).withArgument("x-dead-letter-exchange", ORDER_EXCHANGE).withArgument("x-dead-letter-routing-key", "order.cancel").build(); }
    @Bean Queue orderCancelQueue() { return QueueBuilder.durable(ORDER_CANCEL).build(); }
    @Bean Binding orderTimeoutBinding(Queue orderTimeoutDelayQueue, DirectExchange orderExchange) { return BindingBuilder.bind(orderTimeoutDelayQueue).to(orderExchange).with("order.timeout"); }
    @Bean Binding orderCancelBinding(Queue orderCancelQueue, DirectExchange orderExchange) { return BindingBuilder.bind(orderCancelQueue).to(orderExchange).with("order.cancel"); }
    @Bean Queue smsQueue() { return QueueBuilder.durable("gas.notification.sms").build(); }
    @Bean Queue notificationQueue() { return QueueBuilder.durable("gas.notification.general").build(); }
}
