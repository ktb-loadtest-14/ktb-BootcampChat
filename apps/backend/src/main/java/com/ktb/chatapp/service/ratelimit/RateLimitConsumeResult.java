package com.ktb.chatapp.service.ratelimit;

import java.time.Instant;

public record RateLimitConsumeResult(
        boolean allowed,
        int count,
        Instant expiresAt) {
}
