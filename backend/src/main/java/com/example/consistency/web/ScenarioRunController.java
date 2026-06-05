package com.example.consistency.web;

import com.example.consistency.api.ScenarioApiRouteResponse;
import com.example.consistency.api.ScenarioApiRouter;
import com.example.consistency.coupon.BatchExpirationApiHandler;
import com.example.consistency.coupon.BatchExpirationApiRequest;
import com.example.consistency.coupon.BatchExpirationApiResponse;
import com.example.consistency.coupon.CouponRedemptionApiHandler;
import com.example.consistency.coupon.CouponRedemptionApiRequest;
import com.example.consistency.coupon.CouponRedemptionApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/scenarios")
@ConditionalOnProperty(name = "member-event-consistency.sql.enabled", havingValue = "true", matchIfMissing = true)
public final class ScenarioRunController {

    private final ScenarioApiRouter router;
    private final CouponRedemptionApiHandler couponRedemptionHandler;
    private final BatchExpirationApiHandler batchExpirationHandler;

    public ScenarioRunController(
            ScenarioApiRouter router,
            CouponRedemptionApiHandler couponRedemptionHandler,
            BatchExpirationApiHandler batchExpirationHandler
    ) {
        this.router = router;
        this.couponRedemptionHandler = couponRedemptionHandler;
        this.batchExpirationHandler = batchExpirationHandler;
    }

    @PostMapping(value = "/first-login-reward/runs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> firstLoginReward(@RequestBody FirstLoginRewardRunRequest request) {
        return toResponse(router.handle(
                "POST",
                "/api/scenarios/first-login-reward/runs",
                Map.of(
                        "memberId", String.valueOf(request.memberId()),
                        "strategy", text(request.strategy()),
                        "requestCount", String.valueOf(request.requestCount())
                )
        ));
    }

    @PostMapping(value = "/coupon-campaign-issue/runs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> couponCampaignIssue(@RequestBody CouponCampaignIssueRunRequest request) {
        return toResponse(router.handle(
                "POST",
                "/api/scenarios/coupon-campaign-issue/runs",
                Map.of(
                        "campaignId", String.valueOf(request.campaignId()),
                        "strategy", text(request.strategy()),
                        "capacity", String.valueOf(request.capacity()),
                        "requestCount", String.valueOf(request.requestCount()),
                        "transientRetryCount", String.valueOf(request.transientRetryCount()),
                        "dlqCount", String.valueOf(request.dlqCount())
                )
        ));
    }

    @PostMapping(value = "/point-spend/runs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> pointSpend(@RequestBody PointSpendRunRequest request) {
        return toResponse(router.handle(
                "POST",
                "/api/scenarios/point-spend/runs",
                Map.of(
                        "memberId", String.valueOf(request.memberId()),
                        "strategy", text(request.strategy()),
                        "initialBalance", String.valueOf(request.initialBalance()),
                        "spendAmount", String.valueOf(request.spendAmount()),
                        "requestCount", String.valueOf(request.requestCount()),
                        "idempotencyKey", text(request.idempotencyKey())
                )
        ));
    }

    @PostMapping(value = "/coupon-redemption/runs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CouponRedemptionApiResponse> couponRedemption(@RequestBody CouponRedemptionRunRequest request) {
        CouponRedemptionApiResponse response = couponRedemptionHandler.handle(new CouponRedemptionApiRequest(
                request.couponIssueId(),
                text(request.strategy()),
                request.requestCount(),
                text(request.idempotencyKey()),
                text(request.firstRequestHash()),
                text(request.retryRequestHash())
        ));
        return ResponseEntity
                .status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @PostMapping(value = "/batch-expiration/runs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchExpirationApiResponse> batchExpiration(@RequestBody BatchExpirationRunRequest request) {
        BatchExpirationApiResponse response = batchExpirationHandler.handle(new BatchExpirationApiRequest(
                request.couponIssueId(),
                text(request.strategy()),
                text(request.winner())
        ));
        return ResponseEntity
                .status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    private ResponseEntity<String> toResponse(ScenarioApiRouteResponse response) {
        return ResponseEntity
                .status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    public record FirstLoginRewardRunRequest(
            long memberId,
            String strategy,
            int requestCount
    ) {
    }

    public record CouponCampaignIssueRunRequest(
            long campaignId,
            String strategy,
            int capacity,
            int requestCount,
            int transientRetryCount,
            int dlqCount
    ) {
    }

    public record PointSpendRunRequest(
            long memberId,
            String strategy,
            long initialBalance,
            long spendAmount,
            int requestCount,
            String idempotencyKey
    ) {
    }

    public record CouponRedemptionRunRequest(
            long couponIssueId,
            String strategy,
            int requestCount,
            String idempotencyKey,
            String firstRequestHash,
            String retryRequestHash
    ) {
    }

    public record BatchExpirationRunRequest(
            long couponIssueId,
            String strategy,
            String winner
    ) {
    }
}
