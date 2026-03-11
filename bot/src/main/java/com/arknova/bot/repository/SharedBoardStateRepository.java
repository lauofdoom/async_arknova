package com.arknova.bot.repository;

import com.arknova.bot.model.SharedBoardState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SharedBoardStateRepository extends JpaRepository<SharedBoardState, UUID> {
  Optional<SharedBoardState> findByGameId(UUID gameId);
}
