package com.arknova.bot.model;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.ActionCardOrder;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The complete state of one player within a game. Updated after every action the player takes.
 *
 * <p>The zoo board layout ({@code boardState}) is stored as JSONB because its structure grows as
 * enclosures are built and animals are placed. All other frequently-queried values (tracks,
 * resources) are proper columns.
 */
@Entity
@Table(
    name = "player_states",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_player_states_game_discord",
          columnNames = {"game_id", "discord_id"}),
      @UniqueConstraint(
          name = "uq_player_states_game_seat",
          columnNames = {"game_id", "seat_index"})
    })
@Getter
@Setter
public class PlayerState {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "game_id", nullable = false)
  private Game game;

  @Column(name = "discord_id", nullable = false, length = 20)
  private String discordId;

  @Column(name = "discord_name", nullable = false, length = 100)
  private String discordName;

  /** 0-based turn order index. */
  @Column(name = "seat_index", nullable = false)
  private int seatIndex;

  /** Which zoo map this player is using. e.g. "MAP_1" .. "MAP_6" */
  @Column(name = "map_id", nullable = false, length = 20)
  private String mapId = "MAP_1";

  // ── Tracks ───────────────────────────────────────────────────────────────────

  @Column(name = "appeal", nullable = false)
  private int appeal = 0;

  @Column(name = "conservation", nullable = false)
  private int conservation = 0;

  // ── Resources ────────────────────────────────────────────────────────────────

  @Column(name = "money", nullable = false)
  private int money = 25;

  @Column(name = "x_tokens", nullable = false)
  private int xTokens = 0;

  @Column(name = "reputation", nullable = false)
  private int reputation = 0;

  /** Total capacity from placed keeper cards. NOT the count of placed keepers. */
  @Column(name = "zoo_keepers_capacity", nullable = false)
  private int zooKeepersCapacity = 0;

  @Column(name = "assoc_workers", nullable = false)
  private int assocWorkers = 3;

  @Column(name = "assoc_workers_available", nullable = false)
  private int assocWorkersAvailable = 3;

  // ── Action Card System ───────────────────────────────────────────────────────

  /**
   * Left-to-right order of the 5 action cards. Index 0 = leftmost = strength 1. Stored as a
   * PostgreSQL text array.
   *
   * <p>Do not access directly — use {@link #getActionCardOrder()} which wraps this in the domain
   * object.
   */
  @Column(name = "action_card_order", columnDefinition = "varchar(15)[]", nullable = false)
  private String[] actionCardOrderRaw = {"CARDS", "BUILD", "ANIMALS", "ASSOCIATION", "SPONSOR"};

  /** Permanently upgraded action cards. Stored as PostgreSQL text array. */
  @Column(name = "upgraded_actions", columnDefinition = "varchar(15)[]", nullable = false)
  private String[] upgradedActionsRaw = {};

  // ── Zoo Board ────────────────────────────────────────────────────────────────

  /**
   * Full zoo board layout as JSONB.
   *
   * <pre>
   * {
   *   "enclosures": [
   *     { "id": "E1", "size": 3, "row": 1, "col": 2, "tags": [],
   *       "animalCardIds": ["401"] }
   *   ],
   *   "kioskCount": 0,
   *   "pavilionBuilt": false,
   *   "specialBuildings": []
   * }
   * </pre>
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "board_state", columnDefinition = "jsonb", nullable = false)
  private String boardState =
      "{\"enclosures\":[],\"kioskCount\":0,\"pavilionBuilt\":false,\"specialBuildings\":[]}";

  /** Conservation board slot allocations (projects, partner zoos, universities) as JSONB. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "conservation_slots", columnDefinition = "jsonb", nullable = false)
  private String conservationSlots =
      "{\"projects\":[null,null],\"partnerZoos\":[null,null,null],\"universities\":[null,null]}";

  /**
   * Count of collected icons for each tag type. Used to calculate synergy bonuses.
   *
   * <pre>
   * { "AFRICA": 3, "PREDATOR": 2, "BIRD": 1 }
   * </pre>
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "icons", columnDefinition = "jsonb", nullable = false)
  private String icons = "{}";

  /**
   * Number of cards the player must still discard to complete their CARDS action. Non-zero means
   * this player's turn is not yet complete.
   */
  @Column(name = "pending_discard_count", nullable = false)
  private int pendingDiscardCount = 0;

  /** Discord private channel ID for this player in this game. Null if channel setup not yet run. */
  @Column(name = "private_channel_id", length = 20)
  private String privateChannelId;

  /** Set to true once this player has taken their final scoring turn. */
  @Column(name = "final_scoring_done", nullable = false)
  private boolean finalScoringDone = false;

  /** Populated at game end with the VP breakdown for this player. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "final_vp", columnDefinition = "jsonb")
  private String finalVp;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  // ── Domain Helpers ────────────────────────────────────────────────────────────

  /**
   * Returns a domain-object representation of this player's action card order. Changes to the
   * returned object are NOT automatically persisted — call {@link #setActionCardOrder} to write
   * back.
   */
  public ActionCardOrder getActionCardOrder() {
    return ActionCardOrder.fromArrays(actionCardOrderRaw, upgradedActionsRaw);
  }

  /** Persists changes made to an {@link ActionCardOrder} back to this entity's raw columns. */
  public void setActionCardOrder(ActionCardOrder cardOrder) {
    this.actionCardOrderRaw = cardOrder.toOrderArray();
    this.upgradedActionsRaw = cardOrder.toUpgradedArray();
  }

  /** Returns the strength (1–5) of the specified action card. */
  public int getStrengthOf(ActionCard card) {
    return getActionCardOrder().getStrength(card);
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
