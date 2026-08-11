package com.ktb.chatapp.service.ratelimit;

import com.ktb.chatapp.model.RateLimit;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReturnDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of RateLimitStore.
 * Uses a single atomic update to avoid read-then-write round trips.
 */
@Component
@RequiredArgsConstructor
public class RateLimitMongoStore implements RateLimitStore {

    private final MongoTemplate mongoTemplate;

    @Override
    public RateLimitConsumeResult consume(String clientId, int maxRequests, Instant now, Duration window) {
        Instant nextExpiresAt = now.plus(window);
        Document isExpired = new Document("$or", List.of(
                new Document("$eq", List.of("$expiresAt", null)),
                new Document("$lte", List.of("$expiresAt", now))
        ));
        Document currentCount = new Document("$ifNull", List.of("$count", 0));

        Document nextCount = new Document("$cond", List.of(
                isExpired,
                1,
                new Document("$cond", List.of(
                        new Document("$lt", List.of(currentCount, maxRequests)),
                        new Document("$add", List.of(currentCount, 1)),
                        currentCount
                ))
        ));

        Document nextExpiry = new Document("$cond", List.of(
                isExpired,
                nextExpiresAt,
                "$expiresAt"
        ));

        Document updateStage = new Document("$set", new Document()
                .append("clientId", clientId)
                .append("count", nextCount)
                .append("expiresAt", nextExpiry));

        Document previous = mongoTemplate.getCollection(
                        mongoTemplate.getCollectionName(RateLimit.class))
                .findOneAndUpdate(
                        Filters.eq("clientId", clientId),
                        List.of(updateStage),
                        new FindOneAndUpdateOptions()
                                .upsert(true)
                                .returnDocument(ReturnDocument.BEFORE));

        if (previous == null) {
            return new RateLimitConsumeResult(true, 1, nextExpiresAt);
        }

        int previousCount = previous.getInteger("count", 0);
        java.util.Date previousExpiresAtDate = previous.getDate("expiresAt");
        Instant previousExpiresAt = previousExpiresAtDate != null
                ? previousExpiresAtDate.toInstant()
                : nextExpiresAt;

        if (!previousExpiresAt.isAfter(now)) {
            return new RateLimitConsumeResult(true, 1, nextExpiresAt);
        }

        if (previousCount < maxRequests) {
            return new RateLimitConsumeResult(true, previousCount + 1, previousExpiresAt);
        }

        return new RateLimitConsumeResult(false, previousCount, previousExpiresAt);
    }
}
