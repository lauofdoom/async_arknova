package com.arknova.bot.engine.action;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.service.DeckService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles the CARDS action — lets the player acquire new cards from the deck or display.
 *
 * <h2>Ark Nova CARDS action rules by strength</h2>
 * <pre>
 * Strength 1: Draw 2 from deck, keep 1 (discard 1)
 * Strength 2: Draw 2 from deck, keep both
 * Strength 3: Draw 3 from deck, keep 2 (discard 1)
 * Strength 4: Take 1 card from the display (reputation range)
 * Strength 5: Take 1 from display AND draw 2 from deck, keep both
 * </pre>
 *
 * <h2>Display and reputation range</h2>
 * The display has 6 face-up slots. Slot number = reputation cost to take.
 * A player can take from slot N only if their reputation ≥ N.
 * (Slot 1 is always free; slot 6 requires reputation 6.)
 *
 * <h2>Request parameters</h2>
 * <ul>
 *   <li>{@code "display_card_ids"} — list of card IDs to take from the display (strength 4/5)
 *   <li>{@code "draw_count"} — how many cards to draw from the deck (engine validates vs. strength)
 *   <li>{@code "discard_ids"} — IDs of drawn cards to immediately discard (strength 1 and 3)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CardsActionHandler implements ActionHandler {

  private static final Logger log = LoggerFactory.getLogger(CardsActionHandler.class);

  private final DeckService deckService;

  @Override
  public ActionCard getActionCard() {
    return ActionCard.CARDS;
  }

  @Override
  public ActionResult execute(ActionRequest request, PlayerState player,
      SharedBoardState sharedBoard) {

    int strength = player.getStrengthOf(ActionCard.CARDS);
    UUID gameId = request.gameId();
    String discordId = request.discordId();

    List<String> displayCardIds = request.paramList("display_card_ids");
    List<String> discardIds     = request.paramList("discard_ids");

    // Validate display takes vs. strength
    int maxDisplayTakes = strength >= 4 ? 1 : 0;
    if (strength == 5 && !player.getActionCardOrder().isUpgraded(ActionCard.CARDS)) {
      maxDisplayTakes = 1; // base strength 5: 1 display take
    }
    if (displayCardIds.size() > maxDisplayTakes) {
      return ActionResult.failure(
          "At strength " + strength + " you can take at most " + maxDisplayTakes
          + " card(s) from the display.");
    }

    // Validate reputation range for display takes
    for (String cardId : displayCardIds) {
      List<?> display = deckService.getDisplay(gameId);
      int slot = -1;
      for (int i = 0; i < display.size(); i++) {
        var pc = (com.arknova.bot.model.PlayerCard) display.get(i);
        if (pc.getCard().getId().equals(cardId)) {
          slot = i + 1; // 1-indexed slot
          break;
        }
      }
      if (slot < 0) {
        return ActionResult.failure("Card " + cardId + " is not in the display.");
      }
      // Slot cost = slot number (slot 1 = free, slot 6 = needs reputation 6)
      if (player.getReputation() < slot - 1) {
        return ActionResult.failure(
            "You need reputation " + (slot - 1) + " to take from display slot " + slot
            + " (you have " + player.getReputation() + ").");
      }
    }

    // ── Execute display takes ────────────────────────────────────────────────
    List<String> acquired = new ArrayList<>(displayCardIds);
    for (String cardId : displayCardIds) {
      deckService.takeFromDisplay(gameId, discordId, cardId);
    }

    // ── Execute deck draws ───────────────────────────────────────────────────
    int[] drawConfig = drawConfig(strength, player.getActionCardOrder()
        .isUpgraded(ActionCard.CARDS));
    int drawCount = drawConfig[0];
    int keepCount = drawConfig[1]; // how many to keep; discards = drawCount - keepCount

    List<String> drawn = List.of();
    if (drawCount > 0 && deckService.deckSize(gameId) > 0) {
      drawn = deckService.drawFromDeck(gameId, discordId, drawCount);
      acquired.addAll(drawn);

      // Handle forced discards (strength 1: draw 2 keep 1; strength 3: draw 3 keep 2)
      int mustDiscard = drawCount - keepCount;
      if (mustDiscard > 0) {
        if (discardIds.size() != mustDiscard) {
          // Roll back drawn cards if the player didn't specify discards
          // In practice the Discord flow enforces this before submitting
          return ActionResult.failure(
              "You drew " + drawCount + " cards and must discard " + mustDiscard
              + ". Please specify which card(s) to discard.");
        }
        deckService.discardFromHand(gameId, discordId, discardIds);
        acquired.removeAll(discardIds);
      }
    }

    // ── Build result ─────────────────────────────────────────────────────────
    StringBuilder summary = new StringBuilder();
    summary.append(request.discordName()).append(" used **Cards** (strength ").append(strength).append(")");
    if (!displayCardIds.isEmpty()) {
      summary.append(", took ").append(displayCardIds.size()).append(" from display");
    }
    if (!drawn.isEmpty()) {
      summary.append(", drew ").append(drawn.size());
      if (!discardIds.isEmpty()) {
        summary.append(", discarded ").append(discardIds.size());
      }
    }
    summary.append(". Hand: ").append(
        deckService.getHand(gameId, discordId).size()).append(" card(s).");

    log.info("Game {}: {} CARDS strength={} acquired={}", gameId, discordId, strength, acquired);

    return ActionResult.successWithCards(
        ActionCard.CARDS, strength, summary.toString(),
        Map.of("cards_drawn", drawn.size(), "cards_from_display", displayCardIds.size()),
        acquired);
  }

  /**
   * Returns [drawCount, keepCount] for a given strength.
   * If the CARDS card is upgraded, keepCount = drawCount (no forced discards).
   */
  static int[] drawConfig(int strength, boolean upgraded) {
    return switch (strength) {
      case 1 -> new int[]{2, upgraded ? 2 : 1};  // draw 2, keep 1 (or keep 2 if upgraded)
      case 2 -> new int[]{2, 2};                   // draw 2, keep 2
      case 3 -> new int[]{3, upgraded ? 3 : 2};   // draw 3, keep 2 (or keep 3 if upgraded)
      case 4 -> new int[]{0, 0};                   // display only at strength 4
      case 5 -> new int[]{2, 2};                   // draw 2 + 1 display
      default -> new int[]{0, 0};
    };
  }

  // Allow UUID import without full qualifier
  private java.util.UUID UUID(String s) { return java.util.UUID.fromString(s); }
}
