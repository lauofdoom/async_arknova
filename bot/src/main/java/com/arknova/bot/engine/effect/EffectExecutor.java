package com.arknova.bot.engine.effect;

import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Parses and executes machine-readable card effects stored in {@link
 * CardDefinition#getEffectCode()}.
 *
 * <p>Only {@code "ON_PLAY"} triggers are processed here (fired when a card enters the tableau).
 * Unknown triggers, types, and resources are logged as warnings and skipped — the engine degrades
 * gracefully rather than throwing.
 *
 * <p>Supported effect types:
 *
 * <ul>
 *   <li>{@code "GAIN"} — unconditionally add {@code amount} to the named resource.
 *   <li>{@code "CONDITIONAL_GAIN"} with condition {@code "MIN_ICON"} — apply the gain only when the
 *       player has at least {@code condition.count} icons of type {@code condition.icon}.
 *   <li>{@code "GAIN_PER_ICON"} with condition {@code "ICON"} — multiply {@code amount} by the
 *       player's count of {@code condition.icon}. Optional {@code condition.max} caps the count (0
 *       = no cap).
 *   <li>{@code "GAIN_PER_ICON"} — add {@code amount × iconCount} to the named resource, capped at
 *       {@code max} if {@code max > 0}.
 * </ul>
 *
 * <p>Supported resources: {@code MONEY}, {@code APPEAL}, {@code CONSERVATION}, {@code REPUTATION},
 * {@code X_TOKENS}, {@code BREAK_TRACK}.
 */
@Service
@RequiredArgsConstructor
public class EffectExecutor {

  private static final Logger log = LoggerFactory.getLogger(EffectExecutor.class);

  private final ObjectMapper objectMapper;

  /**
   * Executes all {@code ON_PLAY} effects from the card's {@code effect_code} against the given
   * player, mutating {@link PlayerState} in place.
   *
   * @param cardDef the card whose effects are to be applied; must satisfy {@link
   *     CardDefinition#isAutomated()}
   * @param player the player state to mutate
   * @param sharedBoard the shared board state (required for BREAK_TRACK resource)
   * @return a map of resource-name → delta for every resource that changed (e.g. {@code "money" →
   *     3}); entries with a delta of 0 are omitted
   */
  public Map<String, Integer> execute(
      CardDefinition cardDef, PlayerState player, SharedBoardState sharedBoard) {
    Map<String, Integer> deltas = new HashMap<>();

    List<CardEffect> effects = parseEffects(cardDef);
    if (effects.isEmpty()) {
      return deltas;
    }

    Map<String, Integer> iconCounts = parseIconCounts(player);

    for (CardEffect effect : effects) {
      if (!"ON_PLAY".equals(effect.trigger())) {
        log.warn(
            "EffectExecutor: card {} has unsupported trigger '{}' — skipping",
            cardDef.getId(),
            effect.trigger());
        continue;
      }

      switch (effect.type()) {
        case "GAIN" ->
            applyGain(
                cardDef.getId(), effect.resource(), effect.amount(), player, sharedBoard, deltas);

        case "CONDITIONAL_GAIN" -> {
          if (!evaluateCondition(cardDef.getId(), effect.condition(), iconCounts)) {
            log.debug(
                "EffectExecutor: card {} CONDITIONAL_GAIN condition not met — skipping",
                cardDef.getId());
          } else {
            applyGain(
                cardDef.getId(), effect.resource(), effect.amount(), player, sharedBoard, deltas);
          }
        }

        case "GAIN_PER_ICON" -> {
          CardEffectCondition cond = effect.condition();
          if (cond == null || cond.icon() == null) {
            log.warn(
                "EffectExecutor: card {} GAIN_PER_ICON missing condition.icon — skipping",
                cardDef.getId());
            break;
          }
          int iconCount = iconCounts.getOrDefault(cond.icon(), 0);
          int effectiveCount = (cond.max() > 0) ? Math.min(iconCount, cond.max()) : iconCount;
          if (effectiveCount > 0) {
            applyGain(
                cardDef.getId(),
                effect.resource(),
                effect.amount() * effectiveCount,
                player,
                sharedBoard,
                deltas);
          }
        }

        default ->
            log.warn(
                "EffectExecutor: card {} has unsupported effect type '{}' — skipping",
                cardDef.getId(),
                effect.type());
      }
    }

    return deltas;
  }

  // ── Parsing ───────────────────────────────────────────────────────────────────

  /**
   * Parses the {@code effect_code} JSONB string into a list of {@link CardEffect} records. Returns
   * an empty list if parsing fails (and logs a warning).
   */
  private List<CardEffect> parseEffects(CardDefinition cardDef) {
    String effectCode = cardDef.getEffectCode();
    if (effectCode == null || effectCode.isBlank()) {
      return List.of();
    }

    try {
      JsonNode root = objectMapper.readTree(effectCode);
      JsonNode abilitiesNode = root.path("abilities");
      if (!abilitiesNode.isArray()) {
        log.warn("EffectExecutor: card {} effect_code missing 'abilities' array", cardDef.getId());
        return List.of();
      }

      List<CardEffect> effects = new ArrayList<>();
      for (JsonNode node : abilitiesNode) {
        String trigger = node.path("trigger").asText(null);
        String type = node.path("type").asText(null);
        String resource = node.path("resource").asText(null);
        int amount = node.path("amount").asInt(0);
        String icon = node.path("icon").isMissingNode() ? null : node.path("icon").asText(null);
        int max = node.path("max").asInt(0);

        CardEffectCondition condition = null;
        JsonNode condNode = node.path("condition");
        if (!condNode.isMissingNode() && !condNode.isNull()) {
          String condType = condNode.path("type").asText(null);
          String condIcon = condNode.path("icon").asText(null);
          int count = condNode.path("count").asInt(0);
          int condMax = condNode.path("max").asInt(0);
          condition = new CardEffectCondition(condType, condIcon, count, condMax);
        }

        if (trigger == null || type == null) {
          log.warn(
              "EffectExecutor: card {} has effect entry missing trigger or type — skipping entry",
              cardDef.getId());
          continue;
        }

        effects.add(new CardEffect(trigger, type, resource, amount, icon, max, condition));
      }
      return effects;

    } catch (Exception e) {
      log.warn(
          "EffectExecutor: failed to parse effect_code for card {} — {}",
          cardDef.getId(),
          e.getMessage());
      return List.of();
    }
  }

  /**
   * Parses the player's {@code icons} JSONB column into a {@code Map<String, Integer>}. The icons
   * field stores counts per tag type, e.g. {@code {"PREDATOR": 3, "AFRICA": 1}}. Returns an empty
   * map if parsing fails.
   */
  private Map<String, Integer> parseIconCounts(PlayerState player) {
    String iconsJson = player.getIcons();
    if (iconsJson == null || iconsJson.isBlank() || "{}".equals(iconsJson.trim())) {
      return Map.of();
    }
    try {
      Map<String, Object> raw =
          objectMapper.readValue(iconsJson, new TypeReference<Map<String, Object>>() {});
      Map<String, Integer> result = new HashMap<>();
      for (Map.Entry<String, Object> entry : raw.entrySet()) {
        if (entry.getValue() instanceof Number num) {
          result.put(entry.getKey(), num.intValue());
        }
      }
      return result;
    } catch (Exception e) {
      log.warn(
          "EffectExecutor: failed to parse icons for player {} — {}",
          player.getDiscordId(),
          e.getMessage());
      return Map.of();
    }
  }

  // ── Condition evaluation ──────────────────────────────────────────────────────

  /**
   * Returns {@code true} if the condition is satisfied by the player's current icon counts. Logs a
   * warning and returns {@code false} for unrecognised condition types.
   */
  private boolean evaluateCondition(
      String cardId, CardEffectCondition condition, Map<String, Integer> iconCounts) {
    if (condition == null) {
      log.warn("EffectExecutor: card {} has CONDITIONAL_GAIN with null condition", cardId);
      return false;
    }

    if ("MIN_ICON".equals(condition.type())) {
      String icon = condition.icon();
      int required = condition.count();
      int actual = iconCounts.getOrDefault(icon, 0);
      return actual >= required;
    }

    log.warn(
        "EffectExecutor: card {} has unsupported condition type '{}' — skipping",
        cardId,
        condition.type());
    return false;
  }

  // ── Resource application ──────────────────────────────────────────────────────

  /**
   * Adds {@code amount} to the named resource on {@code player} and accumulates the delta. Logs a
   * warning for unrecognised resource names.
   */
  private void applyGain(
      String cardId,
      String resource,
      int amount,
      PlayerState player,
      SharedBoardState sharedBoard,
      Map<String, Integer> deltas) {

    if (resource == null) {
      log.warn("EffectExecutor: card {} has GAIN with null resource — skipping", cardId);
      return;
    }

    switch (resource) {
      case "MONEY" -> {
        player.setMoney(player.getMoney() + amount);
        deltas.merge("money", amount, Integer::sum);
      }
      case "APPEAL" -> {
        player.setAppeal(player.getAppeal() + amount);
        deltas.merge("appeal", amount, Integer::sum);
      }
      case "CONSERVATION" -> {
        player.setConservation(player.getConservation() + amount);
        deltas.merge("conservation", amount, Integer::sum);
      }
      case "REPUTATION" -> {
        player.setReputation(player.getReputation() + amount);
        deltas.merge("reputation", amount, Integer::sum);
      }
      case "X_TOKENS" -> {
        player.setXTokens(player.getXTokens() + amount);
        deltas.merge("x_tokens", amount, Integer::sum);
      }
      case "BREAK_TRACK" -> {
        if (sharedBoard != null) {
          sharedBoard.setBreakTrack(sharedBoard.getBreakTrack() + amount);
          deltas.merge("break_track", amount, Integer::sum);
        } else {
          log.warn(
              "EffectExecutor: card {} BREAK_TRACK effect but sharedBoard is null — skipping",
              cardId);
        }
      }
      default ->
          log.warn(
              "EffectExecutor: card {} has unsupported resource '{}' — skipping", cardId, resource);
    }
  }
}
