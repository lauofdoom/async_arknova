package com.arknova.bot.engine.action;

import static com.arknova.bot.engine.action.CardsActionHandler.BREAK_VALUE;
import static com.arknova.bot.engine.action.CardsActionHandler.drawConfig;
import static com.arknova.bot.engine.action.CardsActionHandler.snapAvailable;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.ActionCardOrder;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.service.DeckService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardsActionHandler")
class CardsActionHandlerTest {

  @Mock DeckService deckService;

  @InjectMocks CardsActionHandler handler;

  private PlayerState player;
  private SharedBoardState sharedBoard;
  private UUID gameId;

  @BeforeEach
  void setUp() {
    gameId = UUID.randomUUID();
    player = new PlayerState();
    player.setDiscordId("player1");
    player.setDiscordName("Alice");
    player.setReputation(0);
    // Default order: CARDS(1) BUILD(2) ANIMALS(3) ASSOCIATION(4) SPONSOR(5)

    sharedBoard = new SharedBoardState();
    sharedBoard.setGameId(gameId);
  }

  private ActionRequest req(Map<String, Object> params) {
    return new ActionRequest(gameId, "player1", "Alice", ActionCard.CARDS, params, null);
  }

  // ── drawConfig() ─────────────────────────────────────────────────────────────
  // Per card: [drawCount, handDiscardCount]
  // Unupgraded: draw grows evenly, hand discard at odd strengths
  // Upgraded: +1 draw vs unupgraded at each strength, snap available S3+

  @Nested
  @DisplayName("drawConfig() — unupgraded")
  class DrawConfigBase {
    @Test @DisplayName("S1: draw 1, discard 1 from hand")
    void s1() { assertThat(drawConfig(1, false)).containsExactly(1, 1); }

    @Test @DisplayName("S2: draw 1, no discard")
    void s2() { assertThat(drawConfig(2, false)).containsExactly(1, 0); }

    @Test @DisplayName("S3: draw 2, discard 1 from hand")
    void s3() { assertThat(drawConfig(3, false)).containsExactly(2, 1); }

    @Test @DisplayName("S4: draw 2, no discard")
    void s4() { assertThat(drawConfig(4, false)).containsExactly(2, 0); }

    @Test @DisplayName("S5: draw 3, discard 1 from hand")
    void s5() { assertThat(drawConfig(5, false)).containsExactly(3, 1); }
  }

  @Nested
  @DisplayName("drawConfig() — upgraded")
  class DrawConfigUpgraded {
    @Test @DisplayName("S1: draw 1, no discard")
    void s1() { assertThat(drawConfig(1, true)).containsExactly(1, 0); }

    @Test @DisplayName("S2: draw 2, discard 1 from hand")
    void s2() { assertThat(drawConfig(2, true)).containsExactly(2, 1); }

    @Test @DisplayName("S3: draw 2, no discard")
    void s3() { assertThat(drawConfig(3, true)).containsExactly(2, 0); }

    @Test @DisplayName("S4: draw 3, discard 1 from hand")
    void s4() { assertThat(drawConfig(4, true)).containsExactly(3, 1); }

    @Test @DisplayName("S5: draw 4, discard 1 from hand")
    void s5() { assertThat(drawConfig(5, true)).containsExactly(4, 1); }
  }

  @Nested
  @DisplayName("snapAvailable()")
  class SnapAvailableTest {
    @Test @DisplayName("unupgraded: snap only at S5")
    void baseSnap() {
      assertThat(snapAvailable(1, false)).isFalse();
      assertThat(snapAvailable(4, false)).isFalse();
      assertThat(snapAvailable(5, false)).isTrue();
    }

    @Test @DisplayName("upgraded: snap at S3, S4, S5")
    void upgradedSnap() {
      assertThat(snapAvailable(1, true)).isFalse();
      assertThat(snapAvailable(2, true)).isFalse();
      assertThat(snapAvailable(3, true)).isTrue();
      assertThat(snapAvailable(4, true)).isTrue();
      assertThat(snapAvailable(5, true)).isTrue();
    }
  }

  // ── BREAK option ─────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("BREAK")
  class Break {
    @Test
    @DisplayName("break gains fixed 2 money regardless of strength")
    void breakGainsFixedMoney() {
      player.setMoney(10);
      setStrength(player, ActionCard.CARDS, 5); // strength doesn't matter

      ActionResult result = handler.execute(req(Map.of("action", "BREAK")), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(12); // 10 + 2
      assertThat((Integer) result.deltas().get("money_gained")).isEqualTo(BREAK_VALUE);
    }
  }

  // ── SNAP option ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("SNAP")
  class Snap {
    @Test
    @DisplayName("snap succeeds at S5 unupgraded and flags manual resolution")
    void snapAtS5() {
      setStrength(player, ActionCard.CARDS, 5);

      ActionResult result = handler.execute(req(Map.of("action", "SNAP")), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(result.requiresManualResolution()).isTrue();
    }

    @Test
    @DisplayName("snap rejected at S4 unupgraded")
    void snapNotAvailableAtS4() {
      setStrength(player, ActionCard.CARDS, 4);

      ActionResult result = handler.execute(req(Map.of("action", "SNAP")), player, sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("not available");
    }

    @Test
    @DisplayName("snap accepted at S3 upgraded")
    void snapAtS3Upgraded() {
      setStrengthUpgraded(player, ActionCard.CARDS, 3);

      ActionResult result = handler.execute(req(Map.of("action", "SNAP")), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(result.requiresManualResolution()).isTrue();
    }
  }

  // ── Validation ────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("display takes rejected on un-upgraded card")
    void displayRequiresUpgrade() {
      // CARDS at S1 un-upgraded
      ActionResult result = handler.execute(
          req(Map.of("display_card_ids", List.of("card_lion"))), player, sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("upgraded");
    }

    @Test
    @DisplayName("fails when taking more display cards than drawCount allows")
    void tooManyDisplayCards() {
      setStrengthUpgraded(player, ActionCard.CARDS, 1); // S1 upgraded: draw=1, so max 1 display take

      player.setReputation(5);

      ActionResult result = handler.execute(
          req(Map.of("display_card_ids", List.of("c1", "c2"))), player, sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("at most 1");
    }

    @Test
    @DisplayName("fails when taking display card with insufficient reputation")
    void insufficientReputation() {
      setStrengthUpgraded(player, ActionCard.CARDS, 2);
      player.setReputation(2); // slot 5 requires rep ≥ 4

      PlayerCard pc = displayCard("card_lion", 5); // sortOrder = 4
      when(deckService.getDisplay(gameId)).thenReturn(List.of(
          displayCard("c1", 1), displayCard("c2", 2), displayCard("c3", 3),
          displayCard("c4", 4), pc, displayCard("c6", 6)));

      ActionResult result = handler.execute(
          req(Map.of("display_card_ids", List.of("card_lion"))), player, sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("reputation");
    }

    @Test
    @DisplayName("S1 unupgraded: draw succeeds, sets pendingDiscardCount=1 (two-phase flow)")
    void strength1DrawSetsPendingDiscard() {
      // S1 unupgraded: draw 1, discard 1 from hand — but discard happens in a separate command
      when(deckService.drawFromDeck(gameId, "player1", 1)).thenReturn(List.of("c1"));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of(makeHandCard("c1")));

      ActionResult result = handler.execute(req(Map.of()), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getPendingDiscardCount()).isEqualTo(1);
      assertThat(result.summary()).containsIgnoringCase("discard");
      verify(deckService, never()).discardFromHand(any(), any(), any());
    }

    @Test
    @DisplayName("fails when discard_ids provided but strength requires no discard")
    void unexpectedDiscard() {
      setStrength(player, ActionCard.CARDS, 2); // S2 unupgraded: draw 1, no discard
      when(deckService.drawFromDeck(gameId, "player1", 1)).thenReturn(List.of("c1"));

      ActionResult result = handler.execute(
          req(Map.of("discard_ids", List.of("c1"))), player, sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("no discard required");
    }
  }

  // ── Happy paths ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("happy paths")
  class HappyPaths {

    @Test
    @DisplayName("S1 unupgraded: draws 1, sets pendingDiscardCount=1 (discard deferred)")
    void strength1DrawDiscardFromHand() {
      // S1 unupgraded: draw 1 from deck, discard 1 from hand via /arknova discard afterward
      when(deckService.drawFromDeck(gameId, "player1", 1)).thenReturn(List.of("c1"));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of(makeHandCard("c1")));

      ActionResult result = handler.execute(req(Map.of()), player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService).drawFromDeck(gameId, "player1", 1);
      // No discard yet — deferred to the discard command
      verify(deckService, never()).discardFromHand(any(), any(), any());
      assertThat(player.getPendingDiscardCount()).isEqualTo(1);
      assertThat(result.drawnCardIds()).contains("c1");
    }

    @Test
    @DisplayName("S2 unupgraded: draws 1, no discard")
    void strength2DrawNoDiscard() {
      setStrength(player, ActionCard.CARDS, 2);
      when(deckService.drawFromDeck(gameId, "player1", 1)).thenReturn(List.of("c1"));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of(makeHandCard("c1")));

      ActionResult result = handler.execute(req(Map.of()), player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService, never()).discardFromHand(any(), any(), any());
      assertThat(result.drawnCardIds()).containsExactly("c1");
    }

    @Test
    @DisplayName("S1 upgraded: draws 1 from deck, no discard")
    void strength1UpgradedDrawNoDiscard() {
      setStrengthUpgraded(player, ActionCard.CARDS, 1);
      when(deckService.drawFromDeck(gameId, "player1", 1)).thenReturn(List.of("c1"));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of(makeHandCard("c1")));

      ActionResult result = handler.execute(req(Map.of()), player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService, never()).discardFromHand(any(), any(), any());
    }

    @Test
    @DisplayName("upgraded: takes 1 from display (within reputation range)")
    void upgradedTakeFromDisplay() {
      setStrengthUpgraded(player, ActionCard.CARDS, 1); // draw=1, display allowed
      player.setReputation(0); // slot 1 (sortOrder=0) is free

      PlayerCard pc = displayCard("card_lion", 1); // sortOrder=0
      when(deckService.getDisplay(gameId)).thenReturn(List.of(pc));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of(makeHandCard("card_lion")));

      ActionResult result = handler.execute(
          req(Map.of("display_card_ids", List.of("card_lion"))), player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService).takeFromDisplay(gameId, "player1", "card_lion");
      verify(deckService, never()).drawFromDeck(any(), any(), anyInt());
    }

    @Test
    @DisplayName("upgraded: mixes display take + deck draw, sets pendingDiscardCount=1 (deferred)")
    void upgradedMixDisplayAndDeck() {
      setStrengthUpgraded(player, ActionCard.CARDS, 2); // draw=2, hand_discard=1
      player.setReputation(5);

      PlayerCard pc = displayCard("display_card", 1);
      when(deckService.getDisplay(gameId)).thenReturn(List.of(pc));
      // Takes 1 from display + draws 1 from deck = 2 total, 1 discard deferred
      when(deckService.drawFromDeck(gameId, "player1", 1)).thenReturn(List.of("deck_card"));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of());

      ActionResult result = handler.execute(
          req(Map.of("display_card_ids", List.of("display_card"))),
          player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService).takeFromDisplay(gameId, "player1", "display_card");
      verify(deckService).drawFromDeck(gameId, "player1", 1);
      // Discard is deferred — not called here
      verify(deckService, never()).discardFromHand(any(), any(), any());
      assertThat(player.getPendingDiscardCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("empty deck: skips draw, no discard required")
    void emptyDeckSkipsDraw() {
      // S1: draw=1, hand_discard=1. But if deck is empty → nothing drawn → no discard required.
      when(deckService.drawFromDeck(gameId, "player1", 1)).thenReturn(List.of()); // empty deck
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of());

      ActionResult result = handler.execute(req(Map.of()), player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService, never()).discardFromHand(any(), any(), any());
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private void setStrength(PlayerState ps, ActionCard card, int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(card);
    cards.add(targetStrength - 1, card);
    ps.setActionCardOrder(new ActionCardOrder(cards, Set.of()));
  }

  private void setStrengthUpgraded(PlayerState ps, ActionCard card, int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(card);
    cards.add(targetStrength - 1, card);
    ps.setActionCardOrder(new ActionCardOrder(cards, Set.of(card)));
  }

  private PlayerCard displayCard(String cardId, int slot) {
    CardDefinition def = new CardDefinition();
    def.setId(cardId);
    def.setName("Card " + cardId);
    def.setCardType(CardDefinition.CardType.ANIMAL);
    PlayerCard pc = new PlayerCard();
    pc.setCard(def);
    pc.setSortOrder(slot - 1); // 0-based
    pc.setLocation(PlayerCard.CardLocation.DISPLAY);
    return pc;
  }

  private PlayerCard makeHandCard(String cardId) {
    CardDefinition def = new CardDefinition();
    def.setId(cardId);
    def.setName("Card " + cardId);
    def.setCardType(CardDefinition.CardType.ANIMAL);
    PlayerCard pc = new PlayerCard();
    pc.setCard(def);
    pc.setLocation(PlayerCard.CardLocation.HAND);
    return pc;
  }
}
