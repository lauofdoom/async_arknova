package com.arknova.bot.repository;

import com.arknova.bot.model.Game;
import com.arknova.bot.model.Game.GameStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends JpaRepository<Game, UUID> {

  /** Finds an active (non-ended) game whose thread/game channel matches the given ID. */
  @Query(
      "SELECT g FROM Game g WHERE g.threadId = :threadId"
          + " AND g.status NOT IN ('ENDED', 'ABANDONED')")
  Optional<Game> findActiveByThreadId(@Param("threadId") String threadId);

  /**
   * Returns true if there is already a SETUP game from the same lobby/origin channel. Used to
   * prevent creating a second queued game before the first one starts.
   */
  @Query(
      "SELECT COUNT(g) > 0 FROM Game g WHERE g.originChannelId = :originChannelId"
          + " AND g.status = 'SETUP'")
  boolean existsSetupByOriginChannelId(@Param("originChannelId") String originChannelId);

  /** Finds a game by the ID of its shared {@code #board} channel (set during channel setup). */
  Optional<Game> findByBoardChannelId(String boardChannelId);

  /**
   * Finds a game by a player's private cards channel ID. Used so commands issued from {@code
   * #name-cards} channels are resolved to the correct game.
   */
  @Query("SELECT p.game FROM PlayerState p WHERE p.privateChannelId = :channelId")
  Optional<Game> findByPlayerPrivateChannelId(@Param("channelId") String channelId);

  List<Game> findByGuildIdAndStatus(String guildId, GameStatus status);

  List<Game> findByStatus(GameStatus status);

  @Query(
      "SELECT g FROM Game g WHERE g.status NOT IN ('ENDED', 'ABANDONED') ORDER BY g.createdAt DESC")
  List<Game> findAllActive();
}
