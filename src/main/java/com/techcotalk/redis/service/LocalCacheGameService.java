package com.techcotalk.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techcotalk.redis.domain.dto.GameEvent;
import com.techcotalk.redis.domain.model.GameRoom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LocalCacheGameService implements GameService, MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic topic;
    private final ObjectMapper objectMapper;

    public LocalCacheGameService(RedisTemplate<String, Object> redisTemplate,
            @org.springframework.beans.factory.annotation.Qualifier("localCacheTopic") ChannelTopic topic,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    // Local Memory Storage
    private final Map<String, GameRoom> localCache = new ConcurrentHashMap<>();

    @Override
    public String getStrategyName() {
        return "Local Cache + Redis Pub/Sub (Payload)";
    }

    @Override
    public void createRoom(String roomId) {
        log.debug("createRoom");
        localCache.put(roomId, GameRoom.builder().roomId(roomId).build());
    }

    @Override
    public void selectCard(String roomId, String userId, String cardValue) {
        log.debug("selectCard");

        // 1. Update Local Cache
        GameRoom room = localCache.get(roomId);
        if (room == null) {
            // In a real scenario, we might need to fetch from a persistent store if not in
            // local cache
            // For this demo, we assume it must exist.
            log.warn("Room {} not found in local cache, creating new for demo", roomId);
            room = GameRoom.builder().roomId(roomId).build();
            localCache.put(roomId, room);
        }
        room.selectCard(userId, cardValue);

        // 2. Publish Payload Event (Fire and Forget)
        // We send the ENTIRE room state or just the delta. Here we send the room state
        // for simplicity.
        GameEvent event = GameEvent.builder().type(GameEvent.Type.PAYLOAD).roomId(roomId).payload(room).build();
        redisTemplate.convertAndSend(topic.getTopic(), event);
    }

    @Override
    public GameRoom getGameRoom(String roomId) {
        log.debug("getGameRoom");

        return localCache.get(roomId);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.debug("onMessage");

        try {
            GameEvent event = objectMapper.readValue(message.getBody(), GameEvent.class);
            if (event.getType() == GameEvent.Type.PAYLOAD) {
                log.debug("[Local Cache Service] Received Payload Event for Room: {}. Syncing local cache.",
                        event.getRoomId());
                // Sync local cache with the payload
                if (event.getPayload() != null) {
                    localCache.put(event.getRoomId(), event.getPayload());
                }
            }
        } catch (IOException e) {
            log.error("Error parsing message", e);
        }
    }
}
