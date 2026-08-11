package com.ktb.chatapp.service;

import com.ktb.chatapp.event.RoomUpdatedEvent;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceJoinRoomTest {

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecentMessageCounter recentMessageCounter;
    @Mock private RoomParticipantPresenceService roomParticipantPresenceService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(
                roomRepository,
                userRepository,
                recentMessageCounter,
                roomParticipantPresenceService,
                passwordEncoder,
                eventPublisher);
    }

    @Test
    void joinRoom_addsParticipantAtomicallyAndUsesReloadedRoom() {
        User creator = User.builder()
                .id("creator-1")
                .email("creator@example.com")
                .name("Creator")
                .build();
        User participant = User.builder()
                .id("participant-1")
                .email("participant@example.com")
                .name("Participant")
                .build();
        Room roomBeforeJoin = Room.builder()
                .id("room-1")
                .name("Room")
                .creator(creator.getId())
                .participantIds(Set.of(creator.getId()))
                .build();
        Room roomAfterJoin = Room.builder()
                .id("room-1")
                .name("Room")
                .creator(creator.getId())
                .participantIds(Set.of(creator.getId(), participant.getId()))
                .build();

        when(roomRepository.findById("room-1"))
                .thenReturn(Optional.of(roomBeforeJoin), Optional.of(roomAfterJoin));
        when(roomParticipantPresenceService.getParticipantIds(roomBeforeJoin))
                .thenReturn(roomBeforeJoin.getParticipantIds());
        when(roomParticipantPresenceService.getParticipantIds(roomAfterJoin))
                .thenReturn(roomAfterJoin.getParticipantIds());
        when(userRepository.findByEmail(participant.getEmail())).thenReturn(Optional.of(participant));
        when(userRepository.findAllById(any())).thenReturn(List.of(creator, participant));
        when(recentMessageCounter.countRecentMessages("room-1")).thenReturn(0);

        Room result = roomService.joinRoom("room-1", null, participant.getEmail());

        assertSame(roomAfterJoin, result);

        verify(roomRepository, org.mockito.Mockito.times(2)).findById("room-1");
        verify(roomParticipantPresenceService).addParticipant("room-1", participant.getId());
        verify(roomRepository, never()).addParticipant(any(), any());
        verify(roomRepository, never()).save(any(Room.class));
        verify(eventPublisher).publishEvent(any(RoomUpdatedEvent.class));
    }
}
