package com.ktb.chatapp.service.ratelimit;

import java.time.Duration;
import java.time.Instant;

/**
 * Data store interface for rate limit storage.
 * Provides operations for storing and retrieving rate limit data.
 */
public interface RateLimitStore {

    /**
     * Consume one request from a client's rate limit window atomically.
     *
     * @param clientId the client identifier
     * @param maxRequests allowed request count in the window
     * @param now current time
     * @param window limit window
     * @return result containing whether the request was accepted and the updated window state
     */
    RateLimitConsumeResult consume(String clientId, int maxRequests, Instant now, Duration window);
}
