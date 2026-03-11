package com.arknova.bot.engine.action;

import static com.arknova.bot.engine.action.CardsActionHandler.drawConfig;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.ActionCardOrder;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.service.DeckService;
import java.util.List;
import java.util.Map;
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

  @Nested
  @DisplayName("drawConfig()")
  class DrawConfigTest {

    @Test @DisplayName("strength 1: draw 2, keep 1")
    void strength1() { assertThat(drawConfig(1, false)).containsExactly(2, 1); }

    @Test @DisplayName("strength 1 upgraded: draw 2, keep 2")
    void strength1Upgraded() { assertThat(drawConfig(1, true)).containsExactly(2, 2); }

    @Test @DisplayName("strength 2: draw 2, keep 2")
    void strength2() { assertThat(drawConfig(2, false)).containsExactly(2, 2); }

    @Test @DisplayName("strength 3: draw 3, keep 2")
    void strength3() { assertThat(drawConfig(3, false)).containsExactly(3, 2); }

    @Test @DisplayName("strength 3 upgraded: draw 3, keep 3")
    void strength3Upgraded() { assertThat(drawConfig(3, true)).containsExactly(3, 3); }

    @Test @DisplayName("strength 4: draw 0 (display only)")
    void strength4() { assertThat(drawConfig(4, false)).containsExactly(0, 0); }

    @Test @DisplayName("strength 5: draw 2, keep 2")
    void strength5() { assertThat(drawConfig(5, false)).containsExactly(2, 2); }
  }

  // ── Validation ────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("fails when taking more display cards than strength allows")
    void tooManyDisplayCards() {
      // CARDS is at strength 1 by default — display takes not allowed
      ActionResult result = handler.execute(
          req(Map.of("display_card_ids", List.of("card1"), "draw_count", 0)),
          player, sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("at most 0");
    }

    @Test
    @DisplayName("fails when taking display card with insufficient reputation")
    void insufficientReputation() {
      // Put CARDS at strength 4 (move it to position 4)
      setStrength(player, ActionCard.CARDS, 4);
      player.setReputation(2); // needs 4 for slot 5

      // Mock a display with the card at slot 5
      PlayerCard pc = displayCard("card_lion", 5);
      when(deckService.getDisplay(gameId)).thenReturn(List.of(
          displayCard("c1", 1), displayCard("c2", 2), displayCard("c3", 3),
          displayCard("c4", 4), pc, displayCard("c6", 6)));

      ActionResult result = handler.execute(
          req(Map.of("display_card_ids", List.of("card_lion"))),
          player, sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("reputation");
    }

    @Test
    @DisplayName("fails when discard count doesn't match strength 1 requirement")
    void wrongDiscardCount_strength1() {
      // Strength 1: draw 2 keep 1 — must discard 1
      when(deckService.deckSize(gameId)).thenReturn(10);
      when(deckService.drawFromDeck(gameId, "player1", 2)).thenReturn(List.of("c1", "c2"));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of());

      // Provide 0 discards when 1 is required
      ActionResult result = handler.execute(
          req(Map.of("discard_ids", List.of())),
          player, sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("discard 1");
    }
  }

  // ── Happy paths ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("happy paths")
  class HappyPaths {

    @Test
    @DisplayName("strength 1: draws 2, discards 1, hand grows by 1")
    void strength1DrawKeepOne() {
      when(deckService.deckSize(gameId)).thenReturn(10);
      when(deckService.drawFromDeck(gameId, "player1", 2)).thenReturn(List.of("c1", "c2"));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of(makeHandCard("c1")));

      ActionResult result = handler.execute(
          req(Map.of("discard_ids", List.of("c2"))),
          player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService).discardFromHand(gameId, "player1", List.of("c2"));
      assertThat(result.drawnCardIds()).containsExactlyInAnyOrder("c1", "c2");
    }

    @Test
    @DisplayName("strength 2: draws 2, keeps both, no discard required")
    void strength2DrawBoth() {
      setStrength(player, ActionCard.CARDS, 2);
      when(deckService.deckSize(gameId)).thenReturn(10);
      when(deckService.drawFromDeck(gameId, "player1", 2)).thenReturn(List.of("c1", "c2"));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of(
          makeHandCard("c1"), makeHandCard("c2")));

      ActionResult result = handler.execute(req(Map.of()), player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService, never()).discardFromHand(any(), any(), any());
      assertThat(result.drawnCardIds()).containsExactlyInAnyOrder("c1", "c2");
    }

    @Test
    @DisplayName("strength 4: takes 1 from display (reputation 0 = slot 1 free)")
    void strength4TakeFromDisplay() {
      setStrength(player, ActionCard.CARDS, 4);
      player.setReputation(0);

      PlayerCard displayCard = displayCard("card_lion", 1);
      when(deckService.getDisplay(gameId)).thenReturn(List.of(displayCard,
          displayCard("c2", 2), displayCard("c3", 3),
          displayCard("c4", 4), displayCard("c5", 5), displayCard("c6", 6)));
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of(makeHandCard("card_lion")));

      ActionResult result = handler.execute(
          req(Map.of("display_card_ids", List.of("card_lion"))),
          player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService).takeFromDisplay(gameId, "player1", "card_lion");
    }

    @Test
    @DisplayName("empty deck: skips draw gracefully")
    void emptyDeck() {
      when(deckService.deckSize(gameId)).thenReturn(0);
      when(deckService.getHand(gameId, "player1")).thenReturn(List.of());

      // At strength 1, normally must discard 1 of drawn cards.
      // But if deck is empty, nothing is drawn so no discard needed.
      ActionResult result = handler.execute(req(Map.of()), player, sharedBoard);

      assertThat(result.success()).isTrue();
      verify(deckService, never()).drawFromDeck(any(), any(), anyInt());
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  /** Force a card to a specific strength by reordering the ActionCardOrder. */
  private void setStrength(PlayerState ps, ActionCard card, int targetStrength) {
    // Build a custom order where the given card is at targetStrength position
    List<ActionCard> cards = new java.util.ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(card);
    cards.add(targetStrength - 1, card);
    ps.setActionCardOrder(new ActionCardOrder(cards, java.util.Set.of()));
  }

  private PlayerCard displayCard(String cardId, int slot) {
    var def = new com.arknova.bot.model.CardDefinition();
    def.setId(cardId);
    def.setName("Card " + cardId);
    def.setCardType(com.arknova.bot.model.CardDefinition.CardType.ANIMAL);
    PlayerCard pc = new PlayerCard();
    pc.setCard(def);
    pc.setSortOrder(slot - 1);
    pc.setLocation(com.arknova.bot.model.PlayerCard.CardLocation.DISPLAY);
    return pc;
  }

  private PlayerCard makeHandCard(String cardId) {
    var def = new com.arknova.bot.model.CardDefinition();
    def.setId(cardId);
    def.setName("Card " + cardId);
    def.setCardType(com.arknova.bot.model.CardDefinition.CardType.ANIMAL);
    PlayerCard pc = new PlayerCard();
    pc.setCard(def);
    pc.setLocation(com.arknova.bot.model.PlayerCard.CardLocation.HAND);
    return pc;
  }
}
