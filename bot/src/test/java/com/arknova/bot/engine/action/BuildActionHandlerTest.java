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
  // Per card: maximum size of a SINGLE building = X (strength). Upgrade unlocks
  // multi-build (total ≤ X), not a larger individual building.

  @Nested
  @DisplayName("maxSizeForStrength()")
  class MaxSize {

    @Test
    @DisplayName("strength 1: max 1")
    void s1() {
      assertThat(maxSizeForStrength(1, false)).isEqualTo(1);
    }

    @Test
    @DisplayName("strength 2: max 2")
    void s2() {
      assertThat(maxSizeForStrength(2, false)).isEqualTo(2);
    }

    @Test
    @DisplayName("strength 3: max 3")
    void s3() {
      assertThat(maxSizeForStrength(3, false)).isEqualTo(3);
    }

    @Test
    @DisplayName("strength 4: max 4")
    void s4() {
      assertThat(maxSizeForStrength(4, false)).isEqualTo(4);
    }

    @Test
    @DisplayName("strength 5: max 5")
    void s5() {
      assertThat(maxSizeForStrength(5, false)).isEqualTo(5);
    }

    @Test
    @DisplayName("strength 5 upgraded: still max 5 per building")
    void s5u() {
      assertThat(maxSizeForStrength(5, true)).isEqualTo(5);
    }

    @Test
    @DisplayName("strength 1 upgraded: still max 1 per building")
    void s1u() {
      assertThat(maxSizeForStrength(1, true)).isEqualTo(1);
    }
  }

  // ── Validation ────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("fails when size is missing or zero")
    void missingSize() {
      ActionResult result = handler.execute(req(Map.of("row", 0, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("size");
    }

    @Test
    @DisplayName("fails when row is missing")
    void missingRow() {
      ActionResult result = handler.execute(req(Map.of("size", 1, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("location");
    }

    @Test
    @DisplayName("fails when col is missing")
    void missingCol() {
      ActionResult result = handler.execute(req(Map.of("size", 1, "row", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("location");
    }

    @Test
    @DisplayName("fails when enclosure size exceeds strength X")
    void sizeTooLargeForStrength() {
      setBuildStrength(1); // X=1, max size 1
      ActionResult result =
          handler.execute(req(Map.of("size", 2, "row", 0, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("maximum enclosure size is 1");
    }

    @Test
    @DisplayName("fails when player cannot afford the enclosure")
    void insufficientMoney() {
      setBuildStrength(2); // size-2 costs 2×2 = 4
      player.setMoney(3);
      ActionResult result =
          handler.execute(req(Map.of("size", 2, "row", 0, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("money");
    }

    @Test
    @DisplayName("fails when building at an occupied grid location")
    void gridCollision() {
      player.setBoardState(BOARD_E1_AT_0_0);
      setBuildStrength(2);
      ActionResult result =
          handler.execute(req(Map.of("size", 2, "row", 0, "col", 0)), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("already has a structure");
    }

    @Test
    @DisplayName("multi-build rejected on un-upgraded card")
    void multiBuildRequiresUpgrade() {
      setBuildStrength(3);
      ActionResult result =
          handler.execute(
              req(
                  Map.of(
                      "buildings",
                      List.of(
                          Map.of("size", 1, "row", 0, "col", 0, "tags", List.of()),
                          Map.of("size", 1, "row", 1, "col", 0, "tags", List.of())))),
              player,
              sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("upgraded");
    }

    @Test
    @DisplayName("upgrade sub-action fails when BUILD strength is below 4")
    void upgradeRequiresStrength4() {
      setBuildStrength(3);
      ActionResult result =
          handler.execute(req(Map.of("upgrade_action", "CARDS")), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("strength 4");
    }

    @Test
    @DisplayName("upgrade sub-action fails for unknown action card name")
    void upgradeUnknownCard() {
      setBuildStrength(4);
      ActionResult result =
          handler.execute(req(Map.of("upgrade_action", "INVALID")), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("Unknown action card");
    }

    @Test
    @DisplayName("upgrade sub-action fails when target card is already upgraded")
    void upgradeAlreadyDone() {
      List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
      cards.remove(ActionCard.BUILD);
      cards.add(3, ActionCard.BUILD); // strength 4
      player.setActionCardOrder(new ActionCardOrder(cards, Set.of(ActionCard.CARDS)));

      ActionResult result =
          handler.execute(req(Map.of("upgrade_action", "CARDS")), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("already upgraded");
    }

    @Test
    @DisplayName("upgrade sub-action fails when player cannot afford it")
    void upgradeInsufficientMoney() {
      setBuildStrength(4);
      player.setMoney(3); // upgrade costs 4
      ActionResult result =
          handler.execute(req(Map.of("upgrade_action", "ANIMALS")), player, sharedBoard);
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("money");
    }
  }

  // ── Happy paths ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("happy paths")
  class HappyPaths {

    @Test
    @DisplayName("strength 1: builds size-1 enclosure, costs 2 money (1 × 2)")
    void strength1BuildSize1() {
      setBuildStrength(1);
      player.setMoney(10);

      ActionResult result =
          handler.execute(req(Map.of("size", 1, "row", 0, "col", 0)), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(8); // 10 - (1×2)
      assertThat(result.summary()).contains("E1");
      assertThat((String) result.deltas().get("enclosure_id")).isEqualTo("E1");
    }

    @Test
    @DisplayName("size-5 enclosure costs 10 money (5 × 2)")
    void size5Cost() {
      setBuildStrength(5);
      player.setMoney(20);

      ActionResult result =
          handler.execute(req(Map.of("size", 5, "row", 0, "col", 0)), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(10); // 20 - (5×2)
    }

    @Test
    @DisplayName("terrain tags add 2 money each to the base cost")
    void terrainTagsCost() {
      setBuildStrength(3);
      player.setMoney(20);
      // size-3 base = 6; + WATER (2) + ROCK (2) = 10 total

      ActionResult result =
          handler.execute(
              req(Map.of("size", 3, "row", 0, "col", 0, "tags", List.of("WATER", "ROCK"))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(10); // 20 - 10
    }

    @Test
    @DisplayName("unknown terrain tags are silently ignored")
    void unknownTagsIgnored() {
      setBuildStrength(3);
      player.setMoney(20);

      ActionResult result =
          handler.execute(
              req(Map.of("size", 3, "row", 0, "col", 0, "tags", List.of("MAGIC", "WATER"))),
              player,
              sharedBoard);

      // Only WATER applies (+2), MAGIC ignored → cost = 6 + 2 = 8
      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(12); // 20 - 8
    }

    @Test
    @DisplayName("upgraded BUILD at strength 5: multi-build with total size ≤ 5")
    void upgradedMultiBuild() {
      setBuildStrengthUpgraded(5);
      player.setMoney(30);
      // Two buildings: size 3 + size 2 = total 5 ≤ X=5. Cost = (3+2)×2 = 10.

      ActionResult result =
          handler.execute(
              req(
                  Map.of(
                      "buildings",
                      List.of(
                          Map.of("size", 3, "row", 0, "col", 0, "tags", List.of()),
                          Map.of("size", 2, "row", 1, "col", 0, "tags", List.of())))),
              player,
              sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(20); // 30 - 10
    }

    @Test
    @DisplayName("upgraded BUILD: multi-build fails when total size exceeds X")
    void upgradedMultiBuildTotalExceedsX() {
      setBuildStrengthUpgraded(3); // X=3
      player.setMoney(30);

      ActionResult result =
          handler.execute(
              req(
                  Map.of(
                      "buildings",
                      List.of(
                          Map.of("size", 2, "row", 0, "col", 0, "tags", List.of()),
                          Map.of(
                              "size", 2, "row", 1, "col", 0, "tags", List.of())))), // total=4 > X=3
              player,
              sharedBoard);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).containsIgnoringCase("total");
    }

    @Test
    @DisplayName("upgrade sub-action: marks target card as upgraded, costs 4 money")
    void upgradeSuccess() {
      setBuildStrength(4);
      player.setMoney(10);

      ActionResult result =
          handler.execute(req(Map.of("upgrade_action", "ANIMALS")), player, sharedBoard);

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

      ActionResult result =
          handler.execute(req(Map.of("upgrade_action", "cards")), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat(player.getActionCardOrder().isUpgraded(ActionCard.CARDS)).isTrue();
    }

    @Test
    @DisplayName("second enclosure gets sequential ID (E2)")
    void sequentialEnclosureIds() {
      player.setBoardState(BOARD_E1_AT_0_0);
      setBuildStrength(2);
      player.setMoney(20);

      ActionResult result =
          handler.execute(req(Map.of("size", 2, "row", 1, "col", 1)), player, sharedBoard);

      assertThat(result.success()).isTrue();
      assertThat((String) result.deltas().get("enclosure_id")).isEqualTo("E2");
    }
  }
}
