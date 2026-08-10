package com.ktb.chatapp.service;

import com.ktb.chatapp.service.ratelimit.RateLimitConsumeResult;
import com.ktb.chatapp.service.ratelimit.RateLimitStore;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static java.net.InetAddress.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimitStore rateLimitStore;
    @Value("${HOSTNAME:''}")
    private String hostName;
    
    @PostConstruct
    public void init() {
        if (!hostName.isEmpty()) {
            return;
        }
        hostName = generateHostname();
    }
    
    private String generateHostname() {
        try {
            return getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
    }
    
    
    @Transactional
    public RateLimitCheckResult checkRateLimit(String _clientId, int maxRequests, Duration window) {
        String actualClientId = hostName + ":" + _clientId;
        Duration effectiveWindow = window != null ? window : Duration.ofSeconds(1);
        long windowSeconds = Math.max(1L, effectiveWindow.getSeconds());
        Instant now = Instant.now();
        long nowEpochSeconds = now.getEpochSecond();

        try {
            RateLimitConsumeResult consumeResult =
                    rateLimitStore.consume(actualClientId, maxRequests, now, effectiveWindow);

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
            log.error("Rate limit check failed for client: {}", actualClientId, e);
            long resetEpochSeconds = nowEpochSeconds + windowSeconds;
            return RateLimitCheckResult.allowed(
                    maxRequests, maxRequests, windowSeconds, resetEpochSeconds, windowSeconds);
        }
    }
    
}
