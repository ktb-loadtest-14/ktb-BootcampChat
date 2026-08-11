package com.ktb.chatapp.service;

import com.ktb.chatapp.service.ratelimit.RateLimitConsumeResult;
import com.ktb.chatapp.service.ratelimit.RateLimitStore;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimitStore rateLimitStore;
    
    @Transactional
    public RateLimitCheckResult checkRateLimit(String clientId, int maxRequests, Duration window) {
        Duration requestedWindow = window != null ? window : Duration.ofSeconds(1);
        Duration effectiveWindow = requestedWindow.isPositive()
                ? requestedWindow
                : Duration.ofSeconds(1);
        long windowSeconds = effectiveWindow.getSeconds();
        Instant now = Instant.now();
        long nowEpochSeconds = now.getEpochSecond();

        try {
            RateLimitConsumeResult consumeResult =
                    rateLimitStore.consume(clientId, maxRequests, now, effectiveWindow);

            long resetEpochSeconds = consumeResult.expiresAt().getEpochSecond();
            long retryAfterSeconds = Math.max(1L, resetEpochSeconds - nowEpochSeconds);

            if (!consumeResult.allowed()) {
                return RateLimitCheckResult.rejected(
                        maxRequests, windowSeconds, resetEpochSeconds, retryAfterSeconds);
            }

            int remaining = Math.max(0, maxRequests - consumeResult.count());

            return RateLimitCheckResult.allowed(
                    maxRequests, remaining, windowSeconds, resetEpochSeconds, retryAfterSeconds);
        } catch (Exception e) {
            log.error("Rate limit check failed for client: {}", clientId, e);
            long resetEpochSeconds = nowEpochSeconds + windowSeconds;
            return RateLimitCheckResult.allowed(
                    maxRequests, maxRequests, windowSeconds, resetEpochSeconds, windowSeconds);
        }
    }
    
}
