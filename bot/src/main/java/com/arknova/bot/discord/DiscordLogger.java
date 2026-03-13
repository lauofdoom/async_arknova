package com.arknova.bot.discord;

import com.arknova.bot.config.ArkNovaProperties;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import java.awt.Color;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Sends structured game-event embeds to a dedicated Discord log channel.
 *
 * <p>The target channel is configured via {@code arknova.discord.log-channel-id}. If the property
 * is absent or blank, all log calls are silently skipped — nothing breaks in single-channel setups.
 *
 * <p>JDA is injected lazily to avoid a circular bean dependency: JDA → SlashCommandListener →
 * CommandHelper → DiscordLogger → JDA.
 */
@Service
public class DiscordLogger {

  private static final Logger log = LoggerFactory.getLogger(DiscordLogger.class);

  private static final Color COLOR_SUCCESS = new Color(0x2ECC71);
  private static final Color COLOR_NEUTRAL = new Color(0x95A5A6);
  private static final Color COLOR_INFO = new Color(0x3498DB);

  private final JDA jda;
  private final String logChannelId;

  public DiscordLogger(@Lazy JDA jda, ArkNovaProperties props) {
    this.jda = jda;
    this.logChannelId = props.discord().logChannelId();
  }

  // ── Public API ────────────────────────────────────────────────────────────

  public void logGameCreated(Game game, String creatorName) {
    if (!isConfigured()) return;
    send(
        new EmbedBuilder()
            .setColor(COLOR_INFO)
            .setTitle("🎮 New Game Created")
            .setDescription(
                "**"
                    + creatorName
                    + "** created a game.\n"
                    + threadLink(game))
            .addField("Game", shortId(game), true));
  }

  public void logPlayerJoined(Game game, String playerName, int seatNumber) {
    if (!isConfigured()) return;
    send(
        new EmbedBuilder()
            .setColor(COLOR_INFO)
            .setTitle("👤 Player Joined")
            .setDescription(
                "**"
                    + playerName
                    + "** joined as seat "
                    + seatNumber
                    + ".\n"
                    + threadLink(game))
            .addField("Game", shortId(game), true));
  }

  public void logGameStarted(Game game, List<PlayerState> playersInOrder) {
    if (!isConfigured()) return;
    StringBuilder order = new StringBuilder();
    for (PlayerState p : playersInOrder) {
      order
          .append("**")
          .append(p.getSeatIndex() + 1)
          .append(".** ")
          .append(p.getDiscordName())
          .append("\n");
    }
    send(
        new EmbedBuilder()
            .setColor(COLOR_SUCCESS)
            .setTitle("🦒 Game Started!")
            .setDescription(threadLink(game))
            .addField("Turn Order", order.toString().trim(), false)
            .setFooter("First up: " + playersInOrder.get(0).getDiscordName()));
  }

  public void logTurnEnded(Game game, PlayerState ended, PlayerState next) {
    if (!isConfigured()) return;
    send(
        new EmbedBuilder()
            .setColor(COLOR_NEUTRAL)
            .setTitle("⏭️ Turn Ended")
            .setDescription(
                "**"
                    + ended.getDiscordName()
                    + "** ended their turn.\n"
                    + threadLink(game))
            .addField("Next Up", next.getDiscordName(), true)
            .setFooter("Turn " + game.getTurnNumber()));
  }

  public void logGameEnded(Game game, List<PlayerState> players) {
    if (!isConfigured()) return;
    StringBuilder scores = new StringBuilder();
    for (PlayerState p : players) {
      scores
          .append("**")
          .append(p.getSeatIndex() + 1)
          .append(".** ")
          .append(p.getDiscordName())
          .append(" — Appeal: **")
          .append(p.getAppeal())
          .append("** | Conservation: **")
          .append(p.getConservation())
          .append("**\n");
    }
    send(
        new EmbedBuilder()
            .setColor(COLOR_INFO)
            .setTitle("🏁 Game Ended")
            .setDescription(threadLink(game))
            .addField("Final Scores", scores.toString().trim(), false)
            .addField("Game", shortId(game), true));
  }

  public void logAction(Game game, ActionResult result) {
    if (!isConfigured() || !result.success()) return;
    String title =
        result.cardUsed() != null
            ? result.cardUsed().displayName() + " · S" + result.strengthUsed()
            : "Action";
    send(
        new EmbedBuilder()
            .setColor(result.requiresManualResolution() ? COLOR_NEUTRAL : COLOR_SUCCESS)
            .setTitle(title)
            .setDescription(result.summary() + "\n" + threadLink(game))
            .setFooter("Turn " + game.getTurnNumber()));
  }

  // ── Internal ──────────────────────────────────────────────────────────────

  private void send(EmbedBuilder embed) {
    TextChannel channel = jda.getTextChannelById(logChannelId);
    if (channel == null) {
      log.warn(
          "Discord log channel '{}' not found — verify DISCORD_LOG_CHANNEL_ID and that the bot"
              + " has access",
          logChannelId);
      return;
    }
    channel.sendMessageEmbeds(embed.build()).queue(null, err -> log.warn("Log send failed", err));
  }

  private boolean isConfigured() {
    return logChannelId != null && !logChannelId.isBlank();
  }

  private static String threadLink(Game game) {
    return "[→ Jump to game](https://discord.com/channels/"
        + game.getGuildId()
        + "/"
        + game.getThreadId()
        + ")";
  }

  private static String shortId(Game game) {
    return "`" + game.getId().toString().substring(0, 8) + "…`";
  }
}
