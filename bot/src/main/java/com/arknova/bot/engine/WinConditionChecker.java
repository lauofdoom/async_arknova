package com.arknova.bot.engine;

import com.arknova.bot.model.PlayerState;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Checks Ark Nova's win condition — and calculates the break score for multiplayer tiebreaking.
 *
 * <h2>Win condition</h2>
 * The game ends when a player's Appeal track marker and Conservation track marker "cross".
 * The tracks run in opposite directions on a combined scale from 0 to a map-dependent maximum.
 * They cross when:
 * <pre>
 *   appeal + conservation ≥ TRACK_TOTAL
 * </pre>
 * where {@code TRACK_TOTAL} is the sum of both track lengths (e.g. 113 + 80 = 193 for Map 1).
 * More precisely, the markers cross when the remaining distance on both tracks combined ≤ 0:
 * <pre>
 *   (appealMax - appeal) + (conservationMax - conservation) ≤ 0
 *   i.e. appeal ≥ appealMax - conservation + 0
 *   i.e. appeal + conservation ≥ appealMax (since the crossing point is at conservationMax = 0
 *        side and appealMax = 0 other side)
 * </pre>
 *
 * <h2>Standard map track lengths</h2>
 * <ul>
 *   <li>Appeal track: 0–113 (113 steps)
 *   <li>Conservation track: 0–80 (80 steps)
 *   <li>Combined: 193
 * </ul>
 * Tracks "cross" when {@code appeal + conservation ≥ 113} (Appeal track length).
 * This is equivalent to the markers meeting on the scoring wheel.
 *
 * <h2>Break score</h2>
 * The player who triggers the crossing wins (subject to final scoring round). In case of a tie,
 * the player with the higher break score wins. Break score = gap when tracks crossed:
 * <pre>
 *   breakScore = (appeal + conservation) - appealMax
 * </pre>
 * Higher break score = better position = wins tiebreaker.
 */
@Component
public class WinConditionChecker {

  /**
   * Appeal track length for standard maps (Map 1–6). The crossing threshold.
   * This is the value where appeal + conservation first meets or exceeds this number.
   */
  public static final int APPEAL_TRACK_MAX = 113;

  /** Conservation track length for standard maps. */
  public static final int CONSERVATION_TRACK_MAX = 80;

  /**
   * Check whether the given player has triggered the win condition (tracks crossed).
   *
   * @param player the player state to check
   * @return true if this player's tracks have crossed
   */
  public boolean hasWon(PlayerState player) {
    return player.getAppeal() + player.getConservation() >= APPEAL_TRACK_MAX;
  }

  /**
   * Calculate the break score — how far past the crossing point the player's tracks are.
   * Used for tiebreaking when multiple players cross in the same final round.
   * Higher break score wins.
   *
   * @param player the player state
   * @return break score (0 if tracks haven't crossed yet)
   */
  public int breakScore(PlayerState player) {
    int gap = player.getAppeal() + player.getConservation() - APPEAL_TRACK_MAX;
    return Math.max(0, gap);
  }

  /**
   * Determine the winner from a list of players who have all completed the final scoring round.
   * All players in this list have crossed their tracks; winner is determined by break score.
   *
   * @param players all players who completed the final round
   * @return the winning player
   */
  public PlayerState determineWinner(List<PlayerState> players) {
    return players.stream()
        .max(java.util.Comparator.comparingInt(this::breakScore))
        .orElseThrow(() -> new IllegalArgumentException("Player list is empty"));
  }

  /**
   * Check whether ALL players have completed their final scoring turns.
   * Used to determine when to end the game after the final scoring round starts.
   *
   * @param players all players in the game
   * @return true if every player has taken their final scoring turn
   */
  public boolean isFinalScoringComplete(List<PlayerState> players) {
    return players.stream().allMatch(PlayerState::isFinalScoringDone);
  }

  /**
   * Format the track positions as a display string.
   * Example: "Appeal: 87, Conservation: 34 → Break score: 8"
   */
  public String formatTrackDisplay(PlayerState player) {
    int appeal = player.getAppeal();
    int conservation = player.getConservation();
    int remaining = Math.max(0, APPEAL_TRACK_MAX - appeal - conservation);
    boolean crossed = hasWon(player);

    if (crossed) {
      return String.format("Appeal: %d, Conservation: %d → **TRACKS CROSSED** (break score: %d)",
          appeal, conservation, breakScore(player));
    } else {
      return String.format("Appeal: %d/%d, Conservation: %d/%d → %d more points to end game",
          appeal, APPEAL_TRACK_MAX, conservation, CONSERVATION_TRACK_MAX, remaining);
    }
  }
}
