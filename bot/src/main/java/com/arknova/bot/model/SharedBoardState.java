package com.arknova.bot.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The shared board state for one game — the association board, card display, and decks. One row per
 * game, keyed by game_id. Created when the game starts.
 */
@Entity
@Table(name = "shared_board_state")
@Getter
@Setter
public class SharedBoardState {

  @Id
  @Column(name = "game_id")
  private UUID gameId;

  /** Ordered animal draw deck — index 0 is the top card. */
  @Column(name = "animal_deck", columnDefinition = "varchar(20)[]", nullable = false)
  private String[] animalDeck = {};

  /** Ordered sponsor draw deck — index 0 is the top card. */
  @Column(name = "sponsor_deck", columnDefinition = "varchar(20)[]", nullable = false)
  private String[] sponsorDeck = {};

  /** Conservation project deck (smaller, used for setup). */
  @Column(name = "conservation_deck", columnDefinition = "varchar(20)[]", nullable = false)
  private String[] conservationDeck = {};

  /**
   * Association board partner zoo slots.
   *
   * <pre>
   * { "slots": [ { "card_id": "...", "owner_discord_id": "..." }, null, null ] }
   * </pre>
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "partner_zoo_slots", columnDefinition = "jsonb", nullable = false)
  private String partnerZooSlots = "{\"slots\":[]}";

  /**
   * Association board university slots.
   *
   * <pre>
   * { "slots": [ { "name": "...", "owner_discord_id": "..." }, null ] }
   * </pre>
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "university_slots", columnDefinition = "jsonb", nullable = false)
  private String universitySlots = "{\"slots\":[]}";

  /**
   * Conservation project board state.
   *
   * <pre>
   * { "projects": { "101": { "status": "available", "slots": [null, null, null] } } }
   * </pre>
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "conservation_board", columnDefinition = "jsonb", nullable = false)
  private String conservationBoard = "{\"projects\":{}}";

  /**
   * Break tokens awarded to players. Used for tiebreaking.
   *
   * <pre>{ "player1_discord_id": 2, "player2_discord_id": 1 }</pre>
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "break_tokens", columnDefinition = "jsonb", nullable = false)
  private String breakTokens = "{}";

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  // ── Convenience ──────────────────────────────────────────────────────────────

  public int animalDeckSize() {
    return animalDeck == null ? 0 : animalDeck.length;
  }

  public int sponsorDeckSize() {
    return sponsorDeck == null ? 0 : sponsorDeck.length;
  }

  public int totalDeckSize() {
    return animalDeckSize() + sponsorDeckSize();
  }
}
