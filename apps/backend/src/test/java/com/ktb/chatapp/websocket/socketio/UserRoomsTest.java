package com.ktb.chatapp.websocket.socketio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UserRoomsTest {

    @Test
    void concurrentRoomAdds_doNotOverwriteEachOther() throws InterruptedException {
        LocalChatDataStore store = new LocalChatDataStore();
        UserRooms userRooms = new UserRooms(store);
        int roomCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(roomCount);

        try {
            for (int index = 0; index < roomCount; index++) {
                String roomId = "room-" + index;
                executor.submit(() -> {
                    try {
                        start.await();
                        userRooms.add("user-1", roomId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));

            Set<String> expected = IntStream.range(0, roomCount)
                    .mapToObj(index -> "room-" + index)
                    .collect(Collectors.toSet());
            assertThat(userRooms.get("user-1")).containsExactlyInAnyOrderElementsOf(expected);
        } finally {
            executor.shutdownNow();
        }
    }
}
