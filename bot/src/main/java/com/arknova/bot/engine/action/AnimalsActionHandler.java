package com.arknova.bot.engine.action;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.effect.EffectExecutor;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerCard.CardLocation;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.repository.CardDefinitionRepository;
import com.arknova.bot.repository.PlayerCardRepository;
import com.arknova.bot.service.DeckService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles the ANIMALS action — place animal cards from hand (and optionally display) into
 * enclosures.
 *
 * <h2>Placement limits by strength</h2>
 *
 * <pre>
 * ─────────────────────────────────────────────────────────
 *           │  S1  │  S2  │  S3  │  S4  │  S5
 * ──────────┼──────┼──────┼──────┼──────┼──────
 * Standard  │   0  │   1  │   1  │   1  │   2
 * Upgraded  │   1  │   1  │   2  │   2  │   3
 * ─────────────────────────────────────────────────────────
 * </pre>
 *
 * <h2>Card sources</h2>
 *
 * <ul>
 *   <li><b>Standard</b>: all placements must come from the player's <em>hand</em>.
 *   <li><b>Upgraded</b>: each placement slot may come from the player's <em>hand</em> <em>or</em>
 *       from the shared display (within reputation range), with the display card's slot cost added
 *       on top of the printed cost.
 * </ul>
 *
 * <h2>Placement validation (per card)</h2>
 *
 * <ul>
 *   <li>The card must be in the player's hand (or in the display if upgraded).
 *   <li>The card type must be ANIMAL.
 *   <li>The target enclosure must exist on the player's board.
 *   <li>The enclosure size ≥ the animal's {@code min_enclosure_size}.
 *   <li>The enclosure must not be full.
 *   <li>The enclosure terrain tags must satisfy the animal's {@code requirements}.
 *   <li>The player must have enough money for the (possibly discounted) cost.
 * </ul>
 *
 * <h2>Request parameters</h2>
 *
 * <ul>
 *   <li>{@code "hand_card_ids"} — ordered list of card IDs to take from hand (may be empty)
 *   <li>{@code "hand_enc_ids"} — parallel enclosure IDs for hand cards
 *   <li>{@code "display_card_ids"} — card IDs to take from display (upgraded only; may be empty)
 *   <li>{@code "display_enc_ids"} — parallel enclosure IDs for display cards
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AnimalsActionHandler implements ActionHandler {

  private static final Logger log = LoggerFactory.getLogger(AnimalsActionHandler.class);

  private final DeckService deckService;
  private final PlayerCardRepository playerCardRepo;
  private final CardDefinitionRepository cardDefRepo;
  private final ObjectMapper objectMapper;
  private final EffectExecutor effectExecutor;

  @Override
  public ActionCard getActionCard() {
    return ActionCard.ANIMALS;
  }

  @Override
  public ActionResult execute(
      ActionRequest request, PlayerState player, SharedBoardState sharedBoard) {

    int strength = player.getStrengthOf(ActionCard.ANIMALS);
    boolean upgraded = player.getActionCardOrder().isUpgraded(ActionCard.ANIMALS);
    String discordId = request.discordId();
    UUID gameId = request.gameId();

    int maxTotal = maxAnimals(strength, upgraded);

    // ── Strength 1 un-upgraded: no placements ────────────────────────────────
    if (maxTotal == 0) {
      return ActionResult.success(
          ActionCard.ANIMALS,
          strength,
          request.discordName() + " used the Animals action at strength 1 — no animals placed.",
          Map.of("cards_placed", 0));
    }

    List<String> handCardIds = request.paramList("hand_card_ids");
    List<String> handEncIds = request.paramList("hand_enc_ids");
    List<String> displayCardIds = request.paramList("display_card_ids");
    List<String> displayEncIds = request.paramList("display_enc_ids");

    if (handCardIds.size() != handEncIds.size()) {
      return ActionResult.failure("hand_card_ids and hand_enc_ids must have the same length.");
    }
    if (displayCardIds.size() != displayEncIds.size()) {
      return ActionResult.failure(
          "display_card_ids and display_enc_ids must have the same length.");
    }
    if (!displayCardIds.isEmpty() && !upgraded) {
      return ActionResult.failure(
          "Placing animals from the display requires the upgraded ANIMALS card.");
    }

    int totalRequested = handCardIds.size() + displayCardIds.size();
    if (totalRequested == 0) {
      return ActionResult.failure(
          "Please provide at least one card to place (hand_card_ids or display_card_ids).");
    }
    if (totalRequested > maxTotal) {
      return ActionResult.failure(
          "At strength "
              + strength
              + (upgraded ? " (upgraded)" : "")
              + " you can place at most "
              + maxTotal
              + " animal(s) per action (requested "
              + totalRequested
              + ").");
    }

    // ── Gather cards from hand ───────────────────────────────────────────────
    List<PlayerCard> hand =
        handCardIds.isEmpty()
            ? List.of()
            : playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
                gameId, discordId, CardLocation.HAND);

    // ── Gather cards from display ────────────────────────────────────────────
    List<PlayerCard> display =
        displayCardIds.isEmpty() ? List.of() : deckService.getDisplay(gameId);

    // ── Build and validate all placements (fail-fast before any mutation) ────
    List<PlacementPlan> plans = new ArrayList<>();

    for (int i = 0; i < handCardIds.size(); i++) {
      String cardId = handCardIds.get(i);
      PlayerCard pc =
          hand.stream().filter(c -> c.getCard().getId().equals(cardId)).findFirst().orElse(null);
      if (pc == null) {
        return ActionResult.failure("Card " + cardId + " is not in your hand.");
      }
      ActionResult err =
          validatePlacement(
              pc.getCard(), handEncIds.get(i), player, plans, pc.getCard().getBaseCost());
      if (err != null) return err;
      plans.add(
          new PlacementPlan(
              cardId, handEncIds.get(i), pc.getCard(), pc.getCard().getBaseCost(), false));
    }

    for (int i = 0; i < displayCardIds.size(); i++) {
      String cardId = displayCardIds.get(i);
      PlayerCard pc =
          display.stream().filter(c -> c.getCard().getId().equals(cardId)).findFirst().orElse(null);
      if (pc == null) {
        return ActionResult.failure("Card " + cardId + " is not in the display.");
      }
      // Reputation check: slot index (0-based sortOrder) must be within rep range.
      // Slots 5-6 (index 4-5) also require the upgraded ANIMALS card.
      int slotIndex = pc.getSortOrder(); // 0-based
      int[] minRepPerSlot = {1, 2, 4, 7, 10, 13};
      if (slotIndex >= 4 && !upgraded) {
        return ActionResult.failure(
            pc.getCard().getName()
                + " is in display slot "
                + (slotIndex + 1)
                + " — accessing slots 5 and 6 requires the upgraded ANIMALS card.");
      }
      int requiredRep = slotIndex < minRepPerSlot.length ? minRepPerSlot[slotIndex] : 13;
      if (player.getReputation() < requiredRep) {
        return ActionResult.failure(
            "Taking "
                + pc.getCard().getName()
                + " from display slot "
                + (slotIndex + 1)
                + " requires reputation "
                + requiredRep
                + " (you have "
                + player.getReputation()
                + ").");
      }
      int cost = pc.getCard().getBaseCost() + (slotIndex + 1); // base cost + slot number (1-based)
      ActionResult err = validatePlacement(pc.getCard(), displayEncIds.get(i), player, plans, cost);
      if (err != null) return err;
      plans.add(new PlacementPlan(cardId, displayEncIds.get(i), pc.getCard(), cost, true));
    }

    // ── Apply state changes ──────────────────────────────────────────────────
    int totalCost = 0;
    int totalAppeal = 0;
    int totalConservation = 0;
    // Accumulated deltas from automated card effects across all placed cards
    Map<String, Integer> totalFxDeltas = new HashMap<>();
    boolean anyManualResolution = false;
    String manualCardId = null;

    for (PlacementPlan plan : plans) {
      player.setMoney(player.getMoney() - plan.cost());
      totalCost += plan.cost();

      int appealGain = plan.cardDef().getAppealValue();
      int conservationGain = plan.cardDef().getConservationValue();
      player.setAppeal(player.getAppeal() + appealGain);
      player.setConservation(player.getConservation() + conservationGain);
      totalAppeal += appealGain;
      totalConservation += conservationGain;

      // Update icon counts before executing effects so CONDITIONAL_GAIN sees the new icons
      updateIcons(player, plan.cardDef().getTagList());

      if (plan.fromDisplay()) {
        deckService.takeFromDisplay(gameId, discordId, plan.cardId());
        // takeFromDisplay moves card to hand first; then we place it
        deckService.placeAnimal(gameId, discordId, plan.cardId(), plan.enclosureId());
      } else {
        deckService.placeAnimal(gameId, discordId, plan.cardId(), plan.enclosureId());
      }
      addAnimalToEnclosure(player, plan.enclosureId(), plan.cardId());

      if (plan.cardDef().isAutomated()) {
        Map<String, Integer> fx = effectExecutor.execute(plan.cardDef(), player);
        fx.forEach((res, amt) -> totalFxDeltas.merge(res, amt, Integer::sum));
      } else if (plan.cardDef().requiresManualResolution()
          && plan.cardDef().getAbilityText() != null) {
        anyManualResolution = true;
        manualCardId = plan.cardId();
      }
    }

    // ── Build summary message ─────────────────────────────────────────────────
    StringBuilder summary = new StringBuilder();
    summary.append(request.discordName()).append(" placed ");
    for (int i = 0; i < plans.size(); i++) {
      PlacementPlan plan = plans.get(i);
      if (i > 0) summary.append(" and ");
      summary
          .append("**")
          .append(plan.cardDef().getName())
          .append("**")
          .append(" in ")
          .append(plan.enclosureId())
          .append(" for ")
          .append(plan.cost())
          .append("💰");
      if (plan.fromDisplay()) summary.append(" (display)");
    }
    summary.append(".");
    if (totalAppeal > 0) summary.append(" +").append(totalAppeal).append(" appeal.");
    if (totalConservation > 0)
      summary.append(" +").append(totalConservation).append(" conservation.");
    // Append automated effect gains
    int fxMoney = totalFxDeltas.getOrDefault("money", 0);
    int fxAppeal = totalFxDeltas.getOrDefault("appeal", 0);
    int fxConservation = totalFxDeltas.getOrDefault("conservation", 0);
    int fxReputation = totalFxDeltas.getOrDefault("reputation", 0);
    int fxXTokens = totalFxDeltas.getOrDefault("x_tokens", 0);
    if (fxMoney > 0) summary.append(" Gained ").append(fxMoney).append(" money from card effect.");
    if (fxAppeal > 0)
      summary.append(" Gained ").append(fxAppeal).append(" appeal from card effect.");
    if (fxConservation > 0)
      summary.append(" Gained ").append(fxConservation).append(" conservation from card effect.");
    if (fxReputation > 0)
      summary.append(" Gained ").append(fxReputation).append(" reputation from card effect.");
    if (fxXTokens > 0)
      summary.append(" Gained ").append(fxXTokens).append(" x_tokens from card effect.");
    if (anyManualResolution) summary.append(" ⚠️ Manual effect resolution required.");

    log.info(
        "Game {}: {} placed {} animal(s) for {} money total",
        gameId,
        discordId,
        plans.size(),
        totalCost);

    return new ActionResult(
        true,
        null,
        ActionCard.ANIMALS,
        strength,
        summary.toString(),
        Map.of(
            "money_spent",
            totalCost,
            "appeal_gained",
            totalAppeal,
            "conservation_gained",
            totalConservation,
            "cards_placed",
            plans.size(),
            "fx_money_gained",
            fxMoney,
            "fx_appeal_gained",
            fxAppeal),
        List.of(),
        false,
        anyManualResolution,
        manualCardId);
  }

  // ── Static helpers ────────────────────────────────────────────────────────────

  /**
   * Maximum total animals placeable per action at the given strength and upgrade state.
   *
   * <pre>
   *           │  S1  │  S2  │  S3  │  S4  │  S5
   * Standard  │   0  │   1  │   1  │   1  │   2
   * Upgraded  │   1  │   1  │   2  │   2  │   3
   * </pre>
   */
  static int maxAnimals(int strength, boolean upgraded) {
    if (upgraded) {
      return switch (strength) {
        case 1, 2 -> 1;
        case 3, 4 -> 2;
        case 5 -> 3;
        default -> 0;
      };
    }
    return switch (strength) {
      case 2, 3, 4 -> 1;
      case 5 -> 2;
      default -> 0;
    };
  }

  // ── Validation helper ────────────────────────────────────────────────────────

  /**
   * Validates a single placement. Returns a failure ActionResult if invalid, null if OK.
   *
   * @param cost pre-computed cost (may include slot premium for display cards)
   */
  private ActionResult validatePlacement(
      CardDefinition cardDef,
      String enclosureId,
      PlayerState player,
      List<PlacementPlan> pending,
      int cost) {

    if (cardDef.getCardType() != CardDefinition.CardType.ANIMAL) {
      return ActionResult.failure(cardDef.getName() + " is not an animal card.");
    }

    Enclosure enclosure = findEnclosure(player, enclosureId);
    if (enclosure == null) {
      return ActionResult.failure(
          "Enclosure " + enclosureId + " does not exist on your zoo board.");
    }

    int minSize = cardDef.getMinEnclosureSize() != null ? cardDef.getMinEnclosureSize() : 1;
    if (enclosure.size() < minSize) {
      return ActionResult.failure(
          cardDef.getName()
              + " requires an enclosure of size ≥ "
              + minSize
              + " (enclosure "
              + enclosureId
              + " is size "
              + enclosure.size()
              + ").");
    }

    // Capacity: count animals already committed in this action to the same enclosure
    int pendingInSameEnclosure =
        (int) pending.stream().filter(p -> p.enclosureId().equals(enclosureId)).count();
    int occupancy = enclosure.animalCardIds().size() + pendingInSameEnclosure;
    if (occupancy >= enclosure.size()) {
      return ActionResult.failure(
          "Enclosure "
              + enclosureId
              + " is full ("
              + enclosure.animalCardIds().size()
              + "/"
              + enclosure.size()
              + ").");
    }

    for (String req : cardDef.getRequirementList()) {
      if (!req.equals("PARTNER_ZOO") && !enclosure.tags().contains(req)) {
        return ActionResult.failure(
            cardDef.getName()
                + " requires a "
                + req
                + " enclosure, "
                + "but enclosure "
                + enclosureId
                + " does not have that terrain tag.");
      }
    }

    if (player.getMoney() < cost) {
      return ActionResult.failure(
          cardDef.getName()
              + " costs "
              + cost
              + " money, but you only have "
              + player.getMoney()
              + ".");
    }

    return null; // valid
  }

  // ── Board JSON helpers ────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private Enclosure findEnclosure(PlayerState player, String enclosureId) {
    try {
      Map<String, Object> board =
          objectMapper.readValue(player.getBoardState(), new TypeReference<>() {});
      List<Map<String, Object>> enclosures =
          (List<Map<String, Object>>) board.getOrDefault("enclosures", List.of());
      for (Map<String, Object> enc : enclosures) {
        if (enclosureId.equals(enc.get("id"))) {
          int size = ((Number) enc.getOrDefault("size", 0)).intValue();
          List<String> tags = (List<String>) enc.getOrDefault("tags", List.of());
          List<String> animalIds = (List<String>) enc.getOrDefault("animalCardIds", List.of());
          return new Enclosure(enclosureId, size, tags, animalIds);
        }
      }
    } catch (Exception e) {
      log.error("Failed to parse board state for player {}", player.getDiscordId(), e);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private void addAnimalToEnclosure(PlayerState player, String enclosureId, String cardId) {
    try {
      Map<String, Object> board =
          objectMapper.readValue(player.getBoardState(), new TypeReference<>() {});
      List<Map<String, Object>> enclosures =
          (List<Map<String, Object>>) board.getOrDefault("enclosures", List.of());
      for (Map<String, Object> enc : enclosures) {
        if (enclosureId.equals(enc.get("id"))) {
          List<String> animalIds =
              new ArrayList<>((List<String>) enc.getOrDefault("animalCardIds", List.of()));
          animalIds.add(cardId);
          enc.put("animalCardIds", animalIds);
          break;
        }
      }
      player.setBoardState(objectMapper.writeValueAsString(board));
    } catch (Exception e) {
      log.error("Failed to update board state for player {}", player.getDiscordId(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private void updateIcons(PlayerState player, List<String> newTags) {
    try {
      Map<String, Object> icons =
          objectMapper.readValue(player.getIcons(), new TypeReference<>() {});
      for (String tag : newTags) {
        int current = ((Number) icons.getOrDefault(tag, 0)).intValue();
        icons.put(tag, current + 1);
      }
      player.setIcons(objectMapper.writeValueAsString(icons));
    } catch (Exception e) {
      log.error("Failed to update icons for player {}", player.getDiscordId(), e);
    }
  }

  /** Value object for an enclosure parsed from board_state JSON. */
  record Enclosure(String id, int size, List<String> tags, List<String> animalCardIds) {}

  /** Fully-validated, ready-to-apply placement. */
  record PlacementPlan(
      String cardId, String enclosureId, CardDefinition cardDef, int cost, boolean fromDisplay) {}
}
