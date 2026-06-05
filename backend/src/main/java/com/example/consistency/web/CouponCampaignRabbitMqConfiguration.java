package com.example.consistency.web;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "member-event-consistency.sql.enabled", havingValue = "true", matchIfMissing = true)
public class CouponCampaignRabbitMqConfiguration {

    public static final String COMMAND_QUEUE = "coupon-campaign-issue.commands";
    public static final String DLQ_QUEUE = "coupon-campaign-issue.dlq";

    @Bean
    public Queue couponCampaignIssueCommandQueue() {
        return QueueBuilder.durable(COMMAND_QUEUE).build();
    }

    @Bean
    public Queue couponCampaignIssueDlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public CouponCampaignRabbitMqRunTracker couponCampaignRabbitMqRunTracker() {
        return new CouponCampaignRabbitMqRunTracker();
    }
}
