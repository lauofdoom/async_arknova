package com.arknova.bot.model;

import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A card definition from the Ark Nova base game card database. Populated at startup from {@code
 * cards/base_game.json} (which is generated from the Next-Ark-Nova-Cards community repo).
 *
 * <p>The {@code effectCode} field is null for cards where machine-executable effects have not yet
 * been implemented. In those cases, the bot displays {@code abilityText} to the player and
 * requests manual resolution. This is the "progressive automation" model that lets us ship an
 * alpha before all effects are coded.
 */
@Entity
@Table(name = "card_definitions")
@Getter
@Setter
public class CardDefinition {

  /**
   * Card ID matching the official card number (e.g. "401" for Lion). Sourced directly from
   * Next-Ark-Nova-Cards.
   */
  @Id
  @Column(name = "id", length = 20)
  private String id;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "card_type", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private CardType cardType;

  // ── Cost & Requirements ───────────────────────────────────────────────────────

  @Column(name = "base_cost", nullable = false)
  private int baseCost = 0;

  /** Minimum enclosure size required to place this animal (null for non-animals). */
  @Column(name = "min_enclosure_size")
  private Integer minEnclosureSize;

  // ── Tags ──────────────────────────────────────────────────────────────────────

  /** Tags this card has (e.g. AFRICA, PREDATOR, BIRD). PostgreSQL text array. */
  @Column(name = "tags", columnDefinition = "varchar(20)[]", nullable = false)
  private String[] tags = {};

  /** Tags the enclosure must have to accept this animal (e.g. WATER, ROCK). */
  @Column(name = "requirements", columnDefinition = "varchar(20)[]", nullable = false)
  private String[] requirements = {};

  // ── Scoring ───────────────────────────────────────────────────────────────────

  @Column(name = "appeal_value", nullable = false)
  private int appealValue = 0;

  @Column(name = "conservation_value", nullable = false)
  private int conservationValue = 0;

  @Column(name = "reputation_value", nullable = false)
  private int reputationValue = 0;

  // ── Ability Text (display only) ───────────────────────────────────────────────

  /**
   * Human-readable ability description sourced from Next-Ark-Nova-Cards. Always present. Displayed
   * to players when the card is played or viewed.
   */
  @Column(name = "ability_text", columnDefinition = "text")
  private String abilityText;

  // ── Machine-Executable Effect (progressive) ────────────────────────────────────

  /**
   * JSON-encoded machine-executable effect(s). NULL means effects must be resolved manually.
   *
   * <p>Schema:
   *
   * <pre>
   * {
   *   "abilities": [
   *     { "trigger": "ON_PLAY", "type": "GAIN", "resource": "MONEY", "amount": 3 },
   *     { "trigger": "ON_PLAY", "type": "CONDITIONAL_GAIN",
   *       "condition": { "type": "MIN_ICON", "icon": "PREDATOR", "count": 2 },
   *       "resource": "APPEAL", "amount": 1 }
   *   ]
   * }
   * </pre>
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "effect_code", columnDefinition = "jsonb")
  private String effectCode;

  // ── Asset Reference ────────────────────────────────────────────────────────────

  /** URL to the card image (sourced from Next-Ark-Nova-Cards). May be null. */
  @Column(name = "image_url", length = 500)
  private String imageUrl;

  // ── Metadata ──────────────────────────────────────────────────────────────────

  @Column(name = "source", nullable = false, length = 20)
  private String source = "BASE";

  @Column(name = "card_number", length = 10)
  private String cardNumber;

  // ── Domain Helpers ────────────────────────────────────────────────────────────

  /** True if the engine can automatically resolve this card's effects. */
  public boolean isAutomated() {
    return effectCode != null && !effectCode.isBlank();
  }

  /** True if this card requires the player to manually report effect resolution. */
  public boolean requiresManualResolution() {
    return !isAutomated();
  }

  public List<String> getTagList() {
    return tags == null ? List.of() : List.of(tags);
  }

  public List<String> getRequirementList() {
    return requirements == null ? List.of() : List.of(requirements);
  }

  public enum CardType {
    ANIMAL,
    SPONSOR,
    CONSERVATION,
    FINAL_SCORING
  }
}
