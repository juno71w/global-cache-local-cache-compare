package com.techcotalk.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techcotalk.redis.domain.dto.GameEvent;
import com.techcotalk.redis.domain.model.GameRoom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class GlobalCacheGameService implements GameService, MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic topic;
    private final ObjectMapper objectMapper;

    public GlobalCacheGameService(RedisTemplate<String, Object> redisTemplate,
            @Qualifier("globalCacheTopic") ChannelTopic topic,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    private static final String KEY_PREFIX = "gameroom:";

    @Override
    public String getStrategyName() {
        return "Global Cache (Redis) + Redis Pub/Sub";
    }

    @Override
    public void createRoom(String roomId) {
        log.debug("createRoom");
        GameRoom room = GameRoom.builder().roomId(roomId).build();
        redisTemplate.opsForValue().set(KEY_PREFIX + roomId, room);
    }

    @Override
    public void selectCard(String roomId, String userId, String cardValue) {
        log.debug("selectCard");

        // 1. Update Redis Store
        GameRoom room = (GameRoom) redisTemplate.opsForValue().get(KEY_PREFIX + roomId);
        if (room == null)
            throw new RuntimeException("Room not found");

        room.selectCard(userId, cardValue);
        redisTemplate.opsForValue().set(KEY_PREFIX + roomId, room);

        // 2. Publish Invalidation Event
        GameEvent event = GameEvent.builder().type(GameEvent.Type.INVALIDATE).roomId(roomId).build();
        redisTemplate.convertAndSend(topic.getTopic(), event);
    }

    @Override
    public GameRoom getGameRoom(String roomId) {
        log.debug("getGameRoom");

        return (GameRoom) redisTemplate.opsForValue().get(KEY_PREFIX + roomId);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.debug("onMessage");

        try {
            GameEvent event = objectMapper.readValue(message.getBody(), GameEvent.class);
            if (event.getType() == GameEvent.Type.INVALIDATE) {
                log.debug(
                        "[Global Cache Service] Received Invalidation Event for Room: {}. Clients should fetch from Redis.",
                        event.getRoomId());
            }
        } catch (IOException e) {
            log.error("Error parsing message", e);
        }
    }
}
