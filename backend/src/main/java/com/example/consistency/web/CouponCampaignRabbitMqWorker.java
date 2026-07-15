package com.example.consistency.web;

import com.example.consistency.coupon.CouponCampaignCommand;
import com.example.consistency.coupon.CouponCampaignDecision;
import com.example.consistency.coupon.CouponCampaignService;
import com.example.consistency.scenario.StrategyType;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "member-event-consistency.sql.enabled", havingValue = "true", matchIfMissing = true)
public final class CouponCampaignRabbitMqWorker {

    private final CouponCampaignService couponCampaignService;
    private final CouponCampaignRabbitMqPublisher publisher;
    private final CouponCampaignRabbitMqRunTracker tracker;

    public CouponCampaignRabbitMqWorker(
            CouponCampaignService couponCampaignService,
            CouponCampaignRabbitMqPublisher publisher,
            CouponCampaignRabbitMqRunTracker tracker
    ) {
        this.couponCampaignService = couponCampaignService;
        this.publisher = publisher;
        this.tracker = tracker;
    }

    @RabbitListener(queues = CouponCampaignRabbitMqConfiguration.COMMAND_QUEUE, concurrency = "1")
    public void handle(CouponCampaignRabbitMqCommand command) {
        if (!tracker.isActive(command.operationId())) {
            return;
        }
        if (command.transientFailureBudget() > 0) {
            publisher.publish(command.retriedCopy());
            tracker.recordRetried(command.operationId(), command.messageId(), command.campaignId(), command.attempt() + 1, command.lagMs());
            return;
        }
        if (command.dlq()) {
            publisher.publishDlq(command);
            tracker.recordDlq(command.operationId(), command.messageId(), command.campaignId(), command.attempt(), command.lagMs());
            return;
        }
        CouponCampaignDecision decision = couponCampaignService.issue(new CouponCampaignCommand(
                command.campaignId(),
                command.memberId(),
                StrategyType.RABBITMQ_DB_GUARD,
                command.idempotencyKey()
        ));
        tracker.recordCompleted(command, decision);
    }
}
