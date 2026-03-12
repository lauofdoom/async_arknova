package com.arknova.bot.engine.model;

import com.arknova.bot.engine.ActionCard;
import java.util.List;
import java.util.Map;

/**
 * Result of executing a game action. Returned by {@link com.arknova.bot.engine.GameEngine} to the
 * Discord command handler, which uses it to format the public game message.
 */
public record ActionResult(
    boolean success,
    String errorMessage,
    ActionCard cardUsed,
    int strengthUsed,

    /** Human-readable summary of what happened, shown in the game thread. */
    String summary,

    /**
     * State deltas — what changed. Used for the action log {@code result} JSONB column. Keys e.g.
     * "money_delta", "appeal_delta", "cards_drawn", "enclosure_built".
     */
    Map<String, Object> deltas,

    /**
     * Cards drawn into the player's hand this action (shown privately to the player). Empty for
     * actions that don't involve drawing.
     */
    List<String> drawnCardIds,

    /**
     * True if the game has entered Final Scoring phase as a result of this action. The notification
     * service uses this to announce the end-game trigger.
     */
    boolean finalScoringTriggered,

    /**
     * True if this action involved a card with no effect_code and requires the player to manually
     * confirm the effect outcome.
     */
    boolean requiresManualResolution,

    /** The card whose effect needs manual resolution (null if not applicable). */
    String manualResolutionCardId) {

  /** Build a failure result. */
  public static ActionResult failure(String errorMessage) {
    return new ActionResult(
        false, errorMessage, null, 0, null, Map.of(), List.of(), false, false, null);
  }

  /** Build a minimal success result. */
  public static ActionResult success(
      ActionCard card, int strength, String summary, Map<String, Object> deltas) {
    return new ActionResult(
        true, null, card, strength, summary, deltas, List.of(), false, false, null);
  }

  /** Build a success result with drawn cards (for CARDS action). */
  public static ActionResult successWithCards(
      ActionCard card,
      int strength,
      String summary,
      Map<String, Object> deltas,
      List<String> drawnCardIds) {
    return new ActionResult(
        true, null, card, strength, summary, deltas, drawnCardIds, false, false, null);
  }
}
