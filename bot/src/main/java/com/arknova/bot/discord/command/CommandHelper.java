package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordLogger;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared utilities for all slash command handlers: game lookup, error replies, and result
 * formatting.
 */
@Component
@RequiredArgsConstructor
public class CommandHelper {

  private static final Logger log = LoggerFactory.getLogger(CommandHelper.class);

  static final Color COLOR_SUCCESS = new Color(0x2ECC71);
  static final Color COLOR_FAILURE = new Color(0xE74C3C);
  static final Color COLOR_INFO = new Color(0x3498DB);
  static final Color COLOR_NEUTRAL = new Color(0x95A5A6);

  private final GameService gameService;
  private final DiscordLogger discordLogger;

  // ── Game & player lookup ──────────────────────────────────────────────────

  /**
   * Finds the game for the current channel/thread. Sends an ephemeral error and returns empty if
   * not found.
   */
  public Optional<Game> getGame(SlashCommandInteractionEvent event) {
    String channelId = event.getChannel().getId();
    Optional<Game> game = gameService.findByThreadId(channelId);
    if (game.isEmpty()) {
      event
          .getHook()
          .sendMessage(
              "No Ark Nova game found in this channel. Use `/arknova create` to start one.")
          .setEphemeral(true)
          .queue();
    }
    return game;
  }

  /** Returns the game only if ACTIVE; sends ephemeral error otherwise. */
  public Optional<Game> getActiveGame(SlashCommandInteractionEvent event) {
    return getGame(event)
        .filter(
            game -> {
              if (!game.isActive() && game.getStatus() != Game.GameStatus.FINAL_SCORING) {
                String status =
                    game.isSetup() ? "still in setup — use `/arknova start`" : "already ended";
                event
                    .getHook()
                    .sendMessage("This game is " + status + ".")
                    .setEphemeral(true)
                    .queue();
                return false;
              }
              return true;
            });
  }

  // ── Comma-split helpers ──────────────────────────────────────────────────

  /** Splits a nullable comma-separated option string into a trimmed list. */
  public static List<String> splitCsv(String value) {
    if (value == null || value.isBlank()) return List.of();
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  // ── Result formatting ─────────────────────────────────────────────────────

  /**
   * Formats and sends an {@link ActionResult} as a Discord embed. Shows the result summary, any
   * resource changes, and the next player's name.
   */
  public void sendActionResult(
      SlashCommandInteractionEvent event,
      ActionResult result,
      Game game,
      List<PlayerState> allPlayers) {

    EmbedBuilder embed = new EmbedBuilder();

    if (!result.success()) {
      embed
          .setColor(COLOR_FAILURE)
          .setTitle("❌ Action failed")
          .setDescription(result.errorMessage());
      event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
      return;
    }

    embed.setColor(result.requiresManualResolution() ? COLOR_NEUTRAL : COLOR_SUCCESS);

    String title =
        result.cardUsed() != null
            ? "**" + result.cardUsed().displayName() + "** (strength " + result.strengthUsed() + ")"
            : "Action";
    embed.setTitle(title);
    embed.setDescription(result.summary());

    // Resource deltas
    addDeltaField(embed, "💰 Spent", result.deltas(), "money_spent");
    addDeltaField(embed, "💰 Gained", result.deltas(), "money_gained");
    addDeltaField(embed, "🌿 CP", result.deltas(), "conservation_gained");
    addDeltaField(embed, "⭐ Appeal", result.deltas(), "appeal_gained");
    addDeltaField(embed, "🏆 Rep", result.deltas(), "reputation_gained");

    if (result.requiresManualResolution()) {
      embed.addField("⚠️ Manual", "This card's ability requires manual resolution.", false);
    }

    // Next turn footer
    int nextSeat = game.getCurrentSeat();
    allPlayers.stream()
        .filter(p -> p.getSeatIndex() == nextSeat)
        .findFirst()
        .ifPresent(
            next ->
                embed.setFooter(
                    "Turn " + game.getTurnNumber() + " · Next: " + next.getDiscordName()));

    event.getHook().sendMessageEmbeds(embed.build()).queue();
    discordLogger.logAction(game, result);
  }

  /** Sends a simple ephemeral error message. */
  public static void replyError(SlashCommandInteractionEvent event, String message) {
    event.getHook().sendMessage("❌ " + message).setEphemeral(true).queue();
  }

  /** Sends a simple ephemeral success message. */
  public static void replySuccess(SlashCommandInteractionEvent event, String message) {
    event
        .getHook()
        .sendMessageEmbeds(
            new EmbedBuilder().setColor(COLOR_SUCCESS).setDescription("✅ " + message).build())
        .queue();
  }

  /**
   * Wraps a command body in a try/catch that handles {@link IllegalStateException} (user-facing
   * rule violations) and unexpected exceptions gracefully.
   */
  public static void runSafely(SlashCommandInteractionEvent event, Runnable body) {
    try {
      body.run();
    } catch (IllegalStateException e) {
      replyError(event, e.getMessage());
    } catch (Exception e) {
      log.error("Unexpected error in command handler", e);
      replyError(event, "An unexpected error occurred. Please try again.");
    }
  }

  // ── Card formatting ───────────────────────────────────────────────────────

  /**
   * Formats a list of {@link PlayerCard}s into a compact embed-safe string. Each card is one line.
   *
   * <p>Format per card:
   *
   * <ul>
   *   <li>Animal: {@code **Name** · `id` · Cost: N · Size ≥ M · TAG1, TAG2 · Appeal: +A}
   *   <li>Sponsor: {@code **Name** · `id` · Level: N · [Rep: +R]}
   * </ul>
   *
   * @param cards ordered list of player cards (hand or display)
   * @param showSlot if true, prefix each line with "Slot N: " (for display cards)
   */
  public static String formatCardList(List<PlayerCard> cards, boolean showSlot) {
    if (cards.isEmpty()) return "_empty_";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cards.size(); i++) {
      if (i > 0) sb.append('\n');
      if (showSlot) sb.append("**Slot ").append(i + 1).append(":** ");
      sb.append(formatCardLine(cards.get(i).getCard()));
    }
    return sb.toString();
  }

  /** Formats a single {@link CardDefinition} into a compact one-line description. */
  public static String formatCardLine(CardDefinition c) {
    StringBuilder sb = new StringBuilder();
    sb.append("**").append(c.getName()).append("** · `").append(c.getId()).append("`");

    if (c.getCardType() == CardDefinition.CardType.ANIMAL) {
      sb.append(" · Cost: ").append(c.getBaseCost());
      if (c.getMinEnclosureSize() != null) {
        sb.append(" · Size ≥ ").append(c.getMinEnclosureSize());
      }
      List<String> tags = c.getTagList();
      if (!tags.isEmpty()) {
        sb.append(" · ").append(String.join(", ", tags));
      }
      if (c.getAppealValue() > 0) sb.append(" · Appeal: +").append(c.getAppealValue());
      if (c.getConservationValue() > 0) sb.append(" · CP: +").append(c.getConservationValue());
    } else if (c.getCardType() == CardDefinition.CardType.SPONSOR) {
      sb.append(" · Level: ").append(c.getBaseCost());
      if (c.getReputationValue() > 0) sb.append(" · Rep: +").append(c.getReputationValue());
      if (c.getAppealValue() > 0) sb.append(" · Appeal: +").append(c.getAppealValue());
    } else if (c.getCardType() == CardDefinition.CardType.CONSERVATION) {
      if (c.getConservationValue() > 0) sb.append(" · CP: +").append(c.getConservationValue());
    }

    // Append ability text on a new line, truncated to keep embed size manageable
    if (c.getAbilityText() != null && !c.getAbilityText().isBlank()) {
      String ability = c.getAbilityText().trim();
      if (ability.length() > 160) ability = ability.substring(0, 157) + "…";
      sb.append("\n  *").append(ability).append("*");
    }
    return sb.toString();
  }

  // ── Internal ─────────────────────────────────────────────────────────────

  private static void addDeltaField(
      EmbedBuilder embed, String label, java.util.Map<String, Object> deltas, String key) {
    Object val = deltas.get(key);
    if (val instanceof Number n && n.intValue() > 0) {
      embed.addField(label, String.valueOf(n.intValue()), true);
    }
  }
}
