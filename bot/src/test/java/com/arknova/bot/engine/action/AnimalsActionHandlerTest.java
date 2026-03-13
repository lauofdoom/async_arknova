package com.arknova.bot.engine.action;

import static com.arknova.bot.engine.action.AnimalsActionHandler.maxAnimals;
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
import com.arknova.bot.engine.effect.EffectExecutor;
import com.arknova.bot.repository.CardDefinitionRepository;
import com.arknova.bot.repository.PlayerCardRepository;
import com.arknova.bot.service.DeckService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnimalsActionHandler")
class AnimalsActionHandlerTest {

  @Mock DeckService deckService;
  @Mock PlayerCardRepository playerCardRepo;
  @Mock CardDefinitionRepository cardDefRepo;
  @Mock EffectExecutor effectExecutor;

  AnimalsActionHandler handler;

  private PlayerState player;
  private SharedBoardState sharedBoard;
  private UUID gameId;

  /** Board with one size-3 enclosure (no terrain tags, empty). */
  private static final String BOARD_E1_SIZE3 =
      "{\"enclosures\":[{\"id\":\"E1\",\"size\":3,\"row\":0,\"col\":0,\"tags\":[],\"animalCardIds\":[]}]}";

  /** Board with two enclosures: E1 size-3 (no tags) and E2 size-3 WATER, both empty. */
  private static final String BOARD_E1_E2 =
      "{\"enclosures\":["
          + "{\"id\":\"E1\",\"size\":3,\"row\":0,\"col\":0,\"tags\":[],\"animalCardIds\":[]},"
          + "{\"id\":\"E2\",\"size\":3,\"row\":1,\"col\":0,\"tags\":[\"WATER\"],\"animalCardIds\":[]}"
          + "]}";

  /** Board with E1 size-1, empty (tiny, useful for overflow tests). */
  private static final String BOARD_E1_SIZE1 =
      "{\"enclosures\":[{\"id\":\"E1\",\"size\":1,\"row\":0,\"col\":0,\"tags\":[],\"animalCardIds\":[]}]}";

  /** Board with E1 size-3, full (3/3). */
  private static final String BOARD_E1_FULL =
      "{\"enclosures\":[{\"id\":\"E1\",\"size\":3,\"row\":0,\"col\":0,\"tags\":[],\"animalCardIds\":[\"a\",\"b\",\"c\"]}]}";

  @BeforeEach
  void setUp() {
    handler =
        new AnimalsActionHandler(deckService, playerCardRepo, cardDefRepo, new ObjectMapper(), effectExecutor);

    gameId = UUID.randomUUID();
    player = new PlayerState();
    player.setDiscordId("player1");
    player.setDiscordName("Alice");
    player.setMoney(25);
    player.setAppeal(0);
    player.setConservation(0);
    player.setReputation(0);
    player.setBoardState(BOARD_E1_SIZE3);
    player.setIcons("{}");
    // Default order: ANIMALS at position 3 → strength 3 (un-upgraded)
  }

  private ActionRequest req(Map<String, Object> params) {
    return new ActionRequest(gameId, "player1", "Alice", ActionCard.ANIMALS, params, null);
  }

  private void setAnimalsStrength(int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(ActionCard.ANIMALS);
    cards.add(targetStrength - 1, ActionCard.ANIMALS);
    player.setActionCardOrder(new ActionCardOrder(cards, Set.of()));
  }

  private void setAnimalsStrengthUpgraded(int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(ActionCard.ANIMALS);
    cards.add(targetStrength - 1, ActionCard.ANIMALS);
    player.setActionCardOrder(new ActionCardOrder(cards, Set.of(ActionCard.ANIMALS)));
  }

  private CardDefinition animalDef(
      String id,
      String name,
      int baseCost,
      int minSize,
      String[] requirements,
      String[] tags,
      int appeal,
      int conservation,
      String abilityText) {
    CardDefinition def = new CardDefinition();
    def.setId(id);
    def.setName(name);
    def.setCardType(CardDefinition.CardType.ANIMAL);
    def.setBaseCost(baseCost);
    def.setMinEnclosureSize(minSize);
    def.setRequirements(requirements);
    def.setTags(tags);
    def.setAppealValue(appeal);
    def.setConservationValue(conservation);
    def.setAbilityText(abilityText);
    def.setEffectCode(null);
    return def;
  }

  private PlayerCard handCard(CardDefinition def) {
    PlayerCard pc = new PlayerCard();
    pc.setCard(def);
    pc.setLocation(CardLocation.HAND);
    return pc;
  }

  private PlayerCard displayCard(CardDefinition def, int slot) {
    PlayerCard pc = new PlayerCard();
    pc.setCard(def);
    pc.setSortOrder(slot); // 0-based
    pc.setLocation(CardLocation.DISPLAY);
    return pc;
  }

  // ── maxAnimals() ──────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("maxAnimals()")
  class MaxAnimalsTest {

    @Test
    @DisplayName("S1 standard → 0")
    void s1() {
      assertThat(maxAnimals(1, false)).isZero();
    }

    @Test
    @DisplayName("S2 standard → 1")
    void s2() {
      assertThat(maxAnimals(2, false)).isEqualTo(1);
    }

    @Test
    @DisplayName("S3 standard → 1")
    void s3() {
      assertThat(maxAnimals(3, false)).isEqualTo(1);
    }

    @Test
    @DisplayName("S4 standard → 1")
    void s4() {
      assertThat(maxAnimals(4, false)).isEqualTo(1);
    }

    @Test
    @DisplayName("S5 standard → 2")
    void s5() {
      assertThat(maxAnimals(5, false)).isEqualTo(2);
    }

    @Test
    @DisplayName("S1 upgraded → 1")
    void s1u() {
      assertThat(maxAnimals(1, true)).isEqualTo(1);
    }

    @Test
    @DisplayName("S2 upgraded → 1")
    void s2u() {
      assertThat(maxAnimals(2, true)).isEqualTo(1);
    } // same

    @Test
    @DisplayName("S3 upgraded → 2")
    void s3u() {
      assertThat(maxAnimals(3, true)).isEqualTo(2);
    }

    @Test
    @DisplayName("S4 upgraded → 2")
    void s4u() {
      assertThat(maxAnimals(4, true)).isEqualTo(2);
    }

    @Test
    @DisplayName("S5 upgraded → 3")
    void s5u() {
      assertThat(maxAnimals(5, true)).isEqualTo(3);
    }
  }

  // ── Strength 1 un-upgraded: 0 animals ────────────────────────────────────────

  @Nested
  @DisplayName("strength 1 standard (0 animals)")
  class Strength1Standard {

    @Test
    @DisplayName("succeeds immediately without accessing deck/hand")
    void succeeds() {
      setAnimalsStrength(1);
      ActionResult result = handler.execute(req(Map.of()), player, sharedBoard);
      assertThat(result.success()).isTrue();
      verifyNoInteractions(deckService, playerCardRepo);
    }
  }

  // ── Validation ────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("fails when no cards provided (strength 2+)")
    void noCards() {
      setAnimalsStrength(2);
      ActionResult result = handler.execute(req(Map.of()), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("at least one");
    }

    @Test
    @DisplayName("fails when hand lists length mismatch")
    void handListMismatch() {
      setAnimalsStrength(2);
      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("lion"), "hand_enc_ids", List.of())),
              player,
              sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("same length");
    }

    @Test
    @DisplayName("fails when attempting display cards without upgrade")
    void displayRequiresUpgrade() {
      setAnimalsStrength(3);
      ActionResult result =
          handler.execute(
              req(Map.of("display_card_ids", List.of("seal"), "display_enc_ids", List.of("E1"))),
              player,
              sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("upgraded");
    }

    @Test
    @DisplayName("fails when requesting more animals than strength allows")
    void tooManyAnimals() {
      setAnimalsStrength(2); // max 1
      ActionResult result =
          handler.execute(
              req(
                  Map.of(
                      "hand_card_ids", List.of("lion", "fox"),
                      "hand_enc_ids", List.of("E1", "E1"))),
              player,
              sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("at most 1");
    }

    @Test
    @DisplayName("fails when card not in hand")
    void cardNotInHand() {
      setAnimalsStrength(2);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of());

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("lion"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("not in your hand");
    }

    @Test
    @DisplayName("fails when card is not an animal")
    void notAnAnimalCard() {
      setAnimalsStrength(2);
      CardDefinition sponsor = new CardDefinition();
      sponsor.setId("s1");
      sponsor.setName("Sponsor");
      sponsor.setCardType(CardDefinition.CardType.SPONSOR);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(sponsor)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("s1"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("not an animal");
    }

    @Test
    @DisplayName("fails when enclosure does not exist")
    void enclosureNotFound() {
      setAnimalsStrength(2);
      player.setBoardState("{\"enclosures\":[]}");
      CardDefinition def =
          animalDef("lion", "Lion", 6, 2, new String[] {}, new String[] {}, 3, 0, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("lion"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("does not exist");
    }

    @Test
    @DisplayName("fails when enclosure is too small")
    void enclosureTooSmall() {
      setAnimalsStrength(2);
      // E1 size-3; elephant needs size 5
      CardDefinition def =
          animalDef("elephant", "Elephant", 10, 5, new String[] {}, new String[] {}, 4, 0, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("elephant"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("size ≥ 5");
    }

    @Test
    @DisplayName("fails when enclosure is full")
    void enclosureFull() {
      setAnimalsStrength(2);
      player.setBoardState(BOARD_E1_FULL);
      CardDefinition def =
          animalDef("fox", "Fox", 4, 1, new String[] {}, new String[] {}, 1, 0, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("fox"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("full");
    }

    @Test
    @DisplayName("fails when player lacks required continent icon")
    void requirementIconNotMet() {
      setAnimalsStrength(2);
      // Player has no AUSTRALIA icons; kookaburra needs 1
      CardDefinition def =
          animalDef(
              "kookaburra",
              "Laughing Kookaburra",
              5,
              1,
              new String[] {"AUSTRALIA"},
              new String[] {"BIRD", "AUSTRALIA"},
              2,
              0,
              null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("kookaburra"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("AUSTRALIA");
    }

    @Test
    @DisplayName("fails when player lacks required icon count (e.g. Lion needs 3 PREDATOR)")
    void requirementIconCountNotMet() {
      setAnimalsStrength(2);
      player.setIcons("{\"PREDATOR\":2}"); // has 2, needs 3
      CardDefinition def =
          animalDef(
              "lion",
              "Lion",
              16,
              4,
              new String[] {"PREDATOR", "PREDATOR", "PREDATOR"},
              new String[] {"PREDATOR", "AFRICA"},
              9,
              0,
              null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("lion"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("3 PREDATOR");
    }

    @Test
    @DisplayName("fails when ANIMALS_II required but card is not upgraded")
    void animalsIIRequirementNotMet() {
      setAnimalsStrength(2); // not upgraded
      CardDefinition def =
          animalDef(
              "elephant",
              "African Bush Elephant",
              16,
              5,
              new String[] {"ANIMALS_II"},
              new String[] {"AFRICA"},
              8,
              0,
              null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("elephant"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("upgraded Animals");
    }

    @Test
    @DisplayName("fails when player cannot afford the card")
    void insufficientMoney() {
      setAnimalsStrength(2);
      player.setMoney(3);
      CardDefinition def =
          animalDef("lion", "Lion", 10, 2, new String[] {}, new String[] {}, 3, 0, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("lion"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("costs");
    }

    @Test
    @DisplayName("fails when placing 2 animals in same size-1 enclosure (upgraded S5)")
    void cannotOverfillSingleEnclosureInOneAction() {
      setAnimalsStrengthUpgraded(5); // max 3
      player.setBoardState(BOARD_E1_SIZE1);
      player.setMoney(20);
      CardDefinition fox1 =
          animalDef("fox1", "Fox 1", 4, 1, new String[] {}, new String[] {}, 1, 0, null);
      CardDefinition fox2 =
          animalDef("fox2", "Fox 2", 4, 1, new String[] {}, new String[] {}, 1, 0, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(fox1), handCard(fox2)));

      ActionResult result =
          handler.execute(
              req(
                  Map.of(
                      "hand_card_ids", List.of("fox1", "fox2"),
                      "hand_enc_ids", List.of("E1", "E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("full");
    }

    @Test
    @DisplayName("fails when display card slot requires more reputation than player has")
    void displayInsufficientReputation() {
      setAnimalsStrengthUpgraded(2); // max 1
      player.setReputation(0); // slot 1 (sortOrder=1) requires rep 2
      CardDefinition def =
          animalDef("giraffe", "Giraffe", 6, 2, new String[] {}, new String[] {}, 3, 0, null);
      when(deckService.getDisplay(gameId)).thenReturn(List.of(displayCard(def, 1))); // slot index 1

      ActionResult result =
          handler.execute(
              req(Map.of("display_card_ids", List.of("giraffe"), "display_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("reputation");
    }
  }

  // ── Happy paths ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("happy paths")
  class HappyPaths {

    @Test
    @DisplayName("strength 2: places 1 hand animal, deducts cost, gains appeal")
    void strength2PlaceOne() {
      setAnimalsStrength(2);
      player.setMoney(10);
      CardDefinition def =
          animalDef("lion", "Lion", 6, 2, new String[] {}, new String[] {"PREDATOR"}, 3, 0, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("lion"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(4); // 10 - 6
      assertThat(player.getAppeal()).isEqualTo(3);
      verify(deckService).placeAnimal(gameId, "player1", "lion", "E1");
    }

    @Test
    @DisplayName("strength 5: places 2 hand animals, both costs and appeals applied")
    void strength5PlaceTwo() {
      setAnimalsStrength(5);
      player.setBoardState(BOARD_E1_E2);
      player.setMoney(20);
      CardDefinition lion =
          animalDef("lion", "Lion", 6, 2, new String[] {}, new String[] {}, 3, 0, null);
      CardDefinition seal =
          animalDef("seal", "Seal", 4, 1, new String[] {}, new String[] {}, 2, 0, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(lion), handCard(seal)));

      ActionResult result =
          handler.execute(
              req(
                  Map.of(
                      "hand_card_ids", List.of("lion", "seal"),
                      "hand_enc_ids", List.of("E1", "E2"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(10); // 20 - 6 - 4
      assertThat(player.getAppeal()).isEqualTo(5); // 3 + 2
      verify(deckService).placeAnimal(gameId, "player1", "lion", "E1");
      verify(deckService).placeAnimal(gameId, "player1", "seal", "E2");
    }

    @Test
    @DisplayName("upgraded S1: places 1 animal (bonus slot from upgrade)")
    void upgradedStrength1PlacesOne() {
      setAnimalsStrengthUpgraded(1);
      player.setMoney(10);
      CardDefinition def =
          animalDef("fox", "Fox", 4, 1, new String[] {}, new String[] {}, 1, 0, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("fox"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(6);
    }

    @Test
    @DisplayName("upgraded S5: places 3 animals total (2 hand + 1 display)")
    void upgradedStrength5PlacesThree() {
      setAnimalsStrengthUpgraded(5);
      player.setBoardState(BOARD_E1_E2);
      player.setReputation(2); // enough for slot index 1
      player.setMoney(25);

      CardDefinition lion =
          animalDef("lion", "Lion", 6, 2, new String[] {}, new String[] {}, 3, 0, null);
      CardDefinition fox =
          animalDef("fox", "Fox", 4, 1, new String[] {}, new String[] {}, 1, 0, null);
      CardDefinition seal =
          animalDef("seal", "Seal", 5, 1, new String[] {}, new String[] {}, 2, 0, null);

      // Add a third enclosure for the display animal
      String board3 =
          "{\"enclosures\":["
              + "{\"id\":\"E1\",\"size\":3,\"row\":0,\"col\":0,\"tags\":[],\"animalCardIds\":[]},"
              + "{\"id\":\"E2\",\"size\":3,\"row\":1,\"col\":0,\"tags\":[\"WATER\"],\"animalCardIds\":[]},"
              + "{\"id\":\"E3\",\"size\":2,\"row\":2,\"col\":0,\"tags\":[],\"animalCardIds\":[]}"
              + "]}";
      player.setBoardState(board3);

      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(lion), handCard(fox)));
      when(deckService.getDisplay(gameId)).thenReturn(List.of(displayCard(seal, 1)));

      ActionResult result =
          handler.execute(
              req(
                  Map.of(
                      "hand_card_ids", List.of("lion", "fox"),
                      "hand_enc_ids", List.of("E1", "E3"),
                      "display_card_ids", List.of("seal"),
                      "display_enc_ids", List.of("E2"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(result.deltas().get("cards_placed")).isEqualTo(3);
      verify(deckService).placeAnimal(gameId, "player1", "lion", "E1");
      verify(deckService).placeAnimal(gameId, "player1", "fox", "E3");
      verify(deckService).takeFromDisplay(gameId, "player1", "seal");
      verify(deckService).placeAnimal(gameId, "player1", "seal", "E2");
    }

    @Test
    @DisplayName("display card cost = base cost + slot index")
    void displayCardCostIncludesSlotPremium() {
      setAnimalsStrengthUpgraded(2);
      player.setReputation(4); // enough for slot 2 (sortOrder=2, requires rep 4)
      player.setMoney(20);
      CardDefinition def =
          animalDef("bear", "Bear", 6, 1, new String[] {}, new String[] {}, 2, 0, null);
      when(deckService.getDisplay(gameId)).thenReturn(List.of(displayCard(def, 2))); // slot 2

      ActionResult result =
          handler.execute(
              req(Map.of("display_card_ids", List.of("bear"), "display_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(12); // 20 - (6 + 2)
    }

    @Test
    @DisplayName("continent icon requirement satisfied when player has the icon")
    void requirementIconMet() {
      setAnimalsStrength(2);
      player.setIcons("{\"AUSTRALIA\":1}");
      player.setMoney(10);
      CardDefinition def =
          animalDef(
              "kookaburra",
              "Laughing Kookaburra",
              5,
              1,
              new String[] {"AUSTRALIA"},
              new String[] {"BIRD", "AUSTRALIA"},
              2,
              0,
              null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("kookaburra"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("ANIMALS_II requirement satisfied when card is upgraded")
    void animalsIIRequirementMet() {
      setAnimalsStrengthUpgraded(2); // upgraded
      player.setMoney(20);
      CardDefinition def =
          animalDef(
              "elephant",
              "African Bush Elephant",
              16,
              2,
              new String[] {"ANIMALS_II"},
              new String[] {"AFRICA"},
              8,
              0,
              null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("elephant"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("PARTNER_ZOO requirement does not block normal enclosure placement")
    void partnerZooRequirementDoesNotBlock() {
      setAnimalsStrength(2);
      player.setMoney(20);
      CardDefinition def =
          animalDef(
              "leopard",
              "Leopard",
              12,
              2,
              new String[] {"PARTNER_ZOO"},
              new String[] {"PREDATOR", "AFRICA"},
              6,
              0,
              null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("leopard"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("animal with ability text and no effectCode triggers manual resolution")
    void manualResolution() {
      setAnimalsStrength(2);
      player.setMoney(10);
      CardDefinition def =
          animalDef(
              "owl", "Owl", 4, 1, new String[] {}, new String[] {}, 1, 0, "Do something special.");
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("owl"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(result.requiresManualResolution()).isTrue();
      assertThat(result.manualResolutionCardId()).isEqualTo("owl");
    }

    @Test
    @DisplayName("animal with no abilityText does not trigger manual resolution")
    void noManualResolution() {
      setAnimalsStrength(2);
      player.setMoney(10);
      CardDefinition def =
          animalDef("mole", "Mole", 3, 1, new String[] {}, new String[] {}, 1, 0, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("mole"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(result.requiresManualResolution()).isFalse();
    }

    @Test
    @DisplayName("conservation value from animal is applied to player track")
    void conservationApplied() {
      setAnimalsStrength(2);
      player.setMoney(10);
      CardDefinition def =
          animalDef("panda", "Giant Panda", 8, 2, new String[] {}, new String[] {}, 2, 1, null);
      when(playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
              gameId, "player1", CardLocation.HAND))
          .thenReturn(List.of(handCard(def)));

      ActionResult result =
          handler.execute(
              req(Map.of("hand_card_ids", List.of("panda"), "hand_enc_ids", List.of("E1"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getConservation()).isEqualTo(1);
    }
  }
}
