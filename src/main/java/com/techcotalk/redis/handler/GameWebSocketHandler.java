package com.techcotalk.redis.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techcotalk.redis.domain.model.GameRoom;
import com.techcotalk.redis.service.GameService;
import com.techcotalk.redis.service.GlobalCacheGameService;
import com.techcotalk.redis.service.LocalCacheGameService;
import com.techcotalk.redis.service.RdbmsGameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final RdbmsGameService rdbmsGameService;
    private final GlobalCacheGameService globalCacheGameService;
    private final LocalCacheGameService localCacheGameService;
    private final ObjectMapper objectMapper;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        String command = jsonNode.get("command").asText();
        String strategy = jsonNode.get("strategy").asText();
        String roomId = jsonNode.get("roomId").asText();

        GameService service = getService(strategy);

        if ("CREATE_ROOM".equals(command)) {
            service.createRoom(roomId);
            session.sendMessage(new TextMessage("{\"status\":\"CREATED\", \"roomId\":\"" + roomId + "\"}"));
        } else if ("SELECT_CARD".equals(command)) {
            String userId = jsonNode.get("userId").asText();
            String cardValue = jsonNode.get("cardValue").asText();
            service.selectCard(roomId, userId, cardValue);
            session.sendMessage(new TextMessage("{\"status\":\"SELECTED\", \"roomId\":\"" + roomId + "\"}"));
        } else if ("GET_ROOM".equals(command)) {
            GameRoom room = service.getGameRoom(roomId);
            String payload = objectMapper.writeValueAsString(room);
            session.sendMessage(new TextMessage(payload));
        } else {
            session.sendMessage(new TextMessage("{\"error\":\"Unknown command\"}"));
        }
    }

    private GameService getService(String strategy) {
        return switch (strategy.toLowerCase()) {
            case "rdbms" -> rdbmsGameService;
            case "global" -> globalCacheGameService;
            case "local" -> localCacheGameService;
            default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
        };
    }
}
