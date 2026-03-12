package com.arknova.bot.engine;

import com.arknova.bot.engine.action.ActionHandler;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.ActionLogEntry;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.Game.GameStatus;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.repository.ActionLogRepository;
import com.arknova.bot.repository.GameRepository;
import com.arknova.bot.repository.PlayerStateRepository;
import com.arknova.bot.repository.SharedBoardStateRepository;
import com.arknova.bot.service.DeckService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Main game engine implementation. Orchestrates action validation, dispatching to action handlers,
 * win condition checking, turn advancement, and persistence.
 *
 * <h2>Action execution sequence</h2>
 *
 * <ol>
 *   <li>Load game and validate it is ACTIVE
 *   <li>Verify it is the requesting player's turn
 *   <li>Load player state and shared board
 *   <li>Dispatch to the correct {@link ActionHandler}
 *   <li>On success: rotate the used action card to position 1 (strength 1)
 *   <li>Check win condition (tracks crossed)
 *   <li>Advance turn to next player (or trigger final scoring)
 *   <li>Persist all mutated state and append action log entry
 *   <li>Return {@link ActionResult} to the caller
 * </ol>
 */
@Service
public class GameEngineImpl implements GameEngine {

  private static final Logger log = LoggerFactory.getLogger(GameEngineImpl.class);

  private final GameRepository gameRepo;
  private final PlayerStateRepository playerStateRepo;
  private final SharedBoardStateRepository sharedBoardRepo;
  private final ActionLogRepository actionLogRepo;
  private final WinConditionChecker winChecker;
  private final DeckService deckService;

  /** All five action card handlers, indexed by the card they handle. */
  private final Map<ActionCard, ActionHandler> handlers;

  public GameEngineImpl(
      GameRepository gameRepo,
      PlayerStateRepository playerStateRepo,
      SharedBoardStateRepository sharedBoardRepo,
      ActionLogRepository actionLogRepo,
      WinConditionChecker winChecker,
      DeckService deckService,
      List<ActionHandler> handlerList) {
    this.gameRepo = gameRepo;
    this.playerStateRepo = playerStateRepo;
    this.sharedBoardRepo = sharedBoardRepo;
    this.actionLogRepo = actionLogRepo;
    this.winChecker = winChecker;
    this.deckService = deckService;
    this.handlers =
        handlerList.stream()
            .collect(Collectors.toMap(ActionHandler::getActionCard, Function.identity()));
    log.info("GameEngine initialised with handlers: {}", handlers.keySet());
  }

  // ── executeAction ──────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ActionResult executeAction(ActionRequest request) {
    UUID gameId = request.gameId();
    String discordId = request.discordId();
    ActionCard card = request.actionCard();

    // 1. Load and validate game
    Game game = gameRepo.findById(gameId).orElse(null);
    if (game == null) {
      return ActionResult.failure("Game not found.");
    }
    if (!game.isActive() && game.getStatus() != GameStatus.FINAL_SCORING) {
      return ActionResult.failure(
          "This game is not currently active (status: " + game.getStatus() + ").");
    }

    // 2. Load player
    PlayerState player = playerStateRepo.findByGameIdAndDiscordId(gameId, discordId).orElse(null);
    if (player == null) {
      return ActionResult.failure("You are not a participant in this game.");
    }

    // 3. Validate it's this player's turn
    if (!isPlayerTurn(game, player)) {
      PlayerState current =
          playerStateRepo.findByGameIdAndSeatIndex(gameId, game.getCurrentSeat()).orElse(null);
      String whose = current != null ? current.getDiscordName() : "another player";
      return ActionResult.failure("It's not your turn — waiting for " + whose + ".");
    }

    // 4. Load shared board
    SharedBoardState sharedBoard = sharedBoardRepo.findByGameId(gameId).orElse(null);
    if (sharedBoard == null) {
      return ActionResult.failure(
          "Shared board state not found — game may not have started properly.");
    }

    // 5. Dispatch to handler
    ActionHandler handler = handlers.get(card);
    if (handler == null) {
      return ActionResult.failure("Action card " + card + " is not yet implemented.");
    }

    // Capture strength and turn number BEFORE any mutations
    int strengthUsed = player.getStrengthOf(card);
    int turnNumber = game.getTurnNumber();
    ActionResult result = handler.execute(request, player, sharedBoard);

    if (!result.success()) {
      return result;
    }

    // 6. Rotate action card to leftmost position
    ActionCardOrder cardOrder = player.getActionCardOrder();
    cardOrder.use(card); // moves card to position 1
    player.setActionCardOrder(cardOrder);

    // 7. Check win condition
    boolean finalScoringTriggered = false;
    if (winChecker.hasWon(player) && !game.isFinalScoringTriggered()) {
      game.setFinalScoringTriggered(true);
      game.setStatus(GameStatus.FINAL_SCORING);
      finalScoringTriggered = true;
      log.info("Game {}: FINAL SCORING triggered by {}", gameId, discordId);
    }

    // If in final scoring, mark this player's turn as done
    if (game.getStatus() == GameStatus.FINAL_SCORING) {
      player.setFinalScoringDone(true);
    }

    // 8. Advance turn — skip if the player still needs to complete a pending discard
    if (player.getPendingDiscardCount() > 0) {
      // Persist state but hold the turn — the discard command will advance it
      playerStateRepo.save(player);
      sharedBoardRepo.save(sharedBoard);
      gameRepo.save(game);
      appendActionLog(request, result, strengthUsed, turnNumber);
      return result;
    }

    advanceTurn(game);

    // Check if final scoring round is complete (all players done)
    if (game.getStatus() == GameStatus.FINAL_SCORING) {
      List<PlayerState> allPlayers = playerStateRepo.findByGameIdOrderBySeatIndexAsc(gameId);
      if (winChecker.isFinalScoringComplete(allPlayers)) {
        game.setStatus(GameStatus.ENDED);
        log.info("Game {} ENDED", gameId);
      }
    }

    // 9. Persist
    playerStateRepo.save(player);
    sharedBoardRepo.save(sharedBoard);
    gameRepo.save(game);
    appendActionLog(request, result, strengthUsed, turnNumber);

    // Rebuild result with finalScoringTriggered flag
    if (finalScoringTriggered) {
      return new ActionResult(
          result.success(),
          result.errorMessage(),
          result.cardUsed(),
          result.strengthUsed(),
          result.summary()
              + "\n\n🏁 **Final scoring round triggered!** All remaining players take one more turn.",
          result.deltas(),
          result.drawnCardIds(),
          true,
          result.requiresManualResolution(),
          result.manualResolutionCardId());
    }

    return result;
  }

  // ── executeDiscard ─────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ActionResult executeDiscard(UUID gameId, String discordId, List<String> cardIds) {
    // 1. Load and validate game is active
    Game game = gameRepo.findById(gameId).orElse(null);
    if (game == null) {
      return ActionResult.failure("Game not found.");
    }
    if (!game.isActive() && game.getStatus() != GameStatus.FINAL_SCORING) {
      return ActionResult.failure(
          "This game is not currently active (status: " + game.getStatus() + ").");
    }

    // 2. Load player, validate it's their turn
    PlayerState player = playerStateRepo.findByGameIdAndDiscordId(gameId, discordId).orElse(null);
    if (player == null) {
      return ActionResult.failure("You are not a participant in this game.");
    }
    if (!isPlayerTurn(game, player)) {
      PlayerState current =
          playerStateRepo.findByGameIdAndSeatIndex(gameId, game.getCurrentSeat()).orElse(null);
      String whose = current != null ? current.getDiscordName() : "another player";
      return ActionResult.failure("It's not your turn — waiting for " + whose + ".");
    }

    // 3. Validate player has a pending discard
    int pending = player.getPendingDiscardCount();
    if (pending == 0) {
      return ActionResult.failure("You have no pending discard. Use an action card first.");
    }

    // 4. Validate correct number of cards provided
    if (cardIds.size() != pending) {
      return ActionResult.failure(
          "You must discard exactly "
              + pending
              + " card(s) (you provided "
              + cardIds.size()
              + ").");
    }

    // 5. Apply the discard
    deckService.discardFromHand(gameId, discordId, cardIds);

    // 6. Clear pending discard
    player.setPendingDiscardCount(0);

    // 7. Advance turn
    advanceTurn(game);

    // Check if final scoring round is complete
    if (game.getStatus() == GameStatus.FINAL_SCORING) {
      List<PlayerState> allPlayers = playerStateRepo.findByGameIdOrderBySeatIndexAsc(gameId);
      if (winChecker.isFinalScoringComplete(allPlayers)) {
        game.setStatus(GameStatus.ENDED);
        log.info("Game {} ENDED", gameId);
      }
    }

    // 8. Persist
    playerStateRepo.save(player);
    gameRepo.save(game);

    String summary =
        player.getDiscordName() + " discarded " + cardIds.size() + " card(s) from hand.";
    log.info("Game {}: {} DISCARD cards={}", gameId, discordId, cardIds);

    return ActionResult.success(null, 0, summary, Map.of("cards_discarded", cardIds.size()));
  }

  // ── undo ──────────────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ActionResult undo(UUID gameId, String discordId) {
    // TODO Phase 3: implement snapshot-based undo
    return ActionResult.failure("Undo is not yet implemented.");
  }

  // ── isPlayerTurn ──────────────────────────────────────────────────────────

  @Override
  public boolean isPlayerTurn(Game game, PlayerState player) {
    return game.getCurrentSeat() == player.getSeatIndex();
  }

  // ── Internal ──────────────────────────────────────────────────────────────

  private void advanceTurn(Game game) {
    int playerCount = (int) playerStateRepo.countByGameId(game.getId());
    if (playerCount == 0) return;

    int nextSeat = (game.getCurrentSeat() + 1) % playerCount;
    game.setCurrentSeat(nextSeat);
    game.setTurnNumber(game.getTurnNumber() + 1);
  }

  private void appendActionLog(
      ActionRequest request, ActionResult result, int strengthUsed, int turnNumber) {
    try {
      ActionLogEntry entry = new ActionLogEntry();
      entry.setGameId(request.gameId());
      entry.setTurnNumber(turnNumber);
      entry.setDiscordId(request.discordId());
      entry.setDiscordName(request.discordName());
      entry.setActionType("USE_ACTION_CARD");
      entry.setActionCard(request.actionCard());
      entry.setStrengthUsed(strengthUsed);
      entry.setDiscordMessageId(request.discordMessageId());
      entry.setRequiresManual(result.requiresManualResolution());
      actionLogRepo.save(entry);
    } catch (Exception e) {
      log.error("Failed to write action log entry for game {}", request.gameId(), e);
      // Don't fail the action just because logging failed
    }
  }
}
