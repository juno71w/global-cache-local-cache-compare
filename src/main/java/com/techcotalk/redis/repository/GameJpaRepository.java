package com.techcotalk.redis.repository;

import com.techcotalk.redis.domain.model.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameJpaRepository extends JpaRepository<GameRoom, String> {
}
