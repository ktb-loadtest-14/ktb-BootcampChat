package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.repository.MessageRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * 채팅방 목록에 노출하는 "최근 메시지 수"의 집계 창을 한곳에서 관리한다.
 */
@Component
@RequiredArgsConstructor
public class RecentMessageCounter {

    static final Duration RECENT_WINDOW = Duration.ofMinutes(30);

    private final MessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;

    public int countRecentMessages(String roomId) {
        LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
        return (int) messageRepository.countRecentMessagesByRoomId(roomId, since);
    }

    /**
     * 여러 채팅방의 최근 메시지 수를 단일 aggregation으로 조회한다.
     */
    public Map<String, Integer> countRecentMessagesByRoomIds(Collection<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Map.of();
        }

        LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
        TypedAggregation<Message> aggregation = Aggregation.newAggregation(
                Message.class,
                Aggregation.match(Criteria.where("roomId").in(roomIds)
                        .and("timestamp").gte(since)),
                Aggregation.group("roomId").count().as("count")
        );

        Map<String, Integer> counts = new HashMap<>();
        for (Document result : mongoTemplate.aggregate(aggregation, Document.class).getMappedResults()) {
            Object roomId = result.get("_id");
            Object count = result.get("count");
            if (roomId != null && count instanceof Number number) {
                counts.put(roomId.toString(), number.intValue());
            }
        }
        return counts;
    }
}
