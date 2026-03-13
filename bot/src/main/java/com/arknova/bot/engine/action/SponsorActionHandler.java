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
import com.arknova.bot.repository.PlayerCardRepository;
import com.arknova.bot.service.DeckService;
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
 * Handles the SPONSOR action — play sponsor cards from hand, or break for money.
 *
 * <h2>Card rules</h2>
 *
 * <pre>
 * Unupgraded (I): Play 1 sponsor card with a maximum level of X from your hand.
 *                 OR: Break X → gain X money.
 *
 * Upgraded   (II): Play 1 or more sponsors cards with a maximum level of X+1
 *                  from your hand OR from within reputation range (with additional costs).
 *                  OR: Break X → gain 2×X money.
 * </pre>
 *
 * X = current strength of this action card (1–5). A card's "level" = its {@code base_cost}.
 *
 * <h2>Request parameters — play sponsors</h2>
 *
 * <ul>
 *   <li>{@code "card_ids"} — ordered list of sponsor card IDs from hand to play
 *   <li>{@code "display_card_ids"} — sponsor card IDs from the display (upgraded only)
 * </ul>
 *
 * <h2>Request parameters — break</h2>
 *
 * <ul>
 *   <li>{@code "break"} — set to {@code true} to break instead of playing cards
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SponsorActionHandler implements ActionHandler {

  private static final Logger log = LoggerFactory.getLogger(SponsorActionHandler.class);

  private final DeckService deckService;
  private final PlayerCardRepository playerCardRepo;
  private final EffectExecutor effectExecutor;

  @Override
  public ActionCard getActionCard() {
    return ActionCard.SPONSOR;
  }

  @Override
  public ActionResult execute(
      ActionRequest request, PlayerState player, SharedBoardState sharedBoard) {

    int strength = player.getStrengthOf(ActionCard.SPONSOR);
    boolean upgraded = player.getActionCardOrder().isUpgraded(ActionCard.SPONSOR);
    UUID gameId = request.gameId();
    String discordId = request.discordId();

    // ── Break option ─────────────────────────────────────────────────────────
    boolean isBreak = Boolean.parseBoolean(request.paramStr("break"));
    if (!isBreak) {
      // Also treat "break" param = "true" string
      Object breakParam = request.params().get("break");
      isBreak =
          Boolean.TRUE.equals(breakParam) || "true".equalsIgnoreCase(String.valueOf(breakParam));
    }

    if (isBreak) {
      return executeBreak(request, player, strength, upgraded);
    }

    // ── Play sponsors ────────────────────────────────────────────────────────
    List<String> handCardIds = request.paramList("card_ids");
    List<String> displayCardIds = request.paramList("display_card_ids");

    if (handCardIds.isEmpty() && displayCardIds.isEmpty()) {
      return ActionResult.failure(
          "Please specify sponsor cards to play (param: card_ids) or choose to break "
              + "(param: break=true).");
    }
    if (!displayCardIds.isEmpty() && !upgraded) {
      return ActionResult.failure(
          "Taking sponsors from the display requires the upgraded SPONSOR card.");
    }

    // Level cap: unupgraded ≤ X, upgraded ≤ X+1
    int maxLevel = upgraded ? strength + 1 : strength;

    // Load hand and display
    List<PlayerCard> hand =
        handCardIds.isEmpty()
            ? List.of()
            : playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
                gameId, discordId, CardLocation.HAND);
    List<PlayerCard> display =
        displayCardIds.isEmpty() ? List.of() : deckService.getDisplay(gameId);

    // Validate all cards before mutating (fail-fast)
    List<SponsorPlay> plays = new ArrayList<>();
    int pendingCost = 0;

    for (String cardId : handCardIds) {
      PlayerCard pc =
          hand.stream().filter(c -> c.getCard().getId().equals(cardId)).findFirst().orElse(null);
      if (pc == null) {
        return ActionResult.failure("Card " + cardId + " is not in your hand.");
      }
      ActionResult err =
          validateSponsor(
              pc.getCard(),
              cardId,
              maxLevel,
              strength,
              player.getMoney() - pendingCost,
              plays,
              false,
              0);
      if (err != null) return err;
      pendingCost += pc.getCard().getBaseCost();
      plays.add(new SponsorPlay(cardId, pc.getCard(), pc.getCard().getBaseCost(), false));
    }

    for (String cardId : displayCardIds) {
      PlayerCard pc =
          display.stream().filter(c -> c.getCard().getId().equals(cardId)).findFirst().orElse(null);
      if (pc == null) {
        return ActionResult.failure("Card " + cardId + " is not in the display.");
      }
      // Reputation check per slot. Slots 5-6 (index 4-5) also require the upgraded SPONSOR card.
      int slotIndex = pc.getSortOrder(); // 0-based
      int[] minRepPerSlot = {1, 2, 4, 7, 10, 13};
      if (slotIndex >= 4 && !upgraded) {
        return ActionResult.failure(
            pc.getCard().getName()
                + " is in display slot "
                + (slotIndex + 1)
                + " — accessing slots 5 and 6 requires the upgraded SPONSOR card.");
      }
      int requiredRep = slotIndex < minRepPerSlot.length ? minRepPerSlot[slotIndex] : 13;
      if (player.getReputation() < requiredRep) {
        return ActionResult.failure(
            pc.getCard().getName()
                + " is in display slot "
                + (slotIndex + 1)
                + " which requires reputation "
                + requiredRep
                + " (you have "
                + player.getReputation()
                + ").");
      }
      int cost = pc.getCard().getBaseCost() + (slotIndex + 1); // base cost + slot number (1-based)
      ActionResult err =
          validateSponsor(
              pc.getCard(),
              cardId,
              maxLevel,
              strength,
              player.getMoney() - pendingCost,
              plays,
              true,
              cost);
      if (err != null) return err;
      pendingCost += cost;
      plays.add(new SponsorPlay(cardId, pc.getCard(), cost, true));
    }

    // ── Apply state changes ───────────────────────────────────────────────────
    int totalCost = 0;
    int totalAppeal = 0;
    int totalConservation = 0;
    int totalReputation = 0;
    // Accumulated deltas from automated card effects across all played cards
    Map<String, Integer> totalFxDeltas = new HashMap<>();
    boolean anyManual = false;
    String firstManualId = null;

    for (SponsorPlay p : plays) {
      player.setMoney(player.getMoney() - p.cost());
      totalCost += p.cost();
      totalAppeal += p.cardDef().getAppealValue();
      totalConservation += p.cardDef().getConservationValue();
      totalReputation += p.cardDef().getReputationValue();
      player.setAppeal(player.getAppeal() + p.cardDef().getAppealValue());
      player.setConservation(player.getConservation() + p.cardDef().getConservationValue());
      player.setReputation(player.getReputation() + p.cardDef().getReputationValue());

      if (p.fromDisplay()) {
        deckService.takeFromDisplay(gameId, discordId, p.cardId());
      }
      deckService.playSponsor(gameId, discordId, p.cardId());

      if (p.cardDef().isAutomated()) {
        Map<String, Integer> fx = effectExecutor.execute(p.cardDef(), player, sharedBoard);
        fx.forEach((res, amt) -> totalFxDeltas.merge(res, amt, Integer::sum));
      } else if (p.cardDef().requiresManualResolution() && p.cardDef().getAbilityText() != null) {
        anyManual = true;
        if (firstManualId == null) firstManualId = p.cardId();
      }
    }

    // ── Build summary message ─────────────────────────────────────────────────
    StringBuilder summary = new StringBuilder();
    summary.append(request.discordName()).append(" played ");
    for (int i = 0; i < plays.size(); i++) {
      SponsorPlay p = plays.get(i);
      if (i > 0) summary.append(" and ");
      summary
          .append("**")
          .append(p.cardDef().getName())
          .append("**")
          .append(" (lvl ")
          .append(p.cardDef().getBaseCost())
          .append(")")
          .append(" for ")
          .append(p.cost())
          .append("💰");
      if (p.fromDisplay()) summary.append(" (display)");
    }
    summary.append(".");
    if (totalAppeal > 0) summary.append(" +").append(totalAppeal).append(" appeal.");
    if (totalConservation > 0)
      summary.append(" +").append(totalConservation).append(" conservation.");
    if (totalReputation > 0) summary.append(" +").append(totalReputation).append(" reputation.");
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
    if (anyManual) summary.append(" ⚠️ Manual effect resolution required.");

    log.info(
        "Game {}: {} SPONSOR strength={} played={} cost={}",
        request.gameId(),
        discordId,
        strength,
        plays.size(),
        totalCost);

    return new ActionResult(
        true,
        null,
        ActionCard.SPONSOR,
        strength,
        summary.toString(),
        Map.of(
            "money_spent", totalCost,
            "appeal_gained", totalAppeal,
            "conservation_gained", totalConservation,
            "reputation_gained", totalReputation,
            "cards_played", plays.size(),
            "fx_money_gained", fxMoney,
            "fx_appeal_gained", fxAppeal,
            "fx_conservation_gained", fxConservation),
        List.of(),
        false,
        anyManual,
        firstManualId);
  }

  // ── Break ─────────────────────────────────────────────────────────────────────

  private ActionResult executeBreak(
      ActionRequest request, PlayerState player, int strength, boolean upgraded) {

    int gained = upgraded ? strength * 2 : strength;
    player.setMoney(player.getMoney() + gained);

    String summary =
        request.discordName()
            + " broke **Sponsor** (X="
            + strength
            + ") → +"
            + gained
            + "💰"
            + (upgraded ? " (2×X, upgraded)" : "")
            + ".";

    log.info(
        "Game {}: {} SPONSOR break strength={} gained={}",
        request.gameId(),
        request.discordId(),
        strength,
        gained);

    return ActionResult.success(
        ActionCard.SPONSOR, strength, summary, Map.of("money_gained", gained, "break", true));
  }

  // ── Validation ────────────────────────────────────────────────────────────────

  /** Returns a failure result if invalid, null if OK. */
  private ActionResult validateSponsor(
      CardDefinition def,
      String cardId,
      int maxLevel,
      int strength,
      int remainingMoney,
      List<SponsorPlay> pending,
      boolean fromDisplay,
      int displayCost) {

    if (def.getCardType() != CardDefinition.CardType.SPONSOR) {
      return ActionResult.failure(def.getName() + " is not a sponsor card.");
    }
    if (def.getBaseCost() > maxLevel) {
      return ActionResult.failure(
          def.getName()
              + " has level "
              + def.getBaseCost()
              + " but the maximum level you can play at strength "
              + strength
              + " is "
              + maxLevel
              + ".");
    }
    int cost = fromDisplay ? displayCost : def.getBaseCost();
    if (remainingMoney < cost) {
      return ActionResult.failure(
          def.getName()
              + " costs "
              + cost
              + "💰 but you only have "
              + remainingMoney
              + " remaining.");
    }
    if (pending.stream().anyMatch(p -> p.cardId().equals(cardId))) {
      return ActionResult.failure("Card " + cardId + " appears more than once in the request.");
    }
    return null;
  }

  /** Fully-validated, ready-to-apply sponsor play. */
  record SponsorPlay(String cardId, CardDefinition cardDef, int cost, boolean fromDisplay) {}
}
