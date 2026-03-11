package com.arknova.bot.repository;

import com.arknova.bot.model.PlayerState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerStateRepository extends JpaRepository<PlayerState, UUID> {

  List<PlayerState> findByGameIdOrderBySeatIndexAsc(UUID gameId);

  Optional<PlayerState> findByGameIdAndDiscordId(UUID gameId, String discordId);

  Optional<PlayerState> findByGameIdAndSeatIndex(UUID gameId, int seatIndex);

  int countByGameId(UUID gameId);

  boolean existsByGameIdAndDiscordId(UUID gameId, String discordId);
}
