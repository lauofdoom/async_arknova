package com.arknova.bot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.arknova.bot.AbstractIntegrationTest;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerCard.CardLocation;
import com.arknova.bot.repository.PlayerCardRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link DeckService} — exercises deck initialization and card movement
 * against a real PostgreSQL database with the full card catalogue loaded by {@link
 * com.arknova.bot.service.CardDatabaseLoader}.
 */
@DisplayName("DeckService")
@Transactional
class DeckServiceIT extends AbstractIntegrationTest {

  @Autowired DeckService deckService;
  @Autowired GameService gameService;
  @Autowired PlayerCardRepository playerCardRepo;

  private static final String GUILD_ID = "guild-deck-test";
  private static final String THREAD_ID = "thread-deck-test";
  private static final String ALICE_ID = "alice-deck-001";
  private static final String BOB_ID = "bob-deck-002";

  private Game game;

  @BeforeEach
  void setUp() {
    // Create and start a 2-player game; StartCommand would normally call initializeDecks,
    // but for these tests we call it explicitly to isolate DeckService behaviour.
    game = gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice");
    gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
    gameService.startGame(THREAD_ID, ALICE_ID);
  }

  private List<PlayerCard> displayCards() {
    return playerCardRepo.findByGameIdAndLocationOrderBySortOrderAsc(
        game.getId(), CardLocation.DISPLAY);
  }

  // ── initializeDecks ───────────────────────────────────────────────────────

  @Nested
  @DisplayName("initializeDecks")
  class InitializeDecks {

    @Test
    @DisplayName("fills the display with exactly 6 cards")
    void fillsDisplayWithSixCards() {
      deckService.initializeDecks(game, List.of(ALICE_ID, BOB_ID));

      assertThat(displayCards()).hasSize(DeckService.DISPLAY_SIZE);
    }

    @Test
    @DisplayName("deals each player exactly 4 starting hand cards")
    void dealsStartingHandToEachPlayer() {
      deckService.initializeDecks(game, List.of(ALICE_ID, BOB_ID));

      int aliceHand =
          playerCardRepo.countByGameIdAndDiscordIdAndLocation(
              game.getId(), ALICE_ID, CardLocation.HAND);
      int bobHand =
          playerCardRepo.countByGameIdAndDiscordIdAndLocation(
              game.getId(), BOB_ID, CardLocation.HAND);

      assertThat(aliceHand).isEqualTo(DeckService.STARTING_HAND_SIZE);
      assertThat(bobHand).isEqualTo(DeckService.STARTING_HAND_SIZE);
    }

    @Test
    @DisplayName("total dealt cards equals display size + 2 × starting hand size")
    void deckShrinksByDealtCards() {
      deckService.initializeDecks(game, List.of(ALICE_ID, BOB_ID));

      int inDisplay = displayCards().size();
      int inAliceHand =
          playerCardRepo.countByGameIdAndDiscordIdAndLocation(
              game.getId(), ALICE_ID, CardLocation.HAND);
      int inBobHand =
          playerCardRepo.countByGameIdAndDiscordIdAndLocation(
              game.getId(), BOB_ID, CardLocation.HAND);

      assertThat(inDisplay + inAliceHand + inBobHand)
          .as("display + hands should equal display size + 2 × starting hand size")
          .isEqualTo(DeckService.DISPLAY_SIZE + DeckService.STARTING_HAND_SIZE * 2);

      assertThat(deckService.deckSize(game.getId()))
          .as("deck should still have cards remaining after setup")
          .isPositive();
    }

    @Test
    @DisplayName("display slots are numbered 0 through 5")
    void displaySlotsAreZeroIndexed() {
      deckService.initializeDecks(game, List.of(ALICE_ID, BOB_ID));

      List<PlayerCard> display = displayCards();
      for (int i = 0; i < display.size(); i++) {
        assertThat(display.get(i).getSortOrder())
            .as("display slot %d should have sortOrder %d", i, i)
            .isEqualTo(i);
      }
    }
  }

  // ── takeFromDisplay ───────────────────────────────────────────────────────

  @Nested
  @DisplayName("takeFromDisplay")
  class TakeFromDisplay {

    @Test
    @DisplayName("moves the card to the player's hand")
    void movesCardToHand() {
      deckService.initializeDecks(game, List.of(ALICE_ID, BOB_ID));

      String takenCardId = displayCards().get(0).getCard().getId();
      deckService.takeFromDisplay(game.getId(), ALICE_ID, takenCardId);

      List<PlayerCard> aliceHand =
          playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              game.getId(), ALICE_ID, CardLocation.HAND);
      assertThat(aliceHand).extracting(pc -> pc.getCard().getId()).contains(takenCardId);
    }

    @Test
    @DisplayName("display is refilled to 6 slots after taking")
    void displayRemainsFullAfterTake() {
      deckService.initializeDecks(game, List.of(ALICE_ID, BOB_ID));

      String takenCardId = displayCards().get(2).getCard().getId();
      deckService.takeFromDisplay(game.getId(), ALICE_ID, takenCardId);

      assertThat(displayCards()).hasSize(DeckService.DISPLAY_SIZE);
    }

    @Test
    @DisplayName("remaining display cards shift left after take")
    void displayCardsShiftLeft() {
      deckService.initializeDecks(game, List.of(ALICE_ID, BOB_ID));

      List<PlayerCard> before = displayCards();
      String slot1CardId = before.get(1).getCard().getId();

      // Take slot 0 — slot 1 should shift to position 0
      deckService.takeFromDisplay(game.getId(), ALICE_ID, before.get(0).getCard().getId());

      assertThat(displayCards().get(0).getCard().getId())
          .as("former slot 1 card should shift to slot 0")
          .isEqualTo(slot1CardId);
    }
  }

  // ── drawFromDeck ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("drawFromDeck")
  class DrawFromDeck {

    @Test
    @DisplayName("adds drawn cards to the player's hand")
    void addsDrawnCardsToHand() {
      deckService.initializeDecks(game, List.of(ALICE_ID, BOB_ID));

      int handBefore =
          playerCardRepo.countByGameIdAndDiscordIdAndLocation(
              game.getId(), ALICE_ID, CardLocation.HAND);

      deckService.drawFromDeck(game.getId(), ALICE_ID, 2);

      int handAfter =
          playerCardRepo.countByGameIdAndDiscordIdAndLocation(
              game.getId(), ALICE_ID, CardLocation.HAND);

      assertThat(handAfter).isEqualTo(handBefore + 2);
    }

    @Test
    @DisplayName("deck shrinks by the number of cards drawn")
    void deckShrinksAfterDraw() {
      deckService.initializeDecks(game, List.of(ALICE_ID, BOB_ID));

      int deckBefore = deckService.deckSize(game.getId());
      deckService.drawFromDeck(game.getId(), ALICE_ID, 3);

      assertThat(deckService.deckSize(game.getId())).isEqualTo(deckBefore - 3);
    }
  }
}
