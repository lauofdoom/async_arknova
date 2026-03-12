package com.arknova.bot.engine.action;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;

/**
 * Contract for the five action card handlers. Each implementation handles one of: CARDS, BUILD,
 * ANIMALS, ASSOCIATION, SPONSOR.
 *
 * <p>Handlers receive already-validated inputs (turn validation and snapshot are handled by {@link
 * com.arknova.bot.engine.GameEngineImpl} before dispatching here). Handlers are responsible for:
 *
 * <ol>
 *   <li>Validating action-specific rules (resources, card legality, enclosure compatibility)
 *   <li>Mutating {@link PlayerState} and {@link SharedBoardState} in memory
 *   <li>Returning a result describing the changes (the engine persists them)
 * </ol>
 */
public interface ActionHandler {

  /** Which action card this handler processes. */
  ActionCard getActionCard();

  /**
   * Execute the action. The strength used is available via {@link
   * PlayerState#getStrengthOf(ActionCard)} before the card rotation is applied.
   *
   * @param request the player's action request
   * @param player the acting player's current state (mutable)
   * @param sharedBoard the shared board state (mutable)
   * @return result describing what changed
   */
  ActionResult execute(ActionRequest request, PlayerState player, SharedBoardState sharedBoard);
}
