package com.arknova.bot.engine;

/**
 * Constants for Ark Nova scoring rules.
 *
 * <p>The appeal and conservation tracks ARE the VP tracks — positions on those tracks represent
 * victory points directly. This class holds the maximum values for each track, bonus VP per partner
 * zoo / university, and X token scoring constants.
 *
 * <h2>Final VP breakdown</h2>
 *
 * <ol>
 *   <li>Appeal track position (direct VP)
 *   <li>Conservation track position (direct VP)
 *   <li>Placed animal appeal values (already included in appeal track — tracked via action)
 *   <li>Partner zoo bonus: {@link #PARTNER_ZOO_VP} per completed slot
 *   <li>University bonus: {@link #UNIVERSITY_VP} per completed slot
 *   <li>FINAL_SCORING cards in hand (manual resolution — shown but not auto-calculated)
 *   <li>X tokens: {@link #xTokenVp} based on break track position
 * </ol>
 */
public final class ScoringTables {

  // ── Track Maximums ────────────────────────────────────────────────────────

  /** Maximum position on the appeal track. Game ends when appeal + conservation ≥ this. */
  public static final int APPEAL_TRACK_MAX = WinConditionChecker.APPEAL_TRACK_MAX;

  /** Maximum position on the conservation track. */
  public static final int CONSERVATION_TRACK_MAX = WinConditionChecker.CONSERVATION_TRACK_MAX;

  /** Maximum reputation level. */
  public static final int REPUTATION_MAX = 15;

  // ── End-Game Bonuses ──────────────────────────────────────────────────────

  /**
   * VP awarded per completed partner zoo slot at end game. A partner zoo is "completed" when the
   * player has contributed a worker to a conservation project that matches. In this implementation
   * each entry in the {@code conservationSlots.partnerZoos} array that is non-null counts as one
   * completed slot.
   */
  public static final int PARTNER_ZOO_VP = 3;

  /**
   * VP awarded per completed university slot at end game. Same counting logic as partner zoos —
   * each non-null entry in {@code conservationSlots.universities} counts.
   */
  public static final int UNIVERSITY_VP = 4;

  // ── X Token Scoring ───────────────────────────────────────────────────────

  /**
   * Calculate VP from X tokens. Each X token scores VP equal to the player's break track position.
   *
   * @param xTokens number of X tokens the player has
   * @param breakTrack the player's break track position
   * @return total VP from X tokens
   */
  public static int xTokenVp(int xTokens, int breakTrack) {
    return xTokens * breakTrack;
  }

  private ScoringTables() {}
}
