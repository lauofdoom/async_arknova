package com.arknova.bot.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Manages the ordered row of a player's five action cards and their derived strengths.
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li>Cards are arranged left-to-right in a row.
 *   <li>Strength = 1-indexed position from the left (leftmost = 1, rightmost = 5).
 *   <li>When a card is <em>used</em>, it is removed from its current position and placed at
 *       position 1 (leftmost). All cards that were to its left shift one position to the right,
 *       gaining one strength.
 *   <li>Some cards can be permanently <em>upgraded</em> (e.g. via the BUILD action). The upgrade
 *       status is tracked separately and does not affect position logic.
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>
 * Initial: [CARDS(1), BUILD(2), ANIMALS(3), ASSOCIATION(4), SPONSOR(5)]
 * Use ANIMALS (strength 3):
 *   → [ANIMALS(1), CARDS(2), BUILD(3), ASSOCIATION(4), SPONSOR(5)]
 * Use SPONSOR (strength 5):
 *   → [SPONSOR(1), ANIMALS(2), CARDS(3), BUILD(4), ASSOCIATION(5)]
 * </pre>
 *
 * <p>This class is intentionally not a JPA entity — it is serialised to/from a {@code String[]}
 * column on {@link com.arknova.bot.model.PlayerState}.
 */
public final class ActionCardOrder {

  /** Default starting order per the official rules. */
  public static final List<ActionCard> DEFAULT_ORDER =
      List.of(
          ActionCard.CARDS,
          ActionCard.BUILD,
          ActionCard.ANIMALS,
          ActionCard.ASSOCIATION,
          ActionCard.SPONSOR);

  /** Mutable ordered list; index 0 = leftmost = strength 1. */
  private final List<ActionCard> order;

  /** Cards that have been permanently upgraded. */
  private final Set<ActionCard> upgraded;

  /** Construct with the official default starting order and no upgrades. */
  public ActionCardOrder() {
    this.order = new ArrayList<>(DEFAULT_ORDER);
    this.upgraded = EnumSet.noneOf(ActionCard.class);
  }

  /** Construct from persisted state (e.g. loaded from DB). */
  public ActionCardOrder(List<ActionCard> order, Set<ActionCard> upgraded) {
    if (order.size() != 5) {
      throw new IllegalArgumentException("ActionCardOrder must contain exactly 5 cards");
    }
    if (!EnumSet.allOf(ActionCard.class).equals(EnumSet.copyOf(order))) {
      throw new IllegalArgumentException("ActionCardOrder must contain each ActionCard exactly once");
    }
    this.order = new ArrayList<>(order);
    this.upgraded = EnumSet.copyOf(upgraded.isEmpty() ? EnumSet.noneOf(ActionCard.class) : upgraded);
  }

  // ── Queries ────────────────────────────────────────────────────────────────

  /**
   * Returns the current strength (1–5) of the given card. Strength equals the 1-based index from
   * the left.
   */
  public int getStrength(ActionCard card) {
    int idx = order.indexOf(card);
    if (idx < 0) throw new IllegalStateException("Card not found in order: " + card);
    return idx + 1;
  }

  /** Returns an unmodifiable snapshot of the current left-to-right order. */
  public List<ActionCard> getOrder() {
    return Collections.unmodifiableList(order);
  }

  /** Returns true if the given card has been permanently upgraded. */
  public boolean isUpgraded(ActionCard card) {
    return upgraded.contains(card);
  }

  /** Returns an unmodifiable view of all upgraded cards. */
  public Set<ActionCard> getUpgradedCards() {
    return Collections.unmodifiableSet(upgraded);
  }

  // ── Mutations ──────────────────────────────────────────────────────────────

  /**
   * Uses the given card: captures its current strength, moves it to the leftmost position
   * (strength 1), and returns the strength at the time of use.
   *
   * @param card the card being used
   * @return the strength (1–5) the card had when it was used
   */
  public int use(ActionCard card) {
    int strength = getStrength(card); // capture before mutation
    order.remove(card);
    order.add(0, card);
    return strength;
  }

  /**
   * Permanently upgrades the given card. Has no effect if already upgraded.
   *
   * @param card the card to upgrade
   */
  public void upgrade(ActionCard card) {
    upgraded.add(card);
  }

  // ── Serialisation ──────────────────────────────────────────────────────────

  /**
   * Serialises the order to a String array suitable for storage in the {@code
   * action_card_order} DB column (PostgreSQL text array).
   *
   * @return 5-element array of ActionCard names, e.g. ["CARDS","BUILD","ANIMALS","ASSOCIATION","SPONSOR"]
   */
  public String[] toOrderArray() {
    return order.stream().map(Enum::name).toArray(String[]::new);
  }

  /**
   * Serialises the upgraded set to a String array for the {@code upgraded_actions} DB column.
   *
   * @return array of upgraded ActionCard names (may be empty)
   */
  public String[] toUpgradedArray() {
    return upgraded.stream().map(Enum::name).toArray(String[]::new);
  }

  /**
   * Deserialises from DB column values.
   *
   * @param orderArray  5-element array of ActionCard names (must not be null)
   * @param upgradedArray array of upgraded card names (may be null or empty)
   */
  public static ActionCardOrder fromArrays(String[] orderArray, String[] upgradedArray) {
    List<ActionCard> order =
        Arrays.stream(orderArray).map(ActionCard::valueOf).toList();

    Set<ActionCard> upgraded = EnumSet.noneOf(ActionCard.class);
    if (upgradedArray != null) {
      Arrays.stream(upgradedArray).map(ActionCard::valueOf).forEach(upgraded::add);
    }

    return new ActionCardOrder(order, upgraded);
  }

  // ── Display ────────────────────────────────────────────────────────────────

  /**
   * Formats the action card strip for Discord embed display.
   *
   * <p>Example output:
   * <pre>
   * 🃏Cards(1)  🏗️Build(2)  🦁Animals(3)  🤝Association(4)  💼Sponsor(5)
   * </pre>
   * Upgraded cards are annotated with ★.
   */
  public String toDiscordString() {
    StringJoiner sj = new StringJoiner("  ");
    for (int i = 0; i < order.size(); i++) {
      ActionCard card = order.get(i);
      int strength = i + 1;
      String upgradeMarker = upgraded.contains(card) ? "★" : "";
      sj.add(card.emoji() + card.displayName() + upgradeMarker + "(" + strength + ")");
    }
    return sj.toString();
  }

  @Override
  public String toString() {
    return "ActionCardOrder{order=" + order + ", upgraded=" + upgraded + "}";
  }
}
