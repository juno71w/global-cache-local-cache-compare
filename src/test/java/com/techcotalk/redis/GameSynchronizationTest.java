package com.techcotalk.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techcotalk.redis.domain.model.GameRoom;
import com.techcotalk.redis.global.config.RedisConfig;
import com.techcotalk.redis.repository.GameJpaRepository;
import com.techcotalk.redis.service.GameService;
import com.techcotalk.redis.service.GlobalCacheGameService;
import com.techcotalk.redis.service.LocalCacheGameService;
import com.techcotalk.redis.service.RdbmsGameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class GameSynchronizationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ChannelTopic topic;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameJpaRepository gameJpaRepository;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @BeforeEach
    void setUp() {
        // Clean up
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        gameJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Strategy 1: RDBMS + Pub/Sub - Data should be synced via DB fetch")
    void testRdbmsStrategy() {
        // Given
        RdbmsGameService serverA = new RdbmsGameService(gameJpaRepository, redisTemplate, topic, objectMapper);
        RdbmsGameService serverB = new RdbmsGameService(gameJpaRepository, redisTemplate, topic, objectMapper);

        // Register Server B as listener to simulate distributed environment
        redisMessageListenerContainer.addMessageListener(serverB, topic);

        String roomId = "room-1";
        serverA.createRoom(roomId);

        // When
        serverA.selectCard(roomId, "user-1", "Ace");

        // Then
        // Server B should be able to fetch the updated data from DB
        // (In this simple RDBMS impl, it always fetches from DB, so it's trivial,
        // but we verify the flow works)
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            GameRoom room = serverB.getGameRoom(roomId);
            assertThat(room).isNotNull();
            assertThat(room.getUserCards()).containsEntry("user-1", "Ace");
        });
    }

    @Test
    @DisplayName("Strategy 2: Global Cache + Pub/Sub - Data should be synced via Redis fetch")
    void testGlobalCacheStrategy() {
        // Given
        GlobalCacheGameService serverA = new GlobalCacheGameService(redisTemplate, topic, objectMapper);
        GlobalCacheGameService serverB = new GlobalCacheGameService(redisTemplate, topic, objectMapper);

        redisMessageListenerContainer.addMessageListener(serverB, topic);

        String roomId = "room-2";
        serverA.createRoom(roomId);

        // When
        serverA.selectCard(roomId, "user-2", "King");

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            GameRoom room = serverB.getGameRoom(roomId);
            assertThat(room).isNotNull();
            assertThat(room.getUserCards()).containsEntry("user-2", "King");
        });
    }

    @Test
    @DisplayName("Strategy 3: Local Cache + Pub/Sub - Data should be synced via Payload")
    void testLocalCacheStrategy() {
        // Given
        LocalCacheGameService serverA = new LocalCacheGameService(redisTemplate, topic, objectMapper);
        LocalCacheGameService serverB = new LocalCacheGameService(redisTemplate, topic, objectMapper);

        // Register Server B as listener.
        // IMPORTANT: Server B needs to receive the message to update its LOCAL cache.
        redisMessageListenerContainer.addMessageListener(serverB, topic);

        String roomId = "room-3";
        serverA.createRoom(roomId);
        // Server B also needs to know about the room initially or create it on fly
        serverB.createRoom(roomId);

        // When
        serverA.selectCard(roomId, "user-3", "Queen");

        // Then
        // Verify Server B's LOCAL cache is updated WITHOUT fetching from DB/Redis
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            GameRoom room = serverB.getGameRoom(roomId);
            assertThat(room).isNotNull();
            assertThat(room.getUserCards()).containsEntry("user-3", "Queen");
        });
    }
}
