package com.inkwell.post.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String POST_EXCHANGE = "post.exchange";
    public static final String POST_PUBLISHED_QUEUE = "post.published.queue";
    public static final String POST_PUBLISHED_ROUTING_KEY = "post.published.key";

    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.key";
    
    public static final String NEWSLETTER_EXCHANGE = "newsletter.exchange";
    public static final String NEWSLETTER_POST_KEY = "newsletter.post";
    
    public static final String PAYMENT_EXCHANGE = "payment_exchange";
    public static final String PAYMENT_SUCCESS_QUEUE = "post.payment.success.queue";
    public static final String PAYMENT_SUCCESS_ROUTING_KEY = "payment.success";

    @Bean
    public Queue postPublishedQueue() {
        return new Queue(POST_PUBLISHED_QUEUE);
    }

    @Bean
    public TopicExchange postExchange() {
        return new TopicExchange(POST_EXCHANGE);
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(postPublishedQueue()).to(postExchange()).with(POST_PUBLISHED_ROUTING_KEY);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public Queue paymentSuccessQueue() {
        return new Queue(PAYMENT_SUCCESS_QUEUE);
    }

    @Bean
    public Binding paymentBinding() {
        return BindingBuilder.bind(paymentSuccessQueue())
                .to(paymentExchange())
                .with(PAYMENT_SUCCESS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public AmqpTemplate template(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter());
        return rabbitTemplate;
    }
}
