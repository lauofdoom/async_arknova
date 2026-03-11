package com.arknova.bot.engine.model;

import com.arknova.bot.engine.ActionCard;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable request to execute a game action. Built by Discord command handlers and passed to
 * {@link com.arknova.bot.engine.GameEngine}.
 *
 * <p>The {@code params} map carries action-specific data. Each action handler documents its
 * expected parameter keys.
 */
public record ActionRequest(
    UUID gameId,
    String discordId,
    String discordName,
    ActionCard actionCard,

    /**
     * Action-specific parameters.
     *
     * <p>CARDS action:
     * <ul>
     *   <li>{@code "display_card_ids"} — list of card IDs to take from the display (may be empty)
     *   <li>{@code "draw_count"} — how many to draw from the deck (engine validates vs. strength)
     *   <li>{@code "discard_ids"} — card IDs from freshly drawn cards to discard (strength-1 draws)
     * </ul>
     *
     * <p>ANIMALS action:
     * <ul>
     *   <li>{@code "card_id"} — card ID of the animal being placed
     *   <li>{@code "enclosure_id"} — which enclosure to place it in (e.g. "E1")
     * </ul>
     *
     * <p>BUILD action:
     * <ul>
     *   <li>{@code "size"} — enclosure size (1–7)
     *   <li>{@code "row"} — grid row
     *   <li>{@code "col"} — grid column
     *   <li>{@code "tags"} — optional terrain tags (e.g. ["WATER"])
     * </ul>
     */
    Map<String, Object> params,

    /** Discord message ID that triggered this action (for linking back). */
    String discordMessageId) {

  /** Convenience getter — casts a param value to String. */
  public String paramStr(String key) {
    Object v = params.get(key);
    return v == null ? null : v.toString();
  }

  /** Convenience getter — casts a param value to Integer. */
  public int paramInt(String key, int defaultValue) {
    Object v = params.get(key);
    if (v == null) return defaultValue;
    if (v instanceof Number n) return n.intValue();
    try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return defaultValue; }
  }

  /** Convenience getter — casts a param value to a List of Strings. */
  @SuppressWarnings("unchecked")
  public List<String> paramList(String key) {
    Object v = params.get(key);
    if (v instanceof List<?> l) return (List<String>) l;
    return List.of();
  }
}
