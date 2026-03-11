package com.arknova.bot.engine;

import static com.arknova.bot.engine.ActionCard.*;
import static org.assertj.core.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the action card rotation mechanic — the core engine rule.
 *
 * <p>Run with: {@code mvn test -pl bot -Dtest=ActionCardOrderTest}
 */
@DisplayName("ActionCardOrder")
class ActionCardOrderTest {

  private ActionCardOrder order;

  @BeforeEach
  void setUp() {
    order = new ActionCardOrder(); // default: CARDS(1) BUILD(2) ANIMALS(3) ASSOCIATION(4) SPONSOR(5)
  }

  // ── Initial State ────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Initial state")
  class InitialState {

    @Test
    @DisplayName("default order matches official starting order")
    void defaultOrder() {
      assertThat(order.getOrder())
          .containsExactly(CARDS, BUILD, ANIMALS, ASSOCIATION, SPONSOR);
    }

    @Test
    @DisplayName("strengths are 1-indexed from left")
    void defaultStrengths() {
      assertThat(order.getStrength(CARDS)).isEqualTo(1);
      assertThat(order.getStrength(BUILD)).isEqualTo(2);
      assertThat(order.getStrength(ANIMALS)).isEqualTo(3);
      assertThat(order.getStrength(ASSOCIATION)).isEqualTo(4);
      assertThat(order.getStrength(SPONSOR)).isEqualTo(5);
    }

    @Test
    @DisplayName("no cards are upgraded by default")
    void noUpgrades() {
      for (ActionCard card : ActionCard.values()) {
        assertThat(order.isUpgraded(card)).isFalse();
      }
      assertThat(order.getUpgradedCards()).isEmpty();
    }
  }

  // ── Use Card ─────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("use()")
  class UseCard {

    @Test
    @DisplayName("returns the strength the card had before being used")
    void returnsStrengthBeforeUse() {
      assertThat(order.use(CARDS)).isEqualTo(1);
      assertThat(order.use(BUILD)).isEqualTo(2); // was at index 1 before CARDS moved
    }

    @Test
    @DisplayName("used card moves to leftmost position (strength 1)")
    void usedCardMovesToLeftmost() {
      order.use(ANIMALS); // ANIMALS was at position 3
      assertThat(order.getOrder().get(0)).isEqualTo(ANIMALS);
      assertThat(order.getStrength(ANIMALS)).isEqualTo(1);
    }

    @Test
    @DisplayName("cards to the left of the used card shift right by 1")
    void cardsToLeftShiftRight() {
      // Initial: CARDS(1) BUILD(2) ANIMALS(3) ASSOCIATION(4) SPONSOR(5)
      // Use ANIMALS (position 3, 0-indexed 2)
      order.use(ANIMALS);
      // ANIMALS moves to front; CARDS and BUILD each shift right by 1
      assertThat(order.getOrder()).containsExactly(ANIMALS, CARDS, BUILD, ASSOCIATION, SPONSOR);
      assertThat(order.getStrength(CARDS)).isEqualTo(2);
      assertThat(order.getStrength(BUILD)).isEqualTo(3);
    }

    @Test
    @DisplayName("cards to the right of the used card do not change position")
    void cardsToRightUnchanged() {
      // Initial: CARDS(1) BUILD(2) ANIMALS(3) ASSOCIATION(4) SPONSOR(5)
      // Use BUILD (position 2)
      order.use(BUILD);
      // ASSOCIATION and SPONSOR remain at their relative positions (now 4 and 5)
      assertThat(order.getStrength(ASSOCIATION)).isEqualTo(4);
      assertThat(order.getStrength(SPONSOR)).isEqualTo(5);
    }

    @Test
    @DisplayName("using the leftmost card (strength 1) results in no change")
    void useLeftmostCard_noChange() {
      List<ActionCard> before = order.getOrder();
      int strength = order.use(CARDS);
      assertThat(strength).isEqualTo(1);
      assertThat(order.getOrder()).isEqualTo(before);
    }

    @Test
    @DisplayName("using the rightmost card (strength 5) gives max strength and moves all others right")
    void useRightmostCard() {
      int strength = order.use(SPONSOR);
      assertThat(strength).isEqualTo(5);
      assertThat(order.getOrder())
          .containsExactly(SPONSOR, CARDS, BUILD, ANIMALS, ASSOCIATION);
    }

    @Test
    @DisplayName("sequential use produces correct rotation sequence")
    void sequentialUse() {
      // Simulate a realistic game sequence
      // Turn 1: player uses SPONSOR (strongest)
      order.use(SPONSOR);
      // [SPONSOR(1), CARDS(2), BUILD(3), ANIMALS(4), ASSOCIATION(5)]

      assertThat(order.getOrder())
          .containsExactly(SPONSOR, CARDS, BUILD, ANIMALS, ASSOCIATION);

      // Turn 2: player uses ASSOCIATION (now at strength 5)
      order.use(ASSOCIATION);
      // [ASSOCIATION(1), SPONSOR(2), CARDS(3), BUILD(4), ANIMALS(5)]
      assertThat(order.getOrder())
          .containsExactly(ASSOCIATION, SPONSOR, CARDS, BUILD, ANIMALS);
      assertThat(order.getStrength(ANIMALS)).isEqualTo(5);

      // Turn 3: player uses CARDS (now at strength 3)
      int strength = order.use(CARDS);
      assertThat(strength).isEqualTo(3);
      // [CARDS(1), ASSOCIATION(2), SPONSOR(3), BUILD(4), ANIMALS(5)]
      assertThat(order.getOrder())
          .containsExactly(CARDS, ASSOCIATION, SPONSOR, BUILD, ANIMALS);
    }

    @Test
    @DisplayName("always contains all 5 cards after use")
    void alwaysContainsAllFiveCards() {
      for (ActionCard card : ActionCard.values()) {
        order.use(card);
        assertThat(order.getOrder()).containsExactlyInAnyOrder(ActionCard.values());
      }
    }

    @Test
    @DisplayName("order always has exactly 5 elements after multiple uses")
    void orderSizeRemainsFixed() {
      order.use(ANIMALS);
      order.use(SPONSOR);
      order.use(BUILD);
      assertThat(order.getOrder()).hasSize(5);
    }
  }

  // ── Upgrade ──────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("upgrade()")
  class Upgrade {

    @Test
    @DisplayName("marks a card as upgraded")
    void marksAsUpgraded() {
      order.upgrade(BUILD);
      assertThat(order.isUpgraded(BUILD)).isTrue();
    }

    @Test
    @DisplayName("upgrading does not affect position or strength")
    void upgradeDoesNotAffectPosition() {
      int strengthBefore = order.getStrength(BUILD);
      order.upgrade(BUILD);
      assertThat(order.getStrength(BUILD)).isEqualTo(strengthBefore);
      assertThat(order.getOrder()).containsExactly(CARDS, BUILD, ANIMALS, ASSOCIATION, SPONSOR);
    }

    @Test
    @DisplayName("upgrading same card twice is idempotent")
    void upgradingTwiceIsIdempotent() {
      order.upgrade(ANIMALS);
      order.upgrade(ANIMALS);
      assertThat(order.getUpgradedCards()).containsExactly(ANIMALS);
    }

    @Test
    @DisplayName("multiple cards can be upgraded")
    void multipleUpgrades() {
      order.upgrade(BUILD);
      order.upgrade(SPONSOR);
      assertThat(order.getUpgradedCards()).containsExactlyInAnyOrder(BUILD, SPONSOR);
    }

    @Test
    @DisplayName("upgraded status persists through use")
    void upgradePersistsThroughUse() {
      order.upgrade(ANIMALS);
      order.use(ANIMALS);
      assertThat(order.isUpgraded(ANIMALS)).isTrue();
    }
  }

  // ── Serialisation ─────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("serialisation (toArrays / fromArrays)")
  class Serialisation {

    @Test
    @DisplayName("toOrderArray returns correct 5-element string array")
    void toOrderArray() {
      assertThat(order.toOrderArray())
          .containsExactly("CARDS", "BUILD", "ANIMALS", "ASSOCIATION", "SPONSOR");
    }

    @Test
    @DisplayName("toUpgradedArray returns empty array when no upgrades")
    void toUpgradedArray_empty() {
      assertThat(order.toUpgradedArray()).isEmpty();
    }

    @Test
    @DisplayName("toUpgradedArray returns correct names when upgrades present")
    void toUpgradedArray_withUpgrades() {
      order.upgrade(BUILD);
      order.upgrade(SPONSOR);
      assertThat(order.toUpgradedArray()).containsExactlyInAnyOrder("BUILD", "SPONSOR");
    }

    @Test
    @DisplayName("fromArrays round-trips correctly with default order")
    void fromArrays_defaultRoundTrip() {
      String[] orderArr = order.toOrderArray();
      String[] upgradedArr = order.toUpgradedArray();

      ActionCardOrder restored = ActionCardOrder.fromArrays(orderArr, upgradedArr);
      assertThat(restored.getOrder()).isEqualTo(order.getOrder());
      assertThat(restored.getUpgradedCards()).isEqualTo(order.getUpgradedCards());
    }

    @Test
    @DisplayName("fromArrays round-trips correctly after mutations")
    void fromArrays_mutatedRoundTrip() {
      order.use(SPONSOR);
      order.use(BUILD);
      order.upgrade(CARDS);

      ActionCardOrder restored =
          ActionCardOrder.fromArrays(order.toOrderArray(), order.toUpgradedArray());

      assertThat(restored.getOrder()).isEqualTo(order.getOrder());
      assertThat(restored.isUpgraded(CARDS)).isTrue();
      assertThat(restored.isUpgraded(BUILD)).isFalse();
    }

    @Test
    @DisplayName("fromArrays with null upgraded array treats as empty")
    void fromArrays_nullUpgradedArray() {
      ActionCardOrder restored =
          ActionCardOrder.fromArrays(
              new String[]{"CARDS", "BUILD", "ANIMALS", "ASSOCIATION", "SPONSOR"}, null);
      assertThat(restored.getUpgradedCards()).isEmpty();
    }
  }

  // ── Validation ────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("constructor rejects order with wrong number of cards")
    void rejectsWrongSize() {
      assertThatThrownBy(
              () -> new ActionCardOrder(List.of(CARDS, BUILD, ANIMALS), Set.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("exactly 5 cards");
    }

    @Test
    @DisplayName("constructor rejects duplicate cards")
    void rejectsDuplicates() {
      assertThatThrownBy(
              () ->
                  new ActionCardOrder(
                      List.of(CARDS, CARDS, ANIMALS, ASSOCIATION, SPONSOR),
                      EnumSet.noneOf(ActionCard.class)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ── Display ────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("toDiscordString()")
  class DiscordDisplay {

    @Test
    @DisplayName("contains all card names and strengths")
    void containsAllCardsAndStrengths() {
      String display = order.toDiscordString();
      for (ActionCard card : ActionCard.values()) {
        assertThat(display).contains(card.displayName());
        assertThat(display).contains(card.emoji());
      }
      assertThat(display).contains("(1)", "(2)", "(3)", "(4)", "(5)");
    }

    @Test
    @DisplayName("upgraded card shows upgrade marker")
    void upgradedCardShowsMarker() {
      order.upgrade(BUILD);
      assertThat(order.toDiscordString()).contains("Build★(2)");
    }
  }
}
