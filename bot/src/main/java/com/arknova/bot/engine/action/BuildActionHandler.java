package com.arknova.bot.engine.action;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles the BUILD action — construct an enclosure on the player's zoo board.
 *
 * <h2>Ark Nova BUILD action rules by strength</h2>
 * <pre>
 * Strength 1: Build size 1–2. Cost = (size × 2) - 1 money. OR build a kiosk.
 * Strength 2: Build size 1–3.
 * Strength 3: Build size 1–4.
 * Strength 4: Build size 1–5. OR upgrade one action card.
 * Strength 5: Build size 1–6. OR build size 1–7 if upgraded BUILD card.
 * </pre>
 *
 * <h2>Enclosure cost</h2>
 * Base cost = (size × 2) - 1. E.g. size 3 = 5 money, size 5 = 9 money.
 *
 * <h2>Terrain tags</h2>
 * Optional tags (WATER, ROCK) add extra cost (+2 per terrain tag) but allow placing animals
 * with those requirements.
 *
 * <h2>Request parameters</h2>
 * <ul>
 *   <li>{@code "size"} — enclosure size 1–7
 *   <li>{@code "row"} — grid row (0-based)
 *   <li>{@code "col"} — grid column (0-based)
 *   <li>{@code "tags"} — optional terrain tags, e.g. ["WATER"] (default: empty)
 *   <li>{@code "upgrade_action"} — if present at strength 4+, upgrade named action card instead
 *       of building (e.g. "BUILD", "ANIMALS")
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BuildActionHandler implements ActionHandler {

  private static final Logger log = LoggerFactory.getLogger(BuildActionHandler.class);

  /** Maximum size buildable at each strength (1-indexed). */
  private static final int[] MAX_SIZE_BY_STRENGTH = {0, 2, 3, 4, 5, 6};

  /** Terrain tag names that add extra cost when added to an enclosure. */
  private static final List<String> TERRAIN_TAGS = List.of("WATER", "ROCK");

  /** Money cost per terrain tag added. */
  private static final int TERRAIN_COST = 2;

  private final ObjectMapper objectMapper;

  @Override
  public ActionCard getActionCard() {
    return ActionCard.BUILD;
  }

  @Override
  public ActionResult execute(ActionRequest request, PlayerState player,
      SharedBoardState sharedBoard) {

    int strength   = player.getStrengthOf(ActionCard.BUILD);
    String discordId = request.discordId();

    // ── Special case: upgrade an action card (strength 4+) ──────────────────
    String upgradeTarget = request.paramStr("upgrade_action");
    if (upgradeTarget != null) {
      return executeUpgrade(request, player, strength, upgradeTarget);
    }

    // ── Parse parameters ─────────────────────────────────────────────────────
    int size = request.paramInt("size", 0);
    int row  = request.paramInt("row", -1);
    int col  = request.paramInt("col", -1);
    List<String> terrainTags = request.paramList("tags").stream()
        .map(String::toUpperCase)
        .filter(TERRAIN_TAGS::contains)
        .toList();

    if (size < 1) {
      return ActionResult.failure("Please specify an enclosure size (1–" +
          maxSizeForStrength(strength, player.getActionCardOrder().isUpgraded(ActionCard.BUILD)) + ").");
    }
    if (row < 0 || col < 0) {
      return ActionResult.failure("Please specify a grid location (row and col).");
    }

    // ── Validate size vs. strength ────────────────────────────────────────────
    int maxSize = maxSizeForStrength(strength, player.getActionCardOrder().isUpgraded(ActionCard.BUILD));
    if (size > maxSize) {
      return ActionResult.failure(
          "At strength " + strength + " you can build enclosures up to size " + maxSize
          + " (requested size " + size + ").");
    }

    // ── Calculate cost ────────────────────────────────────────────────────────
    int baseCost    = (size * 2) - 1;
    int terrainCost = terrainTags.size() * TERRAIN_COST;
    int totalCost   = baseCost + terrainCost;

    if (player.getMoney() < totalCost) {
      return ActionResult.failure(
          "Building a size-" + size + " enclosure costs " + totalCost + " money"
          + (terrainCost > 0 ? " (base " + baseCost + " + " + terrainCost + " terrain)" : "")
          + ", but you only have " + player.getMoney() + ".");
    }

    // ── Validate grid position ────────────────────────────────────────────────
    // For MVP: just check the enclosure ID doesn't already exist; full grid
    // collision detection comes in Phase 3 with map-aware rendering
    String enclosureId = "E" + (existingEnclosureCount(player) + 1);
    if (enclosureExists(player, row, col)) {
      return ActionResult.failure(
          "Your zoo already has a structure at row " + row + ", col " + col + ".");
    }

    // ── Apply state changes ───────────────────────────────────────────────────
    player.setMoney(player.getMoney() - totalCost);
    addEnclosureToBoard(player, enclosureId, size, row, col, terrainTags);

    // ── Build result ──────────────────────────────────────────────────────────
    StringBuilder summary = new StringBuilder();
    summary.append(request.discordName())
        .append(" built a **size-").append(size).append(" enclosure**");
    if (!terrainTags.isEmpty()) {
      summary.append(" (").append(String.join(", ", terrainTags)).append(")");
    }
    summary.append(" at (").append(row).append(", ").append(col).append(")")
        .append(" for ").append(totalCost).append(" money.").append(" [").append(enclosureId).append("]");

    log.info("Game {}: {} built enclosure {} size={} cost={}", request.gameId(), discordId,
        enclosureId, size, totalCost);

    return ActionResult.success(ActionCard.BUILD, strength, summary.toString(),
        Map.of("enclosure_id", enclosureId, "size", size, "money_spent", totalCost,
               "terrain_tags", terrainTags));
  }

  // ── Upgrade sub-action ────────────────────────────────────────────────────────

  private ActionResult executeUpgrade(ActionRequest request, PlayerState player,
      int strength, String targetCardName) {

    if (strength < 4) {
      return ActionResult.failure(
          "Upgrading an action card requires BUILD strength 4 or higher (current: " + strength + ").");
    }

    ActionCard target;
    try {
      target = ActionCard.valueOf(targetCardName.toUpperCase());
    } catch (IllegalArgumentException e) {
      return ActionResult.failure(
          "Unknown action card: " + targetCardName + ". Valid options: " +
          "CARDS, BUILD, ANIMALS, ASSOCIATION, SPONSOR.");
    }

    if (player.getActionCardOrder().isUpgraded(target)) {
      return ActionResult.failure(target.displayName() + " is already upgraded.");
    }

    // Upgrade costs 4 money (standard rule)
    int cost = 4;
    if (player.getMoney() < cost) {
      return ActionResult.failure("Upgrading costs " + cost + " money, but you have " + player.getMoney() + ".");
    }

    player.setMoney(player.getMoney() - cost);
    var cardOrder = player.getActionCardOrder();
    cardOrder.upgrade(target);
    player.setActionCardOrder(cardOrder);

    String summary = request.discordName() + " upgraded **" + target.displayName() + "** (★) for "
        + cost + " money.";

    log.info("Game {}: {} upgraded {}", request.gameId(), request.discordId(), target);

    return ActionResult.success(ActionCard.BUILD, strength, summary,
        Map.of("upgraded_card", target.name(), "money_spent", cost));
  }

  // ── Board JSON helpers ────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private int existingEnclosureCount(PlayerState player) {
    try {
      Map<String, Object> board = objectMapper.readValue(player.getBoardState(),
          new TypeReference<>() {});
      List<?> enclosures = (List<?>) board.getOrDefault("enclosures", List.of());
      return enclosures.size();
    } catch (Exception e) {
      return 0;
    }
  }

  @SuppressWarnings("unchecked")
  private boolean enclosureExists(PlayerState player, int row, int col) {
    try {
      Map<String, Object> board = objectMapper.readValue(player.getBoardState(),
          new TypeReference<>() {});
      List<Map<String, Object>> enclosures =
          (List<Map<String, Object>>) board.getOrDefault("enclosures", List.of());
      return enclosures.stream().anyMatch(enc ->
          row == ((Number) enc.getOrDefault("row", -1)).intValue() &&
          col == ((Number) enc.getOrDefault("col", -1)).intValue());
    } catch (Exception e) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private void addEnclosureToBoard(PlayerState player, String enclosureId, int size,
      int row, int col, List<String> tags) {
    try {
      Map<String, Object> board = objectMapper.readValue(player.getBoardState(),
          new TypeReference<>() {});
      List<Map<String, Object>> enclosures = new ArrayList<>(
          (List<Map<String, Object>>) board.getOrDefault("enclosures", List.of()));

      Map<String, Object> enc = new HashMap<>();
      enc.put("id", enclosureId);
      enc.put("size", size);
      enc.put("row", row);
      enc.put("col", col);
      enc.put("tags", tags);
      enc.put("animalCardIds", List.of());
      enclosures.add(enc);

      board.put("enclosures", enclosures);
      player.setBoardState(objectMapper.writeValueAsString(board));
    } catch (Exception e) {
      log.error("Failed to update board state for player {}", player.getDiscordId(), e);
    }
  }

  static int maxSizeForStrength(int strength, boolean upgraded) {
    int base = (strength >= 1 && strength <= 5) ? MAX_SIZE_BY_STRENGTH[strength] : 0;
    return upgraded ? base + 1 : base;
  }
}
