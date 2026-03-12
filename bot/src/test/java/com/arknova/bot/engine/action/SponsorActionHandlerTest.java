package com.arknova.bot.engine.action;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.ActionCardOrder;
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
@DisplayName("SponsorActionHandler")
class SponsorActionHandlerTest {

  @Mock DeckService deckService;
  @Mock PlayerCardRepository playerCardRepo;

  @InjectMocks SponsorActionHandler handler;

  private PlayerState player;
  private SharedBoardState sharedBoard;
  private UUID gameId;

  @BeforeEach
  void setUp() {
    gameId = UUID.randomUUID();
    player = new PlayerState();
    player.setDiscordId("player1");
    player.setDiscordName("Alice");
    player.setMoney(20);
    player.setAppeal(0);
    player.setConservation(0);
    player.setReputation(0);
    // Default: SPONSOR at strength 5

    sharedBoard = new SharedBoardState();
    sharedBoard.setGameId(gameId);
  }

  private ActionRequest req(Map<String, Object> params) {
    return new ActionRequest(gameId, "player1", "Alice", ActionCard.SPONSOR, params, null);
  }

  private void setStrength(int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(ActionCard.SPONSOR);
    cards.add(targetStrength - 1, ActionCard.SPONSOR);
    player.setActionCardOrder(new ActionCardOrder(cards, Set.of()));
  }

  private void setStrengthUpgraded(int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(ActionCard.SPONSOR);
    cards.add(targetStrength - 1, ActionCard.SPONSOR);
    player.setActionCardOrder(new ActionCardOrder(cards, Set.of(ActionCard.SPONSOR)));
  }

  /** Creates a SPONSOR PlayerCard in the player's hand with the given base_cost (level). */
  private PlayerCard handSponsor(String id, int level) {
    CardDefinition def = new CardDefinition();
    def.setId(id);
    def.setName("Sponsor " + id);
    def.setCardType(CardDefinition.CardType.SPONSOR);
    def.setBaseCost(level);
    def.setAppealValue(0);
    def.setConservationValue(0);
    def.setReputationValue(0);
    PlayerCard pc = new PlayerCard();
    pc.setCard(def);
    pc.setLocation(CardLocation.HAND);
    return pc;
  }

  /** Creates a SPONSOR PlayerCard in the display at the given 1-based slot (sortOrder = slot-1). */
  private PlayerCard displaySponsor(String id, int level, int slot) {
    CardDefinition def = new CardDefinition();
    def.setId(id);
    def.setName("Sponsor " + id);
    def.setCardType(CardDefinition.CardType.SPONSOR);
    def.setBaseCost(level);
    def.setAppealValue(2);
    def.setConservationValue(0);
    def.setReputationValue(0);
    PlayerCard pc = new PlayerCard();
    pc.setCard(def);
    pc.setSortOrder(slot - 1); // 0-based
    pc.setLocation(CardLocation.DISPLAY);
    return pc;
  }

  // ── BREAK ─────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("BREAK")
  class Break {

    @Test
    @DisplayName("unupgraded break: gains money equal to strength X")
    void unupgradedBreak() {
      setStrength(3); // X=3
      player.setMoney(5);

      ActionResult r = handler.execute(req(Map.of("break", true)), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(8); // 5 + 3
      assertThat((Integer) r.deltas().get("money_gained")).isEqualTo(3);
    }

    @Test
    @DisplayName("upgraded break: gains 2×X money")
    void upgradedBreak() {
      setStrengthUpgraded(3); // X=3
      player.setMoney(5);

      ActionResult r = handler.execute(req(Map.of("break", true)), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(11); // 5 + 2×3
      assertThat((Integer) r.deltas().get("money_gained")).isEqualTo(6);
    }

    @Test
    @DisplayName("break gain scales with strength")
    void breakScalesWithStrength() {
      setStrength(5);
      player.setMoney(0);

      ActionResult r = handler.execute(req(Map.of("break", true)), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(5);
    }

    @Test
    @DisplayName("break with string 'true' is also accepted")
    void breakStringParam() {
      setStrength(2);
      player.setMoney(0);

      ActionResult r = handler.execute(req(Map.of("break", "true")), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(2);
    }
  }

  // ── Validation ────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("fails when no card_ids and no break")
    void noCardsAndNoBreak() {
      ActionResult r = handler.execute(req(Map.of()), player, sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("card_ids");
    }

    @Test
    @DisplayName("fails when display_card_ids provided on unupgraded card")
    void displayRequiresUpgrade() {
      setStrength(3);
      ActionResult r = handler.execute(
          req(Map.of("display_card_ids", List.of("s1"))), player, sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("upgraded");
    }

    @Test
    @DisplayName("fails when sponsor card level exceeds max level (X unupgraded)")
    void levelTooHighUnupgraded() {
      setStrength(2); // X=2, max level = 2
      PlayerCard pc = handSponsor("s1", 3); // level 3 > 2

      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
          gameId, "player1", CardLocation.HAND)).thenReturn(List.of(pc));

      ActionResult r = handler.execute(
          req(Map.of("card_ids", List.of("s1"))), player, sharedBoard);

      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("level 3");
      assertThat(r.errorMessage()).containsIgnoringCase("maximum level");
    }

    @Test
    @DisplayName("upgraded: fails when sponsor card level exceeds X+1")
    void levelTooHighUpgraded() {
      setStrengthUpgraded(2); // X=2, max level = X+1 = 3
      PlayerCard pc = handSponsor("s1", 4); // level 4 > 3

      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
          gameId, "player1", CardLocation.HAND)).thenReturn(List.of(pc));

      ActionResult r = handler.execute(
          req(Map.of("card_ids", List.of("s1"))), player, sharedBoard);

      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("maximum level");
    }

    @Test
    @DisplayName("fails when card_id is not in player's hand")
    void cardNotInHand() {
      setStrength(3);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
          gameId, "player1", CardLocation.HAND)).thenReturn(List.of());

      ActionResult r = handler.execute(
          req(Map.of("card_ids", List.of("s_missing"))), player, sharedBoard);

      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("not in your hand");
    }

    @Test
    @DisplayName("fails when player cannot afford the sponsor card")
    void insufficientMoney() {
      setStrength(3); // max level 3
      player.setMoney(2);
      PlayerCard pc = handSponsor("s1", 3); // costs 3 money

      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
          gameId, "player1", CardLocation.HAND)).thenReturn(List.of(pc));

      ActionResult r = handler.execute(
          req(Map.of("card_ids", List.of("s1"))), player, sharedBoard);

      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("money");
    }

    @Test
    @DisplayName("fails when display_card_id is not in the display")
    void cardNotInDisplay() {
      setStrengthUpgraded(3);
      player.setReputation(5);

      // Display is empty — card "s_missing" not found
      when(deckService.getDisplay(gameId)).thenReturn(List.of());

      ActionResult r = handler.execute(
          req(Map.of("display_card_ids", List.of("s_missing"))), player, sharedBoard);

      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("not in the display");
    }

    @Test
    @DisplayName("fails when display card is outside reputation range")
    void displayOutsideRepRange() {
      setStrengthUpgraded(3);
      player.setReputation(2); // can access slots 0, 1, 2 (0-based)
      PlayerCard pc = displaySponsor("s1", 1, 4); // slot 4 → sortOrder=3 > reputation 2

      when(deckService.getDisplay(gameId)).thenReturn(List.of(pc));

      ActionResult r = handler.execute(
          req(Map.of("display_card_ids", List.of("s1"))), player, sharedBoard);

      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("reputation");
    }

    @Test
    @DisplayName("fails when the same card appears twice in the request")
    void duplicateCardInRequest() {
      setStrength(3);
      PlayerCard pc = handSponsor("s1", 2);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
          gameId, "player1", CardLocation.HAND)).thenReturn(List.of(pc));

      ActionResult r = handler.execute(
          req(Map.of("card_ids", List.of("s1", "s1"))), player, sharedBoard);

      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("more than once");
    }
  }

  // ── Happy paths ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("happy paths")
  class HappyPaths {

    @Test
    @DisplayName("unupgraded: plays 1 sponsor at exactly level X")
    void playOneSponsorAtMaxLevel() {
      setStrength(3); // X=3, max level 3
      PlayerCard pc = handSponsor("s1", 3); // level = X = max

      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
          gameId, "player1", CardLocation.HAND)).thenReturn(List.of(pc));

      ActionResult r = handler.execute(
          req(Map.of("card_ids", List.of("s1"))), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(17); // 20 - 3
      verify(deckService).playSponsor(gameId, "player1", "s1");
      verify(deckService, never()).takeFromDisplay(any(), any(), any());
    }

    @Test
    @DisplayName("upgraded: plays multiple sponsors within level X+1")
    void upgradedPlayMultipleSponsors() {
      setStrengthUpgraded(2); // X=2, max level 3
      PlayerCard s1 = handSponsor("s1", 2);
      PlayerCard s2 = handSponsor("s2", 1);

      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
          gameId, "player1", CardLocation.HAND)).thenReturn(List.of(s1, s2));

      ActionResult r = handler.execute(
          req(Map.of("card_ids", List.of("s1", "s2"))), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(17); // 20 - (2+1)
      assertThat((Integer) r.deltas().get("cards_played")).isEqualTo(2);
      verify(deckService).playSponsor(gameId, "player1", "s1");
      verify(deckService).playSponsor(gameId, "player1", "s2");
    }

    @Test
    @DisplayName("sponsor appeal/conservation/reputation bonuses are applied to player state")
    void sponsorBonusesApplied() {
      setStrength(3);
      CardDefinition def = new CardDefinition();
      def.setId("s1");
      def.setName("Sponsor s1");
      def.setCardType(CardDefinition.CardType.SPONSOR);
      def.setBaseCost(2);
      def.setAppealValue(3);
      def.setConservationValue(1);
      def.setReputationValue(2);
      PlayerCard pc = new PlayerCard();
      pc.setCard(def);
      pc.setLocation(CardLocation.HAND);

      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
          gameId, "player1", CardLocation.HAND)).thenReturn(List.of(pc));

      ActionResult r = handler.execute(
          req(Map.of("card_ids", List.of("s1"))), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getAppeal()).isEqualTo(3);
      assertThat(player.getConservation()).isEqualTo(1);
      assertThat(player.getReputation()).isEqualTo(2);
    }

    @Test
    @DisplayName("upgraded: takes sponsor from display within reputation range")
    void upgradedTakeFromDisplay() {
      setStrengthUpgraded(3); // X=3, max level 4
      player.setReputation(3); // can access sortOrder 0,1,2,3

      PlayerCard pc = displaySponsor("ds1", 3, 2); // slot 2 → sortOrder=1, cost = 3+1=4

      when(deckService.getDisplay(gameId)).thenReturn(List.of(pc));

      ActionResult r = handler.execute(
          req(Map.of("display_card_ids", List.of("ds1"))), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(16); // 20 - (3+1) = 16
      verify(deckService).takeFromDisplay(gameId, "player1", "ds1");
      verify(deckService).playSponsor(gameId, "player1", "ds1");
    }

    @Test
    @DisplayName("summary mentions the sponsor card name")
    void summaryMentionsCardName() {
      setStrength(2);
      PlayerCard pc = handSponsor("s1", 1);

      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
          gameId, "player1", CardLocation.HAND)).thenReturn(List.of(pc));

      ActionResult r = handler.execute(
          req(Map.of("card_ids", List.of("s1"))), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(r.summary()).containsIgnoringCase("Sponsor s1");
    }
  }
}
