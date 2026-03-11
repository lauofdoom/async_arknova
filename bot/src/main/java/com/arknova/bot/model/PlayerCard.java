package com.arknova.bot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks the location of a specific card instance within a game for one player. A card is
 * instantiated into this table when the game starts (shuffled into decks) and transitions through
 * locations as the game progresses.
 *
 * <pre>
 * DECK → DISPLAY → HAND → PLAYED → PLACED (on zoo board)
 *                                → DISCARD
 * </pre>
 */
@Entity
@Table(
    name = "player_cards",
    indexes = {
      @Index(
          name = "idx_player_cards_game_player_loc",
          columnList = "game_id, discord_id, location")
    })
@Getter
@Setter
public class PlayerCard {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "game_id", nullable = false)
  private java.util.UUID gameId;

  @Column(name = "discord_id", nullable = false, length = 20)
  private String discordId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "card_id", nullable = false)
  private CardDefinition card;

  @Column(name = "location", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private CardLocation location;

  /**
   * Reference to which enclosure this card is placed in (only set when {@code location = PLACED}).
   * Matches the enclosure ID in the board_state JSONB (e.g. "E1").
   */
  @Column(name = "enclosure_ref", length = 10)
  private String enclosureRef;

  /** Order within the location (used to maintain deck order and hand display order). */
  @Column(name = "sort_order")
  private Integer sortOrder;

  public enum CardLocation {
    /** In the shuffled draw deck. */
    DECK,
    /** Face-up in the shared card display (market). */
    DISPLAY,
    /** In the player's hand. */
    HAND,
    /** Sponsor card that has been played (effect resolved, in "played" area). */
    PLAYED,
    /** Animal card physically placed on the zoo board inside an enclosure. */
    PLACED,
    /** Discarded. */
    DISCARD
  }
}
