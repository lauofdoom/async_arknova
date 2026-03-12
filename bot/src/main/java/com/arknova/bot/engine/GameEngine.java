package com.arknova.bot.engine;

import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import java.util.List;
import java.util.UUID;

/**
 * Core game rules engine. Validates and applies player actions to game state.
 *
 * <p>Callers (Discord command handlers) build an {@link ActionRequest} and call
 * {@link #executeAction}. The engine:
 * <ol>
 *   <li>Validates the action (correct player's turn, legal move, sufficient resources)
 *   <li>Takes a state snapshot for undo
 *   <li>Applies the action (mutates PlayerState, SharedBoardState, etc.)
 *   <li>Checks win condition
 *   <li>Advances the turn
 *   <li>Persists all changes
 *   <li>Returns an {@link ActionResult} for the caller to format into Discord messages
 * </ol>
 */
public interface GameEngine {

  /**
   * Execute a player action. This is the main entry point for all game actions.
   *
   * <p>The result's {@code success} field indicates whether the action was applied. On failure,
   * {@code errorMessage} contains a player-facing explanation.
   *
   * @param request the action to execute
   * @return result describing what happened (including state deltas and messaging hints)
   */
  ActionResult executeAction(ActionRequest request);

  /**
   * Complete a pending hand discard after a CARDS draw action. Called via the
   * {@code /arknova discard} command. Validates and applies the discard, then advances the turn.
   *
   * @param gameId     the game to act within
   * @param discordId  the player completing the discard
   * @param cardIds    card IDs from the player's hand to discard
   * @return result describing the outcome
   */
  ActionResult executeDiscard(UUID gameId, String discordId, List<String> cardIds);

  /**
   * Undo the last action taken by the specified player, if eligible.
   *
   * <p>Undo is only allowed if:
   * <ul>
   *   <li>The requesting player took the last action in this game
   *   <li>A snapshot exists for that action
   * </ul>
   *
   * @param gameId     the game to undo within
   * @param discordId  the player requesting the undo
   * @return result describing the undo outcome
   */
  ActionResult undo(UUID gameId, String discordId);

  /**
   * Validate whether it is currently this player's turn in the given game.
   * Cheap check — does not require loading full game state.
   */
  boolean isPlayerTurn(Game game, PlayerState player);
}
