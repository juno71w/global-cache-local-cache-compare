package com.techcotalk.redis.domain.model;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class GameRoom implements Serializable {
    @Id
    private String roomId;

    @ElementCollection(fetch = FetchType.EAGER)
    @Builder.Default
    private Map<String, String> userCards = new HashMap<>();

    @jakarta.persistence.Transient
    @Builder.Default
    private byte[] dummyData = new byte[1024]; // 1KB dummy data

    public void selectCard(String userId, String cardValue) {
        this.userCards.put(userId, cardValue);
    }
}
