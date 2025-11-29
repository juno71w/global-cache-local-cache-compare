package com.techcotalk.redis.domain.dto;

import com.techcotalk.redis.domain.model.GameRoom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameEvent implements Serializable {
    public enum Type {
        INVALIDATE, // For RDBMS & Global Cache: "Something changed, go fetch it"
        PAYLOAD // For Local Cache: "Here is the change, apply it"
    }

    private Type type;
    private String roomId;
    private GameRoom payload; // Only used for PAYLOAD type
}
