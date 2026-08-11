package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.*;
import com.ktb.chatapp.event.RoomCreatedEvent;
import com.ktb.chatapp.event.RoomUpdatedEvent;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RecentMessageCounter recentMessageCounter;
    private final RoomParticipantPresenceService roomParticipantPresenceService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public RoomsResponse getAllRooms(String name) {
        try {
            List<Room> rooms = roomRepository.findAll();
            Map<String, User> usersById = loadUsersById(rooms);
            Map<String, Integer> recentMessageCounts = loadRecentMessageCounts(rooms);

            // 방·사용자·최근 메시지 수를 각각 일괄 조회한 뒤 메모리에서 응답을 조립한다.
            List<RoomResponse> roomResponses = rooms.stream()
                .map(room -> safeMapToRoomResponse(
                        room,
                        name,
                        usersById,
                        recentMessageCounts.getOrDefault(room.getId(), 0)))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(
                    RoomResponse::getCreatedAtDateTime,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

            PageMetadata metadata = PageMetadata.builder()
                .total(roomResponses.size())
                .page(0)
                .pageSize(roomResponses.size())
                .totalPages(1)
                .hasMore(false)
                .currentCount(roomResponses.size())
                .build();

            return RoomsResponse.builder()
                .success(true)
                .data(roomResponses)
                .metadata(metadata)
                .build();

        } catch (Exception e) {
            log.error("방 목록 조회 에러", e);
            return RoomsResponse.builder()
                .success(false)
                .data(List.of())
                .build();
        }
    }

    public HealthResponse getHealthStatus() {
        try {
            long startTime = System.currentTimeMillis();

            // MongoDB 연결 상태 확인
            boolean isMongoConnected = false;
            long latency = 0;

            try {
                // 간단한 쿼리로 연결 상태 및 지연 시간 측정
                roomRepository.findOneForHealthCheck();
                long endTime = System.currentTimeMillis();
                latency = endTime - startTime;
                isMongoConnected = true;
            } catch (Exception e) {
                log.warn("MongoDB 연결 확인 실패", e);
                isMongoConnected = false;
            }

            // 최근 활동 조회
            LocalDateTime lastActivity = roomRepository.findMostRecentRoom()
                    .map(Room::getCreatedAt)
                    .orElse(null);

            // 서비스 상태 정보 구성
            Map<String, HealthResponse.ServiceHealth> services = new HashMap<>();
            services.put("database", HealthResponse.ServiceHealth.builder()
                .connected(isMongoConnected)
                .latency(latency)
                .build());

            return HealthResponse.builder()
                .success(true)
                .services(services)
                .lastActivity(lastActivity)
                .build();

        } catch (Exception e) {
            log.error("Health check 실행 중 에러 발생", e);
            return HealthResponse.builder()
                .success(false)
                .services(new HashMap<>())
                .build();
        }
    }

    public Room createRoom(CreateRoomRequest createRoomRequest, String name) {
        User creator = userRepository.findByEmail(name)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + name));

        Room room = new Room();
        room.setName(createRoomRequest.getName().trim());
        room.setCreator(creator.getId());
        room.getParticipantIds().add(creator.getId());

        if (createRoomRequest.getPassword() != null && !createRoomRequest.getPassword().isEmpty()) {
            room.setHasPassword(true);
            room.setPassword(passwordEncoder.encode(createRoomRequest.getPassword()));
        }

        Room savedRoom = roomRepository.save(room);
        roomParticipantPresenceService.initialize(savedRoom);
        
        // Publish event for room created
        try {
            RoomResponse roomResponse = toRoomResponse(savedRoom, name);
            eventPublisher.publishEvent(new RoomCreatedEvent(this, roomResponse));
        } catch (Exception e) {
            log.error("roomCreated 이벤트 발행 실패", e);
        }
        
        return savedRoom;
    }

    public Optional<Room> findRoomById(String roomId) {
        return roomRepository.findById(roomId);
    }

    public Room joinRoom(String roomId, String password, String name) {
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return null;
        }

        Room room = roomOpt.get();
        User user = userRepository.findByEmail(name)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + name));

        // 비밀번호 확인
        if (room.isHasPassword()) {
            if (password == null || !passwordEncoder.matches(password, room.getPassword())) {
                throw new RuntimeException("비밀번호가 일치하지 않습니다.");
            }
        }

        Set<String> participantIds = roomParticipantPresenceService.getParticipantIds(room);

        // 이미 참여중인지 확인
        if (!participantIds.contains(user.getId())) {
            roomParticipantPresenceService.addParticipant(roomId, user.getId());
            room = roomRepository.findById(roomId).orElse(room);
        }
        
        // Publish event for room updated
        try {
            RoomResponse roomResponse = toRoomResponse(room, name);
            eventPublisher.publishEvent(new RoomUpdatedEvent(this, roomId, roomResponse));
        } catch (Exception e) {
            log.error("roomUpdate 이벤트 발행 실패", e);
        }

        return room;
    }

    public RoomResponse toRoomResponse(Room room, String name) {
        if (room == null) return null;

        Map<String, User> usersById = loadUsersById(List.of(room));
        int recentMessageCount = safeRecentMessageCount(room.getId());
        return mapToRoomResponse(room, name, usersById, recentMessageCount);
    }

    private Map<String, User> loadUsersById(Collection<Room> rooms) {
        Set<String> userIds = new HashSet<>();
        for (Room room : rooms) {
            if (room.getCreator() != null) {
                userIds.add(room.getCreator());
            }
            userIds.addAll(safeParticipantIds(room));
        }

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private RoomResponse mapToRoomResponse(
            Room room,
            String name,
            Map<String, User> usersById,
            int recentMessageCount) {
        Set<String> participantIds = safeParticipantIds(room);
        User creator = usersById.get(room.getCreator());
        List<User> participants = participantIds.stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .toList();

        return RoomResponse.builder()
            .id(room.getId())
            .name(room.getName() != null ? room.getName() : "제목 없음")
            .hasPassword(room.isHasPassword())
            .creator(toUserSummary(creator, room.getCreator()))
            .participants(participants.stream()
                .map(this::toUserSummary)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()))
            .createdAtDateTime(room.getCreatedAt())
            .isCreator(creator != null
                    && creator.getEmail() != null)
            .createdAtDateTime(room.getCreatedAt() != null ? room.getCreatedAt() : LocalDateTime.now())
            .isCreator(creator != null
                    && creator.getEmail() != null
                    && name != null
                    && creator.getEmail().equalsIgnoreCase(name))
            .recentMessageCount(recentMessageCount)
            .build();
    }

    private Map<String, Integer> loadRecentMessageCounts(Collection<Room> rooms) {
        List<String> roomIds = rooms.stream()
                .map(Room::getId)
                .filter(Objects::nonNull)
                .toList();

        if (roomIds.isEmpty()) {
            return Map.of();
        }

        try {
            return recentMessageCounter.countRecentMessagesByRoomIds(roomIds);
        } catch (RuntimeException e) {
            log.warn("최근 메시지 수 일괄 조회 실패. 방별 fallback으로 전환합니다.", e);
            Map<String, Integer> fallback = new HashMap<>();
            for (String roomId : roomIds) {
                fallback.put(roomId, safeRecentMessageCount(roomId));
            }
            return fallback;
        }
    }

    private int safeRecentMessageCount(String roomId) {
        if (roomId == null) {
            return 0;
        }

        try {
            return recentMessageCounter.countRecentMessages(roomId);
        } catch (RuntimeException e) {
            log.warn("최근 메시지 수 조회 실패 - roomId={}", roomId, e);
            return 0;
        }
    }

    private Set<String> safeParticipantIds(Room room) {
        if (room == null) {
            return Set.of();
        }

        try {
            return roomParticipantPresenceService.getParticipantIds(room);
        } catch (RuntimeException e) {
            log.warn("참여자 조회 실패. Room 문서 participantIds로 fallback합니다. roomId={}", room.getId(), e);
            return room.getParticipantIds() != null ? room.getParticipantIds() : Set.of();
        }
    }

    private Optional<RoomResponse> safeMapToRoomResponse(
            Room room,
            String name,
            Map<String, User> usersById,
            int recentMessageCount) {
        try {
            return Optional.of(mapToRoomResponse(room, name, usersById, recentMessageCount));
        } catch (RuntimeException e) {
            log.warn("방 응답 조립 실패 - roomId={}", room != null ? room.getId() : null, e);
            return Optional.empty();
        }
    }

    private UserResponse toUserSummary(User user) {
        return toUserSummary(user, null);
    }

    private UserResponse toUserSummary(User user, String fallbackId) {
        if (user == null && fallbackId == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user != null && user.getId() != null ? user.getId() : fallbackId)
                .name(user != null && user.getName() != null ? user.getName() : "알 수 없음")
                .email(user != null && user.getEmail() != null ? user.getEmail() : "")
                .build();
    }
}
