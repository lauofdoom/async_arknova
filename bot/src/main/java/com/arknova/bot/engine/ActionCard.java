package com.arknova.bot.engine;

/**
 * The five action cards every player has. Their left-to-right position in the player's row
 * determines their current strength (1 = leftmost/weakest, 5 = rightmost/strongest).
 *
 * <p>When a card is used it moves to position 1 (leftmost), shifting all cards to its left one
 * position rightward.
 */
public enum ActionCard {
  CARDS,
  BUILD,
  ANIMALS,
  ASSOCIATION,
  SPONSOR;

  /** Display name shown to players in Discord embeds. */
  public String displayName() {
    return switch (this) {
      case CARDS -> "Cards";
      case BUILD -> "Build";
      case ANIMALS -> "Animals";
      case ASSOCIATION -> "Association";
      case SPONSOR -> "Sponsor";
    };
  }

  /** Emoji prefix for Discord formatting. */
  public String emoji() {
    return switch (this) {
      case CARDS -> "🃏";
      case BUILD -> "🏗️";
      case ANIMALS -> "🦁";
      case ASSOCIATION -> "🤝";
      case SPONSOR -> "💼";
    };
  }
}
