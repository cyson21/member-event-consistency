package com.example.consistency.web;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "member-event-consistency.sql.enabled", havingValue = "true", matchIfMissing = true)
public final class CouponCampaignRabbitMqPublisher {

    private final RabbitTemplate rabbitTemplate;

    public CouponCampaignRabbitMqPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(CouponCampaignRabbitMqCommand command) {
        rabbitTemplate.convertAndSend(CouponCampaignRabbitMqConfiguration.COMMAND_QUEUE, command);
    }

    public void publishDlq(CouponCampaignRabbitMqCommand command) {
        rabbitTemplate.convertAndSend(CouponCampaignRabbitMqConfiguration.DLQ_QUEUE, command);
    }
}
