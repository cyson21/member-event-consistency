package com.example.consistency.coupon;

import java.util.HashMap;
import java.util.Map;

public final class InMemoryBatchExpirationRepository implements BatchExpirationRepository {

    private final Map<Long, String> couponStatuses = new HashMap<>();

    @Override
    public boolean tryMarkUsed(long couponIssueId) {
        return tryMarkTerminal(couponIssueId, "USED");
    }

    @Override
    public boolean tryMarkExpired(long couponIssueId) {
        return tryMarkTerminal(couponIssueId, "EXPIRED");
    }

    @Override
    public long usedCount(long couponIssueId) {
        return "USED".equals(couponStatuses.get(couponIssueId)) ? 1 : 0;
    }

    @Override
    public long expiredCount(long couponIssueId) {
        return "EXPIRED".equals(couponStatuses.get(couponIssueId)) ? 1 : 0;
    }

    private boolean tryMarkTerminal(long couponIssueId, String terminalStatus) {
        String currentStatus = couponStatuses.getOrDefault(couponIssueId, "ISSUED");
        if (!"ISSUED".equals(currentStatus)) {
            return false;
        }
        couponStatuses.put(couponIssueId, terminalStatus);
        return true;
    }
}
