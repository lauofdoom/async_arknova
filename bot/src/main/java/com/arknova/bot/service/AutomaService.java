package com.arknova.bot.service;

import com.arknova.bot.discord.DiscordChannelService;
import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.ActionCardOrder;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.repository.PlayerStateRepository;
import com.arknova.bot.repository.SharedBoardStateRepository;
import java.awt.Color;
import net.dv8tion.jda.api.EmbedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes turns on behalf of the automa (single-player AI opponent).
 *
 * <p>The automa strategy is deliberately simple — it exists to keep the game moving for solo
 * testing and single-player play, not to provide a challenging opponent:
 *
 * <ul>
 *   <li>Always plays the highest-strength action card (rightmost in its strip).
 *   <li>Applies a fixed resource gain per action type, scaled by card strength.
 *   <li>Rotates the used card to position 1, exactly like a human player.
 *   <li>Posts a summary to the {@code #board} channel, then advances the turn.
 * </ul>
 *
 * <p>The {@link DiscordChannelService} is injected {@code @Lazy} to avoid circular beans.
 */
@Service
public class AutomaService {

  private static final Logger log = LoggerFactory.getLogger(AutomaService.class);

  private static final Color AUTOMA_COLOR = new Color(0x95A5A6);

  private final PlayerStateRepository playerStateRepo;
  private final SharedBoardStateRepository sharedBoardRepo;
  private final GameService gameService;
  private final DiscordChannelService channelService;

  public AutomaService(
      PlayerStateRepository playerStateRepo,
      SharedBoardStateRepository sharedBoardRepo,
      GameService gameService,
      @Lazy DiscordChannelService channelService) {
    this.playerStateRepo = playerStateRepo;
    this.sharedBoardRepo = sharedBoardRepo;
    this.gameService = gameService;
    this.channelService = channelService;
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  /**
   * Executes a full automa turn: picks the strongest action card, applies gains, rotates the card,
   * posts a board-channel summary, and advances the turn counter.
   *
   * @param game the game whose current seat belongs to the automa
   * @return the game state after the automa's turn has been advanced
   */
  @Transactional
  public Game executeTurn(Game game) {
    PlayerState automa =
        playerStateRepo
            .findByGameIdAndSeatIndex(game.getId(), game.getCurrentSeat())
            .orElseThrow(
                () ->
                    new IllegalStateException("No automa found at seat " + game.getCurrentSeat()));

    if (!automa.isAutoma()) {
      throw new IllegalStateException("Player at current seat is not an automa.");
    }

    // Pick the strongest action card (rightmost = index 4)
    ActionCardOrder cardOrder = automa.getActionCardOrder();
    ActionCard chosen = cardOrder.getOrder().get(4);
    int strength = cardOrder.use(chosen);

    // Apply gains and build the action description
    String actionDescription = applyGains(game, automa, chosen, strength);

    // Persist updated action card order
    automa.setActionCardOrder(cardOrder);
    playerStateRepo.save(automa);

    log.info(
        "Automa turn in game {}: played {} (strength {}) — {}",
        game.getId(),
        chosen,
        strength,
        actionDescription);

    // Post turn summary to #board channel (fire-and-forget)
    EmbedBuilder embed =
        new EmbedBuilder()
            .setColor(AUTOMA_COLOR)
            .setTitle("🤖 Automa Turn")
            .setDescription(actionDescription)
            .addField(
                "Action Card",
                chosen.emoji() + " " + chosen.displayName() + " (strength " + strength + ")",
                true)
            .addField("New Strip", cardOrder.toDiscordString(), false)
            .setFooter("Automa plays automatically — your turn is next");
    channelService.postToBoardChannel(game, embed);

    // Advance the turn (automa never has pending discards)
    return gameService.endTurn(game.getThreadId(), GameService.AUTOMA_DISCORD_ID);
  }

  // ── Internal ───────────────────────────────────────────────────────────────

  /**
   * Applies the gain for the chosen action card and returns a human-readable description.
   *
   * <p>Gain table:
   *
   * <ul>
   *   <li><b>CARDS</b> — advances the shared break track by 1 (applies tempo pressure)
   *   <li><b>BUILD</b> — gains {@code strength × 2} money
   *   <li><b>ANIMALS</b> — gains {@code strength} appeal
   *   <li><b>ASSOCIATION</b> — gains 1 conservation point
   *   <li><b>SPONSOR</b> — gains {@code strength} money + 1 appeal
   * </ul>
   */
  private String applyGains(Game game, PlayerState automa, ActionCard card, int strength) {
    return switch (card) {
      case CARDS -> {
        SharedBoardState shared =
            sharedBoardRepo
                .findByGameId(game.getId())
                .orElseThrow(() -> new IllegalStateException("Shared board not found"));
        shared.setBreakTrack(shared.getBreakTrack() + 1);
        sharedBoardRepo.save(shared);
        yield "Advanced the **break track** by 1 (now " + shared.getBreakTrack() + ")";
      }

      case BUILD -> {
        int gain = strength * 2;
        automa.setMoney(automa.getMoney() + gain);
        yield "Gained **" + gain + " 💰** (strength " + strength + " × 2)";
      }

      case ANIMALS -> {
        automa.setAppeal(automa.getAppeal() + strength);
        yield "Gained **" + strength + " ⭐ appeal**";
      }

      case ASSOCIATION -> {
        automa.setConservation(automa.getConservation() + 1);
        yield "Gained **1 🌿 conservation point**";
      }

      case SPONSOR -> {
        automa.setMoney(automa.getMoney() + strength);
        automa.setAppeal(automa.getAppeal() + 1);
        yield "Gained **" + strength + " 💰** and **1 ⭐ appeal**";
      }
    };
  }
}
