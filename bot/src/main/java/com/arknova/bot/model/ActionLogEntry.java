package com.arknova.bot.model;

import com.arknova.bot.engine.ActionCard;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable record of every action taken in a game. Used for action log display and undo. */
@Entity
@Table(
    name = "action_log",
    indexes = {
      @Index(name = "idx_action_log_game_turn", columnList = "game_id, turn_number"),
      @Index(name = "idx_action_log_game_player", columnList = "game_id, discord_id")
    })
@Getter
@Setter
public class ActionLogEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "game_id", nullable = false)
  private UUID gameId;

  @Column(name = "turn_number", nullable = false)
  private int turnNumber;

  @Column(name = "discord_id", nullable = false, length = 20)
  private String discordId;

  @Column(name = "discord_name", nullable = false, length = 100)
  private String discordName;

  @Column(name = "action_type", nullable = false, length = 30)
  private String actionType;

  @Column(name = "action_card", length = 15)
  @Enumerated(EnumType.STRING)
  private ActionCard actionCard;

  @Column(name = "strength_used")
  private Integer strengthUsed;

  /**
   * All parameters of this action (card IDs, locations, amounts, etc.) encoded as JSONB. The
   * exact schema varies by action type and is documented in the engine layer.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "params", columnDefinition = "jsonb", nullable = false)
  private String params = "{}";

  /**
   * The state changes that resulted from this action. Used to display human-readable action
   * summaries and to support undo. Null for manual-resolution actions until the player confirms
   * the outcome.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result", columnDefinition = "jsonb")
  private String result;

  /**
   * True when this action involved a card with no {@code effect_code}. The player had to manually
   * report the effect.
   */
  @Column(name = "requires_manual", nullable = false)
  private boolean requiresManual = false;

  /** Discord message ID that triggered this action. Useful for linking back to the original message. */
  @Column(name = "discord_message_id", length = 20)
  private String discordMessageId;

  /** ID of the state snapshot taken before this action (for undo support). */
  @Column(name = "snapshot_id")
  private UUID snapshotId;

  @Column(name = "ts", nullable = false, updatable = false)
  private Instant ts = Instant.now();
}
