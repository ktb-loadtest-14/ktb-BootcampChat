package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.repository.RoomRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Service;

/**
 * 채팅방 참여자 상태를 Redis Set으로 관리한다.
 *
 * <p>쓰기 시에는 MongoDB와 Redis를 함께 갱신하고, 읽기 시에는 Redis 우선 조회 후 cache miss인 경우
 * MongoDB 값을 Redis에 적재한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomParticipantPresenceService {

    private final RoomRepository roomRepository;
    private final StringRedisTemplate redisTemplate;

    public Set<String> getParticipantIds(Room room) {
        if (room == null || room.getId() == null) {
            return Set.of();
        }

        String key = participantKey(room.getId());

        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                Set<String> members = redisTemplate.opsForSet().members(key);
                return members != null ? new LinkedHashSet<>(members) : Set.of();
            }

            Set<String> participantIds = copyParticipantIds(room);
            prime(room.getId(), participantIds);
            return participantIds;
        } catch (RuntimeException e) {
            log.warn("Redis participant lookup failed for room {}. Falling back to Mongo snapshot.", room.getId(), e);
            return copyParticipantIds(room);
        }
    }

    public void initialize(Room room) {
        if (room == null || room.getId() == null) {
            return;
        }
        try {
            prime(room.getId(), copyParticipantIds(room));
        } catch (RuntimeException e) {
            log.warn("Failed to initialize Redis participants for room {}", room.getId(), e);
        }
    }

    public void addParticipant(String roomId, String userId) {
        roomRepository.addParticipant(roomId, userId);
        try {
            redisTemplate.opsForSet().add(participantKey(roomId), userId);
        } catch (RuntimeException e) {
            log.warn("Failed to mirror participant add into Redis. roomId={}, userId={}", roomId, userId, e);
        }
    }

    public void removeParticipant(String roomId, String userId) {
        roomRepository.removeParticipant(roomId, userId);
        try {
            String key = participantKey(roomId);
            SetOperations<String, String> ops = redisTemplate.opsForSet();
            ops.remove(key, userId);
            Long remaining = ops.size(key);
            if (remaining != null && remaining == 0L) {
                redisTemplate.delete(key);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to mirror participant removal into Redis. roomId={}, userId={}", roomId, userId, e);
        }
    }

    private void prime(String roomId, Set<String> participantIds) {
        String key = participantKey(roomId);
        redisTemplate.delete(key);
        if (!participantIds.isEmpty()) {
            redisTemplate.opsForSet().add(key, participantIds.toArray(String[]::new));
        }
    }

    private Set<String> copyParticipantIds(Room room) {
        return room.getParticipantIds() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(room.getParticipantIds());
    }

    private String participantKey(String roomId) {
        return "room:" + roomId + ":members";
    }
}
