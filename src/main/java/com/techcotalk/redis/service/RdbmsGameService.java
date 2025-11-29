package com.techcotalk.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techcotalk.redis.domain.dto.GameEvent;
import com.techcotalk.redis.domain.model.GameRoom;
import com.techcotalk.redis.repository.GameJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Slf4j
@Service
public class RdbmsGameService implements GameService, MessageListener {

    private final GameJpaRepository gameJpaRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic topic;
    private final ObjectMapper objectMapper;

    public RdbmsGameService(GameJpaRepository gameJpaRepository, RedisTemplate<String, Object> redisTemplate,
            @org.springframework.beans.factory.annotation.Qualifier("rdbmsTopic") ChannelTopic topic,
            ObjectMapper objectMapper) {
        this.gameJpaRepository = gameJpaRepository;
        this.redisTemplate = redisTemplate;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyName() {
        return "RDBMS + Redis Pub/Sub";
    }

    @Override
    @Transactional
    public void createRoom(String roomId) {
        log.debug("createRoom");
        gameJpaRepository.save(GameRoom.builder().roomId(roomId).build());
    }

    @Override
    @Transactional
    public void selectCard(String roomId, String userId, String cardValue) {
        log.debug("selectCard");

        // 1. Update DB
        GameRoom room = gameJpaRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Room not found"));
        room.selectCard(userId, cardValue);
        gameJpaRepository.save(room);

        // 2. Publish Invalidation Event
        GameEvent event = GameEvent.builder().type(GameEvent.Type.INVALIDATE).roomId(roomId).build();
        redisTemplate.convertAndSend(topic.getTopic(), event);
    }

    @Override
    @Transactional(readOnly = true)
    public GameRoom getGameRoom(String roomId) {
        return gameJpaRepository.findById(roomId).orElse(null);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.debug("onMessage");

        try {
            GameEvent event = objectMapper.readValue(message.getBody(), GameEvent.class);
            if (event.getType() == GameEvent.Type.INVALIDATE) {
                log.debug("[RDBMS Service] Received Invalidation Event for Room: {}. Fetching from DB...",
                        event.getRoomId());
                // In a real app with local caching, we would evict the cache here.
                // Since this is pure RDBMS strategy, we just log it or notify connected clients
                // via WebSocket.
                // For this simulation, we assume the "Client" will call getGameRoom() again.
            }
        } catch (IOException e) {
            log.error("Error parsing message", e);
        }
    }
}
