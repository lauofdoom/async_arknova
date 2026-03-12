package com.arknova.bot.service;

import com.arknova.bot.model.Game;
import com.arknova.bot.model.Game.GameStatus;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.repository.GameRepository;
import com.arknova.bot.repository.PlayerStateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates game lifecycle operations: creation, joining, starting, and retrieving game state.
 *
 * <p>This service is the primary entry point for all Discord command handlers. Game engine logic
 * (action validation, state mutation, win condition) lives in the engine layer and will be wired in
 * as Phase 1 progresses.
 */
@Service
@RequiredArgsConstructor
public class GameService {

  private static final Logger log = LoggerFactory.getLogger(GameService.class);

  private static final int MAX_PLAYERS = 4;
  private static final int MIN_PLAYERS = 2;

  private final GameRepository gameRepo;
  private final PlayerStateRepository playerStateRepo;

  // ── Game Creation ──────────────────────────────────────────────────────────

  /**
   * Creates a new game associated with a Discord thread. The creating player is automatically added
   * as the first player (seat 0).
   *
   * @param guildId Discord guild ID
   * @param threadId Discord thread ID created for this game
   * @param creatorDiscordId Discord ID of the player who ran /arknova create
   * @param creatorName Display name of the creator
   * @return the newly created Game
   * @throws IllegalStateException if a game already exists for this thread
   */
  @Transactional
  public Game createGame(
      String guildId, String threadId, String creatorDiscordId, String creatorName) {

    if (gameRepo.existsByThreadId(threadId)) {
      throw new IllegalStateException("A game already exists in this thread.");
    }

    Game game = new Game();
    game.setGuildId(guildId);
    game.setThreadId(threadId);
    game.setStatus(GameStatus.SETUP);
    game = gameRepo.save(game);

    // Add the creator as player 0
    addPlayerToGame(game, creatorDiscordId, creatorName, 0);

    log.info("Game {} created in thread {} by {}", game.getId(), threadId, creatorName);
    return game;
  }

  // ── Joining ────────────────────────────────────────────────────────────────

  /**
   * Adds a player to an open (SETUP status) game.
   *
   * @throws IllegalStateException if game is full, not in SETUP, or player already joined
   */
  @Transactional
  public PlayerState joinGame(String threadId, String discordId, String discordName) {
    Game game = requireGame(threadId);

    if (!game.isSetup()) {
      throw new IllegalStateException("This game has already started.");
    }

    int currentPlayerCount = playerStateRepo.countByGameId(game.getId());
    if (currentPlayerCount >= MAX_PLAYERS) {
      throw new IllegalStateException("This game is full (" + MAX_PLAYERS + " players maximum).");
    }

    if (playerStateRepo.existsByGameIdAndDiscordId(game.getId(), discordId)) {
      throw new IllegalStateException("You have already joined this game.");
    }

    return addPlayerToGame(game, discordId, discordName, currentPlayerCount);
  }

  // ── Starting ───────────────────────────────────────────────────────────────

  /**
   * Starts the game. Validates minimum player count and transitions status to ACTIVE.
   *
   * @throws IllegalStateException if called by a non-participant, game is not in SETUP, or there
   *     are fewer than MIN_PLAYERS players
   */
  @Transactional
  public Game startGame(String threadId, String requestingDiscordId) {
    Game game = requireGame(threadId);

    if (!game.isSetup()) {
      throw new IllegalStateException("Game is not in setup phase.");
    }

    if (!playerStateRepo.existsByGameIdAndDiscordId(game.getId(), requestingDiscordId)) {
      throw new IllegalStateException("Only participants can start the game.");
    }

    int playerCount = playerStateRepo.countByGameId(game.getId());
    if (playerCount < MIN_PLAYERS) {
      throw new IllegalStateException(
          "Need at least " + MIN_PLAYERS + " players to start (currently " + playerCount + ").");
    }

    game.setStatus(GameStatus.ACTIVE);
    game.setStartedAt(Instant.now());
    game.setCurrentSeat(0);
    game.setTurnNumber(1);
    game = gameRepo.save(game);

    List<PlayerState> players = playerStateRepo.findByGameIdOrderBySeatIndexAsc(game.getId());
    initializePlayerStates(players);

    log.info("Game {} started with {} players", game.getId(), playerCount);
    return game;
  }

  /**
   * Sets initial resources for each player at game start. Starting money follows the official rule:
   * the player in seat {@code n} begins with {@code 25 + n} money (compensating for acting later in
   * turn order).
   */
  @Transactional
  public void initializePlayerStates(List<PlayerState> players) {
    for (PlayerState player : players) {
      player.setMoney(25 + player.getSeatIndex());
      // All other starting values (assocWorkers, boardState, conservationSlots,
      // actionCardOrder) are already set to correct defaults by the entity.
      playerStateRepo.save(player);
    }
  }

  // ── Queries ────────────────────────────────────────────────────────────────

  public Optional<Game> findByThreadId(String threadId) {
    return gameRepo.findByThreadId(threadId);
  }

  public List<PlayerState> getPlayersInOrder(UUID gameId) {
    return playerStateRepo.findByGameIdOrderBySeatIndexAsc(gameId);
  }

  public Optional<PlayerState> getCurrentPlayer(Game game) {
    return playerStateRepo.findByGameIdAndSeatIndex(game.getId(), game.getCurrentSeat());
  }

  public Optional<PlayerState> getPlayerState(UUID gameId, String discordId) {
    return playerStateRepo.findByGameIdAndDiscordId(gameId, discordId);
  }

  // ── Internal ───────────────────────────────────────────────────────────────

  private PlayerState addPlayerToGame(
      Game game, String discordId, String discordName, int seatIndex) {
    PlayerState player = new PlayerState();
    player.setGame(game);
    player.setDiscordId(discordId);
    player.setDiscordName(discordName);
    player.setSeatIndex(seatIndex);
    return playerStateRepo.save(player);
  }

  private Game requireGame(String threadId) {
    return gameRepo
        .findByThreadId(threadId)
        .orElseThrow(() -> new IllegalStateException("No game found in this thread."));
  }
}
