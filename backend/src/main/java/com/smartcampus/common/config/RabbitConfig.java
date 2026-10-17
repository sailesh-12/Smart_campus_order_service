package com.smartcampus.common.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration active when app.events.transport=rabbit.
 * Declares the fanout exchange 'order.events' and an anonymous queue bound to it
 * so that each instance receives all events for local SSE propagation.
 */
@Configuration
@ConditionalOnProperty(name = "app.events.transport", havingValue = "rabbit")
public class RabbitConfig {

    public static final String EXCHANGE_ORDER_EVENTS = "order.events";

    @Bean
    public FanoutExchange orderEventsExchange() {
        return new FanoutExchange(EXCHANGE_ORDER_EVENTS);
    }

    @Bean
    public Queue anonymousEventQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding binding(FanoutExchange orderEventsExchange, Queue anonymousEventQueue) {
        return BindingBuilder.bind(anonymousEventQueue).to(orderEventsExchange);
    }
}
