package com.inkwell.newsletter.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String POST_EXCHANGE = "post.exchange";
    public static final String POST_PUBLISHED_QUEUE = "post.published.queue";
    public static final String POST_PUBLISHED_ROUTING_KEY = "post.published.key";

    public static final String NEWSLETTER_EXCHANGE = "newsletter.exchange";
    public static final String NEWSLETTER_QUEUE = "newsletter.queue";
    public static final String NEWSLETTER_CONFIRMATION_QUEUE = "newsletter.confirmation.queue";
    public static final String NEWSLETTER_POST_QUEUE = "newsletter.post.queue";

    public static final String NEWSLETTER_SEND_KEY = "newsletter.send";
    public static final String NEWSLETTER_CONFIRM_KEY = "newsletter.confirm";
    public static final String NEWSLETTER_POST_KEY = "newsletter.post";

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

    public static final String NEWSLETTER_DLX = "newsletter.dlx";
    public static final String NEWSLETTER_DLQ = "newsletter.dlq";

    @Bean
    public TopicExchange newsletterExchange() {
        return new TopicExchange(NEWSLETTER_EXCHANGE);
    }

    @Bean
    public TopicExchange newsletterDLX() {
        return new TopicExchange(NEWSLETTER_DLX);
    }

    @Bean
    public Queue newsletterDLQ() {
        return new Queue(NEWSLETTER_DLQ);
    }

    @Bean
    public Binding newsletterDLXBinding() {
        return BindingBuilder.bind(newsletterDLQ()).to(newsletterDLX()).with("#");
    }

    @Bean
    public Queue newsletterQueue() {
        return QueueBuilder.durable(NEWSLETTER_QUEUE)
                .withArgument("x-dead-letter-exchange", NEWSLETTER_DLX)
                .build();
    }

    @Bean
    public Queue newsletterConfirmationQueue() {
        return QueueBuilder.durable(NEWSLETTER_CONFIRMATION_QUEUE)
                .withArgument("x-dead-letter-exchange", NEWSLETTER_DLX)
                .build();
    }

    @Bean
    public Queue newsletterPostQueue() {
        return QueueBuilder.durable(NEWSLETTER_POST_QUEUE)
                .withArgument("x-dead-letter-exchange", NEWSLETTER_DLX)
                .build();
    }

    @Bean
    public Binding newsletterSendBinding() {
        return BindingBuilder.bind(newsletterQueue()).to(newsletterExchange()).with(NEWSLETTER_SEND_KEY);
    }

    @Bean
    public Binding newsletterConfirmBinding() {
        return BindingBuilder.bind(newsletterConfirmationQueue()).to(newsletterExchange()).with(NEWSLETTER_CONFIRM_KEY);
    }

    @Bean
    public Binding newsletterPostBinding() {
        return BindingBuilder.bind(newsletterPostQueue()).to(newsletterExchange()).with(NEWSLETTER_POST_KEY);
    }

    @Bean
    public MessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }
}
