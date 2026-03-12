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
 * Handles the BUILD action — construct enclosures and special buildings on the zoo board.
 *
 * <h2>Card rules</h2>
 * <pre>
 * Unupgraded (I): Build 1 building with a maximum size of X. Pay 2 per space.
 *                 Available: Kiosk, pavilion, standard enclosures, petting zoo.
 *
 * Upgraded   (II): Build 1 or more DIFFERENT buildings with a total maximum size of X.
 *                  Pay 2 per space.
 *                  Newly available: Large Bird Aviary, Reptile House.
 * </pre>
 *
 * X = current strength of this action card (1–5).
 *
 * <h2>Enclosure cost</h2>
 * Base cost = size × 2 money. E.g. size 3 = 6 money, size 5 = 10 money.
 * Optional terrain tags (WATER, ROCK) add +2 money each.
 *
 * <h2>Request parameters — single build (un-upgraded or upgraded building 1 enclosure)</h2>
 * <ul>
 *   <li>{@code "size"} — enclosure size 1–X
 *   <li>{@code "row"} — grid row (0-based)
 *   <li>{@code "col"} — grid column (0-based)
 *   <li>{@code "tags"} — optional terrain tags, e.g. ["WATER"]
 * </ul>
 *
 * <h2>Request parameters — multi-build (upgraded only, total size ≤ X)</h2>
 * <ul>
 *   <li>{@code "buildings"} — list of objects, each with "size", "row", "col", "tags"
 * </ul>
 *
 * <h2>Request parameters — upgrade action card (strength 4+)</h2>
 * <ul>
 *   <li>{@code "upgrade_action"} — name of the action card to upgrade (e.g. "ANIMALS")
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BuildActionHandler implements ActionHandler {

  private static final Logger log = LoggerFactory.getLogger(BuildActionHandler.class);

  /** Terrain tag names that add extra cost when added to an enclosure. */
  private static final List<String> TERRAIN_TAGS = List.of("WATER", "ROCK");

  /** Extra money cost per terrain tag. */
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
    boolean upgraded = player.getActionCardOrder().isUpgraded(ActionCard.BUILD);

    // ── Special case: upgrade an action card (strength 4+) ──────────────────
    String upgradeTarget = request.paramStr("upgrade_action");
    if (upgradeTarget != null) {
      return executeUpgrade(request, player, strength, upgradeTarget);
    }

    // ── Multi-build (upgraded only) ──────────────────────────────────────────
    List<Map<String, Object>> buildingsList = extractBuildingsList(request);
    if (buildingsList != null) {
      if (!upgraded) {
        return ActionResult.failure(
            "Multi-build requires the upgraded BUILD card (param: buildings).");
      }
      return executeMultiBuild(request, player, strength, buildingsList);
    }

    // ── Single build ─────────────────────────────────────────────────────────
    int size = request.paramInt("size", 0);
    int row  = request.paramInt("row", -1);
    int col  = request.paramInt("col", -1);
    List<String> terrainTags = request.paramList("tags").stream()
        .map(String::toUpperCase).filter(TERRAIN_TAGS::contains).toList();

    if (size < 1) {
      return ActionResult.failure(
          "Please specify an enclosure size (1–" + strength + ").");
    }
    if (row < 0 || col < 0) {
      return ActionResult.failure("Please specify a grid location (row and col).");
    }
    if (size > strength) {
      return ActionResult.failure(
          "At strength " + strength + " (X=" + strength + ") the maximum enclosure size is "
          + strength + " (requested size " + size + ").");
    }

    return buildSingle(request, player, strength, size, row, col, terrainTags,
        existingEnclosureCount(player));
  }

  // ── Multi-build ───────────────────────────────────────────────────────────────

  private ActionResult executeMultiBuild(ActionRequest request, PlayerState player,
      int strength, List<Map<String, Object>> buildings) {

    if (buildings.isEmpty()) {
      return ActionResult.failure("buildings list must not be empty.");
    }

    // Validate total size ≤ strength (X)
    int totalSize = buildings.stream()
        .mapToInt(b -> ((Number) b.getOrDefault("size", 0)).intValue()).sum();
    if (totalSize > strength) {
      return ActionResult.failure(
          "Total enclosure size " + totalSize + " exceeds the maximum of X=" + strength
          + " at current strength.");
    }

    // Pre-validate each building
    int baseCount = existingEnclosureCount(player);
    int pendingCost = 0;

    record BuildPlan(int size, int row, int col, List<String> tags, String enclosureId) {}
    List<BuildPlan> plans = new ArrayList<>();

    for (int i = 0; i < buildings.size(); i++) {
      Map<String, Object> b = buildings.get(i);
      int size = ((Number) b.getOrDefault("size", 0)).intValue();
      int row  = ((Number) b.getOrDefault("row",  -1)).intValue();
      int col  = ((Number) b.getOrDefault("col",  -1)).intValue();
      List<String> tags = extractTags(b);

      if (size < 1) {
        return ActionResult.failure("Building " + (i + 1) + ": size must be ≥ 1.");
      }
      if (row < 0 || col < 0) {
        return ActionResult.failure("Building " + (i + 1) + ": must specify row and col.");
      }
      if (enclosureExists(player, row, col)
          || plans.stream().anyMatch(p -> p.row() == row && p.col() == col)) {
        return ActionResult.failure(
            "Building " + (i + 1) + ": a structure already exists at (" + row + ", " + col + ").");
      }

      int baseCost    = size * 2;
      int terrainCost = tags.size() * TERRAIN_COST;
      pendingCost += baseCost + terrainCost;

      if (player.getMoney() < pendingCost) {
        return ActionResult.failure(
            "Insufficient money: total build cost so far is " + pendingCost
            + " but you only have " + player.getMoney() + ".");
      }

      plans.add(new BuildPlan(size, row, col, tags, "E" + (baseCount + i + 1)));
    }

    // Apply
    int totalCost = 0;
    StringBuilder summary = new StringBuilder();
    summary.append(request.discordName()).append(" built ");
    List<String> builtIds = new ArrayList<>();

    for (int i = 0; i < plans.size(); i++) {
      BuildPlan p = plans.get(i);
      int cost = (p.size() * 2) + (p.tags().size() * TERRAIN_COST);
      player.setMoney(player.getMoney() - cost);
      totalCost += cost;
      addEnclosureToBoard(player, p.enclosureId(), p.size(), p.row(), p.col(), p.tags());
      builtIds.add(p.enclosureId());
      if (i > 0) summary.append(" and ");
      summary.append("**size-").append(p.size()).append("** [").append(p.enclosureId()).append("]");
      if (!p.tags().isEmpty()) summary.append(" (").append(String.join(", ", p.tags())).append(")");
    }
    summary.append(" for ").append(totalCost).append("💰 (total size ").append(totalSize)
        .append("/").append(strength).append(").");

    log.info("Game {}: {} multi-built {} enclosure(s) total-size={} cost={}",
        request.gameId(), request.discordId(), plans.size(), totalSize, totalCost);

    return ActionResult.success(ActionCard.BUILD, strength, summary.toString(),
        Map.of("enclosure_ids", builtIds, "total_size", totalSize, "money_spent", totalCost));
  }

  // ── Single build ──────────────────────────────────────────────────────────────

  private ActionResult buildSingle(ActionRequest request, PlayerState player,
      int strength, int size, int row, int col, List<String> terrainTags, int baseCount) {

    int baseCost    = size * 2;
    int terrainCost = terrainTags.size() * TERRAIN_COST;
    int totalCost   = baseCost + terrainCost;

    if (player.getMoney() < totalCost) {
      return ActionResult.failure(
          "Building a size-" + size + " enclosure costs " + totalCost + " money"
          + (terrainCost > 0 ? " (base " + baseCost + " + " + terrainCost + " terrain)" : "")
          + ", but you only have " + player.getMoney() + ".");
    }
    if (enclosureExists(player, row, col)) {
      return ActionResult.failure(
          "Your zoo already has a structure at row " + row + ", col " + col + ".");
    }

    String enclosureId = "E" + (baseCount + 1);
    player.setMoney(player.getMoney() - totalCost);
    addEnclosureToBoard(player, enclosureId, size, row, col, terrainTags);

    StringBuilder summary = new StringBuilder();
    summary.append(request.discordName())
        .append(" built a **size-").append(size).append(" enclosure**");
    if (!terrainTags.isEmpty()) {
      summary.append(" (").append(String.join(", ", terrainTags)).append(")");
    }
    summary.append(" at (").append(row).append(", ").append(col).append(")")
        .append(" for ").append(totalCost).append("💰")
        .append(" [").append(enclosureId).append("].");

    log.info("Game {}: {} built {} size={} cost={}", request.gameId(), request.discordId(),
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
          "Unknown action card: " + targetCardName
          + ". Valid options: CARDS, BUILD, ANIMALS, ASSOCIATION, SPONSOR.");
    }

    if (player.getActionCardOrder().isUpgraded(target)) {
      return ActionResult.failure(target.displayName() + " is already upgraded.");
    }

    int cost = 4;
    if (player.getMoney() < cost) {
      return ActionResult.failure(
          "Upgrading costs " + cost + " money, but you have " + player.getMoney() + ".");
    }

    player.setMoney(player.getMoney() - cost);
    var cardOrder = player.getActionCardOrder();
    cardOrder.upgrade(target);
    player.setActionCardOrder(cardOrder);

    String summary = request.discordName() + " upgraded **" + target.displayName()
        + "** (★) for " + cost + "💰.";
    log.info("Game {}: {} upgraded {}", request.gameId(), request.discordId(), target);

    return ActionResult.success(ActionCard.BUILD, strength, summary,
        Map.of("upgraded_card", target.name(), "money_spent", cost));
  }

  // ── Board JSON helpers ────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private int existingEnclosureCount(PlayerState player) {
    try {
      Map<String, Object> board = objectMapper.readValue(
          player.getBoardState(), new TypeReference<>() {});
      return ((List<?>) board.getOrDefault("enclosures", List.of())).size();
    } catch (Exception e) {
      return 0;
    }
  }

  @SuppressWarnings("unchecked")
  private boolean enclosureExists(PlayerState player, int row, int col) {
    try {
      Map<String, Object> board = objectMapper.readValue(
          player.getBoardState(), new TypeReference<>() {});
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
      Map<String, Object> board = objectMapper.readValue(
          player.getBoardState(), new TypeReference<>() {});
      List<Map<String, Object>> enclosures = new ArrayList<>(
          (List<Map<String, Object>>) board.getOrDefault("enclosures", List.of()));

      Map<String, Object> enc = new HashMap<>();
      enc.put("id",           enclosureId);
      enc.put("size",         size);
      enc.put("row",          row);
      enc.put("col",          col);
      enc.put("tags",         tags);
      enc.put("animalCardIds", List.of());
      enclosures.add(enc);

      board.put("enclosures", enclosures);
      player.setBoardState(objectMapper.writeValueAsString(board));
    } catch (Exception e) {
      log.error("Failed to update board state for player {}", player.getDiscordId(), e);
    }
  }

  /**
   * Returns the maximum size of a single building at the given strength (X = strength value).
   * Upgrade status does not change the per-building size limit; it unlocks multi-build.
   */
  static int maxSizeForStrength(int strength) {
    return strength;
  }

  /** Kept for test backward-compatibility. Upgrade no longer changes max individual size. */
  static int maxSizeForStrength(int strength, boolean upgraded) {
    return strength;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> extractBuildingsList(ActionRequest request) {
    Object v = request.params().get("buildings");
    if (v instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map) {
      return (List<Map<String, Object>>) l;
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private List<String> extractTags(Map<String, Object> building) {
    Object v = building.get("tags");
    if (v instanceof List<?> l) {
      return l.stream().map(Object::toString).map(String::toUpperCase)
          .filter(TERRAIN_TAGS::contains).toList();
    }
    return List.of();
  }
}
