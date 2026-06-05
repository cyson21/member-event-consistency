package com.example.consistency.reward;

public record FirstLoginRewardApiRequest(
        long memberId,
        String strategy,
        int requestCount
) {
}

