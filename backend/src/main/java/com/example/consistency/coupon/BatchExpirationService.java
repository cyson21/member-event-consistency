package com.example.consistency.coupon;

public final class BatchExpirationService {

    private final BatchExpirationRepository repository;

    public BatchExpirationService(BatchExpirationRepository repository) {
        this.repository = repository;
    }

    public BatchExpirationDecision use(long couponIssueId) {
        boolean used = repository.tryMarkUsed(couponIssueId);
        return new BatchExpirationDecision(
                used,
                false,
                !used,
                used ? "" : "use rejected because coupon already expired"
        );
    }

    public BatchExpirationDecision expire(long couponIssueId) {
        boolean expired = repository.tryMarkExpired(couponIssueId);
        return new BatchExpirationDecision(
                false,
                expired,
                !expired,
                expired ? "" : "expiration rejected because coupon already used"
        );
    }
}
