package com.ktb.chatapp.service.session;

import com.ktb.chatapp.model.Session;
import com.ktb.chatapp.repository.SessionRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of SessionStore.
 * Uses SessionRepository for persistence.
 */
@Component
@RequiredArgsConstructor
public class SessionMongoStore implements SessionStore {
    
    private final SessionRepository sessionRepository;
    private final MongoTemplate mongoTemplate;
    
    @Override
    public Optional<Session> findByUserId(String userId) {
        return sessionRepository.findByUserId(userId);
    }
    
    @Override
    public Session save(Session session) {
        return sessionRepository.save(session);
    }

    @Override
    public void touch(String userId, String sessionId, long lastActivity, Instant expiresAt) {
        Query query = Query.query(
                Criteria.where("userId").is(userId)
                        .and("sessionId").is(sessionId)
        );
        Update update = new Update()
                .max("lastActivity", lastActivity)
                .max("expiresAt", expiresAt);
        mongoTemplate.updateFirst(query, update, Session.class);
    }
    
    @Override
    public void delete(String userId, String sessionId) {
        Session session = sessionRepository.findByUserId(userId).orElse(null);
        if (session != null && sessionId.equals(session.getSessionId())) {
            sessionRepository.delete(session);
        }
    }
    
    @Override
    public void deleteAll(String userId) {
        sessionRepository.deleteByUserId(userId);
    }
}
