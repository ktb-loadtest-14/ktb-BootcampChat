package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * 메시지 리액션을 문서 전체 save 없이 MongoDB 원자 연산으로 갱신한다.
 */
@Service
@RequiredArgsConstructor
public class MessageReactionService {

    private final MongoTemplate mongoTemplate;

    public Optional<Message> updateReaction(
            String messageId,
            String reaction,
            String userId,
            String type) {
        validate(messageId, reaction, userId);

        String reactionPath = "reactions." + reaction;
        Update update = switch (type) {
            case "add" -> new Update().addToSet(reactionPath, userId);
            case "remove" -> new Update().pull(reactionPath, userId);
            case null, default -> throw new IllegalArgumentException("지원하지 않는 리액션 타입입니다.");
        };

        Message updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(messageId)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Message.class);
        return Optional.ofNullable(updated);
    }

    private void validate(String messageId, String reaction, String userId) {
        if (messageId == null || messageId.isBlank()
                || reaction == null || reaction.isBlank()
                || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("리액션 요청 값이 올바르지 않습니다.");
        }

        // 리액션은 MongoDB 필드 경로로 사용되므로 경로 조작 문자를 허용하지 않는다.
        if (reaction.contains(".") || reaction.startsWith("$")) {
            throw new IllegalArgumentException("지원하지 않는 리액션입니다.");
        }
    }
}
