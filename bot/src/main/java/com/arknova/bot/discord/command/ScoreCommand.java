package com.arknova.bot.discord.command;

import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova score — manually set or adjust any player track.
 *
 * <p>Options:
 *
 * <ul>
 *   <li>{@code track} — which track to adjust: appeal, conservation, reputation, break, xtokens
 *   <li>{@code value} — set absolute value (e.g. "25"), add (+5), or remove (-3)
 *   <li>{@code player} — target player (defaults to the caller)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ScoreCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "score";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData(
            "score", "Set or adjust a player's track (appeal, conservation, etc.)")
        .addOption(
            OptionType.STRING,
            "track",
            "Track to adjust: appeal, conservation, reputation, break, xtokens",
            true)
        .addOption(
            OptionType.STRING, "value", "Set value (e.g. 25), add (+5), or subtract (-3)", true)
        .addOption(OptionType.USER, "player", "Player to adjust (default: you)", false);
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          Optional<Game> maybeGame = commandHelper.getActiveGame(event);
          if (maybeGame.isEmpty()) return;
          Game game = maybeGame.get();

          // Resolve target player
          OptionMapping playerOpt = event.getOption("player");
          String targetDiscordId;
          String targetName;
          if (playerOpt != null) {
            User targetUser = playerOpt.getAsUser();
            targetDiscordId = targetUser.getId();
            targetName = targetUser.getName();
          } else {
            targetDiscordId = event.getUser().getId();
            targetName = event.getUser().getName();
          }

          String track = event.getOption("track", OptionMapping::getAsString).trim().toLowerCase();
          String valueStr = event.getOption("value", OptionMapping::getAsString).trim();

          // Parse value — plain int = set, +N = delta add, -N = delta subtract
          Integer setValue = null;
          Integer deltaValue = null;
          try {
            if (valueStr.startsWith("+")) {
              deltaValue = Integer.parseInt(valueStr.substring(1));
            } else if (valueStr.startsWith("-")) {
              deltaValue = Integer.parseInt(valueStr); // negative
            } else {
              setValue = Integer.parseInt(valueStr);
              if (setValue < 0) {
                throw new IllegalStateException("Track value cannot be set to a negative number.");
              }
            }
          } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Invalid value \""
                    + valueStr
                    + "\". Use a plain integer (e.g. 25), +N to add, or -N to subtract.");
          }

          // Break track is shared — handle separately without a target player.
          if ("break".equals(track)) {
            int oldValue = gameService.getSharedBreakTrack(game.getId());
            gameService.adjustTrack(game.getId(), targetDiscordId, track, setValue, deltaValue);
            int newValue = gameService.getSharedBreakTrack(game.getId());
            EmbedBuilder embed =
                new EmbedBuilder()
                    .setColor(CommandHelper.COLOR_SUCCESS)
                    .setTitle("Track Updated")
                    .setDescription("Shared **break** track updated.")
                    .addField("Break", oldValue + " → **" + newValue + "**", true);
            event.getHook().sendMessageEmbeds(embed.build()).queue();
            return;
          }

          // Read old value for display
          PlayerState before =
              gameService
                  .getPlayerState(game.getId(), targetDiscordId)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Player <@" + targetDiscordId + "> is not in this game."));
          int oldValue = trackValue(before, track);

          PlayerState updated =
              gameService.adjustTrack(game.getId(), targetDiscordId, track, setValue, deltaValue);
          int newValue = trackValue(updated, track);

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_SUCCESS)
                  .setTitle("Track Updated")
                  .setDescription("**" + targetName + "**'s **" + track + "** track updated.")
                  .addField(capitalize(track), oldValue + " → **" + newValue + "**", true);

          event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
  }

  private int trackValue(PlayerState player, String track) {
    return switch (track.toLowerCase()) {
      case "appeal" -> player.getAppeal();
      case "conservation" -> player.getConservation();
      case "reputation" -> player.getReputation();
      case "xtokens" -> player.getXTokens();
      default -> 0;
    };
  }

  private String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
