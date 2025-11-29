package com.techcotalk.redis.service;

import com.techcotalk.redis.domain.model.GameRoom;

public interface GameService {
    void createRoom(String roomId);

    void selectCard(String roomId, String userId, String cardValue);

    GameRoom getGameRoom(String roomId);

    String getStrategyName();
}
