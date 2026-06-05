package com.example.consistency.coupon;

import java.util.HashMap;
import java.util.Map;

public final class InMemoryCouponRedemptionRepository implements CouponRedemptionRepository {

    private final Map<Long, String> couponStatuses = new HashMap<>();
    private final Map<String, String> idempotencyHashes = new HashMap<>();

    @Override
    public boolean tryMarkUsed(long couponIssueId) {
        String currentStatus = couponStatuses.getOrDefault(couponIssueId, "ISSUED");
        if (!"ISSUED".equals(currentStatus)) {
            return false;
        }
        couponStatuses.put(couponIssueId, "USED");
        return true;
    }

    @Override
    public boolean insertIdempotencyRecord(String idempotencyKey, String requestHash, String responseRef) {
        if (idempotencyHashes.containsKey(idempotencyKey)) {
            return false;
        }
        idempotencyHashes.put(idempotencyKey, requestHash);
        return true;
    }

    @Override
    public long replayCount(String idempotencyKey) {
        return idempotencyHashes.containsKey(idempotencyKey) ? 1 : 0;
    }

    @Override
    public long requestHashMismatchCount(String idempotencyKey, String requestHash) {
        String storedHash = idempotencyHashes.get(idempotencyKey);
        if (storedHash == null || storedHash.equals(requestHash)) {
            return 0;
        }
        return 1;
    }

    @Override
    public long usedCount(long couponIssueId) {
        return "USED".equals(couponStatuses.get(couponIssueId)) ? 1 : 0;
    }
}
