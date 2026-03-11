package com.arknova.bot.repository;

import com.arknova.bot.model.Game;
import com.arknova.bot.model.Game.GameStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends JpaRepository<Game, UUID> {

  Optional<Game> findByThreadId(String threadId);

  boolean existsByThreadId(String threadId);

  List<Game> findByGuildIdAndStatus(String guildId, GameStatus status);

  List<Game> findByStatus(GameStatus status);

  @Query("SELECT g FROM Game g WHERE g.status NOT IN ('ENDED', 'ABANDONED') ORDER BY g.createdAt DESC")
  List<Game> findAllActive();
}
