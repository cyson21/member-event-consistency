package com.example.consistency.coupon;

public record BatchExpirationDecision(
        boolean used,
        boolean expired,
        boolean rejected,
        String rejectionReason
) {
}
