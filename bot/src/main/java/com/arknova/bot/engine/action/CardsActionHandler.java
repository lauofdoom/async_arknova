package com.arknova.bot.engine.action;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.service.DeckService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles the CARDS action — draw cards from the deck, take from the display, or break.
 *
 * <h2>Card rules</h2>
 *
 * <pre>
 * Unupgraded (I): Draw cards from the deck OR snap.
 *                 BREAK: fixed 2💰 (shown on card, not strength-based).
 *
 *   Strength │  1  │  2  │  3  │  4  │  5
 *   Draw     │  1  │  1  │  2  │  2  │  3
 *   Discard  │  1  │  –  │  1  │  –  │  1
 *   Snap     │  –  │  –  │  –  │  –  │  ✓
 *
 * Upgraded  (II): Draw cards within reputation range OR from the deck OR snap.
 *                 BREAK: fixed 2💰.
 *
 *   Strength │  1  │  2  │  3  │  4  │  5
 *   Draw     │  1  │  2  │  2  │  3  │  4
 *   Discard  │  –  │  1  │  –  │  1  │  1
 *   Snap     │  –  │  –  │  ✓  │  ✓  │  ✓
 * </pre>
 *
 * "Discard" = discard from your FULL HAND after drawing (choose any card, including newly drawn).
 * "Snap" = take from the break pile; requires manual resolution in Phase 1. Upgraded "within
 * reputation range" = take from the face-up display within rep range instead of (or in addition to)
 * drawing from the deck.
 *
 * <h2>Request parameters — DRAW (default)</h2>
 *
 * <ul>
 *   <li>{@code "display_card_ids"} — cards to take from display (upgraded only, rep range)
 *   <li>{@code "discard_ids"} — card IDs from hand to discard (required when Discard ≥ 1)
 * </ul>
 *
 * <h2>Request parameters — BREAK</h2>
 *
 * <ul>
 *   <li>{@code "action"} = "BREAK" → gain 2💰 (fixed); no cards drawn
 * </ul>
 *
 * <h2>Request parameters — SNAP</h2>
 *
 * <ul>
 *   <li>{@code "action"} = "SNAP" → take from break pile; manual resolution required
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CardsActionHandler implements ActionHandler {

  private static final Logger log = LoggerFactory.getLogger(CardsActionHandler.class);

  /** Fixed money gained when breaking the CARDS action card. */
  static final int BREAK_VALUE = 2;

  /**
   * Draw config per strength: [drawCount, handDiscardCount, snapAllowed(0/1)]. Index = strength
   * (1–5); index 0 unused.
   */
  private static final int[][] CONFIG_BASE = {
    {0, 0, 0}, // unused
    {1, 1, 0}, // S1: draw 1, discard 1 from hand
    {1, 0, 0}, // S2: draw 1, no discard
    {2, 1, 0}, // S3: draw 2, discard 1 from hand
    {2, 0, 0}, // S4: draw 2, no discard
    {3, 1, 1}, // S5: draw 3, discard 1 from hand, snap available
  };

  private static final int[][] CONFIG_UPGRADED = {
    {0, 0, 0}, // unused
    {1, 0, 0}, // S1: draw 1, no discard        (from deck or display)
    {2, 1, 0}, // S2: draw 2, discard 1 from hand
    {2, 0, 1}, // S3: draw 2, no discard, snap available
    {3, 1, 1}, // S4: draw 3, discard 1 from hand, snap available
    {4, 1, 1}, // S5: draw 4, discard 1 from hand, snap available
  };

  private final DeckService deckService;

  /**
   * Returns [drawCount, handDiscardCount] for the given strength. handDiscardCount = cards to
   * discard from full hand after drawing.
   */
  static int[] drawConfig(int strength, boolean upgraded) {
    int[] cfg = upgraded ? CONFIG_UPGRADED[strength] : CONFIG_BASE[strength];
    return new int[] {cfg[0], cfg[1]};
  }

  /** Returns true if snapping is available at the given strength. */
  static boolean snapAvailable(int strength, boolean upgraded) {
    int[] cfg = upgraded ? CONFIG_UPGRADED[strength] : CONFIG_BASE[strength];
    return cfg[2] == 1;
  }

  @Override
  public ActionCard getActionCard() {
    return ActionCard.CARDS;
  }

  @Override
  public ActionResult execute(
      ActionRequest request, PlayerState player, SharedBoardState sharedBoard) {

    int strength = player.getStrengthOf(ActionCard.CARDS);
    boolean upgraded = player.getActionCardOrder().isUpgraded(ActionCard.CARDS);
    UUID gameId = request.gameId();
    String discordId = request.discordId();

    String action = request.paramStr("action");

    // ── BREAK ────────────────────────────────────────────────────────────────
    if ("BREAK".equalsIgnoreCase(action)) {
      player.setMoney(player.getMoney() + BREAK_VALUE);
      player.setBreakTrack(player.getBreakTrack() + 1);
      String summary = request.discordName() + " broke **Cards** → +" + BREAK_VALUE + "💰.";
      log.info("Game {}: {} CARDS break +{} breakTrack={}", gameId, discordId, BREAK_VALUE,
          player.getBreakTrack());
      return ActionResult.success(
          ActionCard.CARDS, strength, summary, Map.of("money_gained", BREAK_VALUE, "break", true));
    }

    // ── SNAP ─────────────────────────────────────────────────────────────────
    // Minimum reputation required to access each display slot (0-based index).
    // Slots 4 and 5 (1-based slots 5-6) also require the upgraded CARDS card.
    int[] cfg = upgraded ? CONFIG_UPGRADED[strength] : CONFIG_BASE[strength];
    boolean snapAllowed = cfg[2] == 1;

    if ("SNAP".equalsIgnoreCase(action)) {
      if (!snapAllowed) {
        return ActionResult.failure(
            "Snapping is not available at strength "
                + strength
                + (upgraded ? " (upgraded)" : "")
                + ".");
      }
      // SNAP = take any one card from the display into hand, ignoring reputation.
      String snapCardId = request.paramStr("snap_card_id");
      if (snapCardId == null || snapCardId.isBlank()) {
        return ActionResult.failure(
            "Specify which display card to snap with `snap_card_id:<card_id>`. "
                + "Use `/arknova display` to see the current display.");
      }
      List<PlayerCard> display = deckService.getDisplay(gameId);
      boolean inDisplay =
          display.stream().anyMatch(pc -> pc.getCard().getId().equals(snapCardId));
      if (!inDisplay) {
        return ActionResult.failure("Card " + snapCardId + " is not in the display.");
      }
      deckService.takeFromDisplay(gameId, discordId, snapCardId);
      String snappedName =
          display.stream()
              .filter(pc -> pc.getCard().getId().equals(snapCardId))
              .findFirst()
              .map(pc -> pc.getCard().getName())
              .orElse(snapCardId);
      String summary = request.discordName() + " snapped **" + snappedName + "** from the display.";
      log.info("Game {}: {} CARDS snap card={}", gameId, discordId, snapCardId);
      return ActionResult.successWithCards(
          ActionCard.CARDS, strength, summary, Map.of("snap", true), List.of(snapCardId));
    }

    // ── DRAW (default) ───────────────────────────────────────────────────────
    int drawCount = cfg[0];
    int handDiscard = cfg[1];

    List<String> displayCardIds = request.paramList("display_card_ids");
    List<String> discardIds = request.paramList("discard_ids");

    // Display takes only for upgraded
    if (!displayCardIds.isEmpty() && !upgraded) {
      return ActionResult.failure("Drawing from the display requires the upgraded CARDS card.");
    }

    // Total cards drawn = deck draws + display takes ≤ drawCount
    if (displayCardIds.size() > drawCount) {
      return ActionResult.failure(
          "You can take at most "
              + drawCount
              + " card(s) total at strength "
              + strength
              + " (requested "
              + displayCardIds.size()
              + " from display).");
    }

    // Reputation thresholds per display slot (0-based index → min reputation required).
    // Slot 5 (index 4) and slot 6 (index 5) require the upgraded CARDS card.
    int[] minRepPerSlot = {1, 2, 4, 7, 10, 13};

    // Reputation check for display takes
    List<PlayerCard> display =
        displayCardIds.isEmpty() ? List.of() : deckService.getDisplay(gameId);
    for (String cardId : displayCardIds) {
      PlayerCard pc =
          display.stream().filter(c -> c.getCard().getId().equals(cardId)).findFirst().orElse(null);
      if (pc == null) {
        return ActionResult.failure("Card " + cardId + " is not in the display.");
      }
      int slotIndex = pc.getSortOrder(); // 0-based
      // Slots 5-6 (index 4-5) require the upgraded CARDS card
      if (slotIndex >= 4 && !upgraded) {
        return ActionResult.failure(
            pc.getCard().getName()
                + " is in display slot "
                + (slotIndex + 1)
                + " — accessing slots 5 and 6 requires the upgraded CARDS card.");
      }
      int minRep = slotIndex < minRepPerSlot.length ? minRepPerSlot[slotIndex] : 13;
      if (player.getReputation() < minRep) {
        return ActionResult.failure(
            pc.getCard().getName()
                + " is in display slot "
                + (slotIndex + 1)
                + " which requires reputation "
                + minRep
                + " (you have "
                + player.getReputation()
                + ").");
      }
    }

    // ── Execute display takes ────────────────────────────────────────────────
    List<String> acquired = new ArrayList<>(displayCardIds);
    for (String cardId : displayCardIds) {
      deckService.takeFromDisplay(gameId, discordId, cardId);
    }

    // ── Execute deck draws ───────────────────────────────────────────────────
    int deckDrawCount = drawCount - displayCardIds.size();
    List<String> drawn = List.of();
    if (deckDrawCount > 0) {
      drawn = deckService.drawFromDeck(gameId, discordId, deckDrawCount);
      acquired.addAll(drawn);
    }

    // ── Hand discard (two-phase flow) ────────────────────────────────────────
    // If a discard is required AND cards were actually acquired, defer the discard to a
    // separate /arknova discard command so the player can see their drawn cards first.
    boolean discardRequired = handDiscard > 0 && !acquired.isEmpty();
    if (discardRequired) {
      // Set pending discard on the player — turn will not advance until resolved.
      player.setPendingDiscardCount(handDiscard);
    } else if (!discardIds.isEmpty()) {
      return ActionResult.failure(
          "No discard required at strength " + strength + (upgraded ? " (upgraded)" : "") + ".");
    }

    // ── Build result ─────────────────────────────────────────────────────────
    StringBuilder summary = new StringBuilder();
    summary
        .append(request.discordName())
        .append(" used **Cards** (strength ")
        .append(strength)
        .append(", X=")
        .append(strength)
        .append(")");
    if (!displayCardIds.isEmpty()) {
      summary.append(", took ").append(displayCardIds.size()).append(" from display");
    }
    if (!drawn.isEmpty()) {
      summary.append(", drew ").append(drawn.size()).append(" from deck");
    }
    if (discardRequired) {
      summary
          .append(" — please discard ")
          .append(handDiscard)
          .append(" card(s) with `/arknova discard`");
    }
    summary
        .append(". Hand: ")
        .append(deckService.getHand(gameId, discordId).size())
        .append(" card(s).");

    log.info(
        "Game {}: {} CARDS strength={} deck={} display={} pendingDiscard={}",
        gameId,
        discordId,
        strength,
        drawn.size(),
        displayCardIds.size(),
        handDiscard);

    return ActionResult.successWithCards(
        ActionCard.CARDS,
        strength,
        summary.toString(),
        Map.of(
            "cards_drawn", drawn.size(),
            "cards_from_display", displayCardIds.size(),
            "pending_discard", discardRequired ? handDiscard : 0),
        acquired);
  }
}
