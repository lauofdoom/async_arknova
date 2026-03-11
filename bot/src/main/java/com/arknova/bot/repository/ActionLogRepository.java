package com.arknova.bot.repository;

import com.arknova.bot.model.ActionLogEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionLogRepository extends JpaRepository<ActionLogEntry, Long> {

  List<ActionLogEntry> findByGameIdOrderByTsDesc(UUID gameId);

  List<ActionLogEntry> findTop20ByGameIdOrderByTsDesc(UUID gameId);

  /** The most recent action in this game — used to validate undo eligibility. */
  @Query("SELECT a FROM ActionLogEntry a WHERE a.gameId = :gameId ORDER BY a.ts DESC LIMIT 1")
  Optional<ActionLogEntry> findLastAction(UUID gameId);

  int countByGameId(UUID gameId);
}
