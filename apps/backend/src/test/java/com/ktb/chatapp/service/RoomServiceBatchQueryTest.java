package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.RoomsResponse;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceBatchQueryTest {

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecentMessageCounter recentMessageCounter;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void getAllRooms_loadsUsersAndMessageCountsInBatches() {
        User creator = User.builder()
                .id("user-1")
                .email("creator@example.com")
                .name("Creator")
                .build();
        User participant = User.builder()
                .id("user-2")
                .email("participant@example.com")
                .name("Participant")
                .build();
        Room room = Room.builder()
                .id("room-1")
                .name("Room")
                .creator(creator.getId())
                .participantIds(Set.of(creator.getId(), participant.getId()))
                .createdAt(LocalDateTime.now())
                .build();

        when(roomRepository.findAll()).thenReturn(List.of(room));
        when(userRepository.findAllById(any())).thenReturn(List.of(creator, participant));
        when(recentMessageCounter.countRecentMessagesByRoomIds(List.of(room.getId())))
                .thenReturn(Map.of(room.getId(), 3));

        RoomService roomService = new RoomService(
                roomRepository,
                userRepository,
                recentMessageCounter,
                passwordEncoder,
                eventPublisher);

        RoomsResponse response = roomService.getAllRooms(creator.getEmail());

        assertEquals(1, response.getData().size());
        assertEquals(2, response.getData().getFirst().getParticipants().size());
        assertEquals(3, response.getData().getFirst().getRecentMessageCount());
        assertTrue(response.getData().getFirst().isCreator());
        verify(userRepository).findAllById(any());
        verify(recentMessageCounter).countRecentMessagesByRoomIds(List.of(room.getId()));
        verify(userRepository, never()).findById(any());
        verify(recentMessageCounter, never()).countRecentMessages(room.getId());
    }
}
