package com.arknova.bot.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One Ark Nova game instance. A game is scoped to a single Discord thread and progresses through:
 * SETUP → ACTIVE → FINAL_SCORING → ENDED (or ABANDONED).
 */
@Entity
@Table(name = "games")
@Getter
@Setter
public class Game {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** The Discord guild (server) this game belongs to. */
  @Column(name = "guild_id", nullable = false, length = 20)
  private String guildId;

  /** The Discord thread created for this game. One game = one thread. */
  @Column(name = "thread_id", nullable = false, unique = true, length = 20)
  private String threadId;

  /** Discord category created for this game on start. Null if channel setup not yet run. */
  @Column(name = "category_id", length = 20)
  private String categoryId;

  /** Discord #board channel ID for this game. Null if channel setup not yet run. */
  @Column(name = "board_channel_id", length = 20)
  private String boardChannelId;

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private GameStatus status = GameStatus.SETUP;

  /**
   * Seat index of the player whose turn it currently is. Indexes into the {@link PlayerState} rows
   * ordered by {@code seat_index}.
   */
  @Column(name = "current_seat", nullable = false)
  private int currentSeat = 0;

  /** Monotonically incrementing turn counter. Incremented after each player action. */
  @Column(name = "turn_number", nullable = false)
  private int turnNumber = 0;

  /**
   * True once any player's tracks have crossed and the final scoring round has been triggered. The
   * remaining players each take one more turn before the game ends.
   */
  @Column(name = "final_scoring_triggered", nullable = false)
  private boolean finalScoringTriggered = false;

  /**
   * Flexible game settings stored as JSONB. Contains: player_count, starting_money_override,
   * map_ids[], etc.
   *
   * <p>Avoid evolving this casually — prefer dedicated columns for settings queried in SQL.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "settings", columnDefinition = "jsonb", nullable = false)
  private String settings = "{}";

  /**
   * Optimistic locking — incremented on every update. Prevents lost updates from race conditions.
   */
  @Version
  @Column(name = "state_version", nullable = false)
  private int stateVersion = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  // ── Convenience ─────────────────────────────────────────────────────────────

  public boolean isActive() {
    return status == GameStatus.ACTIVE;
  }

  public boolean isSetup() {
    return status == GameStatus.SETUP;
  }

  public boolean isFinished() {
    return status == GameStatus.ENDED || status == GameStatus.ABANDONED;
  }

  public enum GameStatus {
    SETUP,
    ACTIVE,
    FINAL_SCORING,
    ENDED,
    ABANDONED
  }
}
