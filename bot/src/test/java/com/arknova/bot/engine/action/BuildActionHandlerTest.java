package com.arknova.bot.engine.action;

import static com.arknova.bot.engine.action.BuildActionHandler.maxSizeForStrength;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.ActionCardOrder;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuildActionHandler")
class BuildActionHandlerTest {

  BuildActionHandler handler;

  private PlayerState player;
  private SharedBoardState sharedBoard;
  private UUID gameId;

  /** Empty board. */
  private static final String BOARD_EMPTY = "{\"enclosures\":[]}";

  /** Board with an enclosure at (0,0). */
  private static final String BOARD_E1_AT_0_0 =
      "{\"enclosures\":[{\"id\":\"E1\",\"size\":2,\"row\":0,\"col\":0,\"tags\":[],\"animalCardIds\":[]}]}";

  @BeforeEach
  void setUp() {
    handler = new BuildActionHandler(new ObjectMapper());

    gameId = UUID.randomUUID();
    player = new PlayerState();
    player.setDiscordId("player1");
    player.setDiscordName("Alice");
    player.setMoney(25);
    player.setBoardState(BOARD_EMPTY);
    // Default order: BUILD is at strength 2 by default
  }

  private ActionRequest req(Map<String, Object> params) {
    return new ActionRequest(gameId, "player1", "Alice", ActionCard.BUILD, params, null);
  }

  /** Force BUILD to a specific strength. */
  private void setBuildStrength(int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(ActionCard.BUILD);
    cards.add(targetStrength - 1, ActionCard.BUILD);
    player.setActionCardOrder(new ActionCardOrder(cards, Set.of()));
  }

  /** Force BUILD to a specific strength AND mark it as upgraded. */
  private void setBuildStrengthUpgraded(int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(ActionCard.BUILD);
    cards.add(targetStrength - 1, ActionCard.BUILD);
    player.setActionCardOrder(new ActionCardOrder(cards, Set.of(ActionCard.BUILD)));
  }

  // ── maxSizeForStrength() ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("maxSizeForStrength()")
  class MaxSize {

    @Test @DisplayName("strength 1: max 2")         void s1()  { assertThat(maxSizeForStrength(1, false)).isEqualTo(2); }
    @Test @DisplayName("strength 2: max 3")         void s2()  { assertThat(maxSizeForStrength(2, false)).isEqualTo(3); }
    @Test @DisplayName("strength 3: max 4")         void s3()  { assertThat(maxSizeForStrength(3, false)).isEqualTo(4); }
    @Test @DisplayName("strength 4: max 5")         void s4()  { assertThat(maxSizeForStrength(4, false)).isEqualTo(5); }
    @Test @DisplayName("strength 5: max 6")         void s5()  { assertThat(maxSizeForStrength(5, false)).isEqualTo(6); }
    @Test @DisplayName("strength 5 upgraded: max 7") void s5u() { assertThat(maxSizeForStrength(5, true)).isEqualTo(7); }
    @Test @DisplayName("strength 1 upgraded: max 3") void s1u() { assertThat(maxSizeForStrength(1, true)).isEqualTo(3); }
  }

  // ── Validation ────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("fails when size is missing or zero")
    void missingSize() {
      ActionResult result = handler.execute(
          req(Map.of("row", 0, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("size");
    }

    @Test
    @DisplayName("fails when row is missing")
    void missingRow() {
      ActionResult result = handler.execute(
          req(Map.of("size", 2, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("location");
    }

    @Test
    @DisplayName("fails when col is missing")
    void missingCol() {
      ActionResult result = handler.execute(
          req(Map.of("size", 2, "row", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("location");
    }

    @Test
    @DisplayName("fails when enclosure size exceeds strength limit")
    void sizeTooLargeForStrength() {
      setBuildStrength(1); // max size 2
      ActionResult result = handler.execute(
          req(Map.of("size", 3, "row", 0, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("up to size 2");
    }

    @Test
    @DisplayName("fails when player cannot afford the enclosure")
    void insufficientMoney() {
      player.setMoney(2); // size-2 costs 3
      setBuildStrength(1);
      ActionResult result = handler.execute(
          req(Map.of("size", 2, "row", 0, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("money");
    }

    @Test
    @DisplayName("fails when building at an occupied grid location")
    void gridCollision() {
      player.setBoardState(BOARD_E1_AT_0_0); // already has enclosure at (0,0)
      setBuildStrength(2);
      ActionResult result = handler.execute(
          req(Map.of("size", 2, "row", 0, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("already has a structure");
    }

    @Test
    @DisplayName("upgrade sub-action fails when BUILD strength is below 4")
    void upgradeRequiresStrength4() {
      setBuildStrength(3);
      ActionResult result = handler.execute(
          req(Map.of("upgrade_action", "CARDS")), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("strength 4");
    }

    @Test
    @DisplayName("upgrade sub-action fails for unknown action card name")
    void upgradeUnknownCard() {
      setBuildStrength(4);
      ActionResult result = handler.execute(
          req(Map.of("upgrade_action", "INVALID")), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("Unknown action card");
    }

    @Test
    @DisplayName("upgrade sub-action fails when target card is already upgraded")
    void upgradeAlreadyDone() {
      // Set BUILD at strength 4, CARDS already upgraded
      List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
      cards.remove(ActionCard.BUILD);
      cards.add(3, ActionCard.BUILD); // strength 4
      player.setActionCardOrder(new ActionCardOrder(cards, Set.of(ActionCard.CARDS)));

      ActionResult result = handler.execute(
          req(Map.of("upgrade_action", "CARDS")), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("already upgraded");
    }

    @Test
    @DisplayName("upgrade sub-action fails when player cannot afford it")
    void upgradeInsufficientMoney() {
      setBuildStrength(4);
      player.setMoney(3); // upgrade costs 4
      ActionResult result = handler.execute(
          req(Map.of("upgrade_action", "ANIMALS")), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("money");
    }
  }

  // ── Happy paths ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("happy paths")
  class HappyPaths {

    @Test
    @DisplayName("strength 1: builds size-2 enclosure, deducts 3 money")
    void strength1BuildSize2() {
      setBuildStrength(1);
      player.setMoney(10);

      ActionResult result = handler.execute(
          req(Map.of("size", 2, "row", 0, "col", 0)), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(7); // 10 - (2*2 - 1) = 10 - 3
      assertThat(result.summary()).contains("E1");
      assertThat((String) result.deltas().get("enclosure_id")).isEqualTo("E1");
    }

    @Test
    @DisplayName("size-5 enclosure costs 9 money base")
    void size5Cost() {
      setBuildStrength(4); // max size 5
      player.setMoney(20);

      ActionResult result = handler.execute(
          req(Map.of("size", 5, "row", 0, "col", 0)), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(11); // 20 - (5*2 - 1)
    }

    @Test
    @DisplayName("terrain tags add 2 money each to the base cost")
    void terrainTagsCost() {
      setBuildStrength(2);
      player.setMoney(20);
      // size-3 base = 5; + WATER (2) + ROCK (2) = 9 total

      ActionResult result = handler.execute(
          req(Map.of("size", 3, "row", 0, "col", 0, "tags", List.of("WATER", "ROCK"))),
          player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(11); // 20 - 9
    }

    @Test
    @DisplayName("unknown terrain tags are silently ignored")
    void unknownTagsIgnored() {
      setBuildStrength(2);
      player.setMoney(20);

      ActionResult result = handler.execute(
          req(Map.of("size", 3, "row", 0, "col", 0, "tags", List.of("MAGIC", "WATER"))),
          player, sharedBoard);

      // Only WATER should apply (+2), MAGIC ignored → cost = 5 + 2 = 7
      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(13); // 20 - 7
    }

    @Test
    @DisplayName("upgraded BUILD at strength 5 allows size-7 enclosure")
    void upgradedAllowsSize7() {
      setBuildStrengthUpgraded(5);
      player.setMoney(30);

      ActionResult result = handler.execute(
          req(Map.of("size", 7, "row", 0, "col", 0)), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(17); // 30 - (7*2 - 1)
    }

    @Test
    @DisplayName("upgrade sub-action: marks target card as upgraded, costs 4 money")
    void upgradeSuccess() {
      setBuildStrength(4);
      player.setMoney(10);

      ActionResult result = handler.execute(
          req(Map.of("upgrade_action", "ANIMALS")), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(6); // 10 - 4
      assertThat(player.getActionCardOrder().isUpgraded(ActionCard.ANIMALS)).isTrue();
      assertThat(result.summary()).containsIgnoringCase("upgraded");
    }

    @Test
    @DisplayName("upgrade sub-action: case-insensitive card name accepted")
    void upgradeCaseInsensitive() {
      setBuildStrength(4);
      player.setMoney(10);

      ActionResult result = handler.execute(
          req(Map.of("upgrade_action", "cards")), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getActionCardOrder().isUpgraded(ActionCard.CARDS)).isTrue();
    }

    @Test
    @DisplayName("second enclosure gets sequential ID (E2)")
    void sequentialEnclosureIds() {
      player.setBoardState(BOARD_E1_AT_0_0); // already has E1
      setBuildStrength(2);
      player.setMoney(20);

      ActionResult result = handler.execute(
          req(Map.of("size", 2, "row", 1, "col", 1)), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat((String) result.deltas().get("enclosure_id")).isEqualTo("E2");
    }
  }
}
