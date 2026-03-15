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
 * /arknova adjust — manually adjust a player's resources (admin/correction use).
 *
 * <p>Options:
 *
 * <ul>
 *   <li>{@code money} — set money (e.g. "25"), add (+10), or remove (-5)
 *   <li>{@code partner_zoos} — number of partner zoo slots to add
 *   <li>{@code universities} — number of university slots to add
 *   <li>{@code player} — target player (defaults to the caller)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdjustCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "adjust";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("adjust", "Manually adjust a player's resources")
        .addOption(
            OptionType.STRING, "money", "Set money (e.g. 25), add (+10), or remove (-5)", false)
        .addOption(OptionType.INTEGER, "partner_zoos", "Number of partner zoos to add", false)
        .addOption(OptionType.INTEGER, "universities", "Number of universities to add", false)
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

          // Determine target player
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

          // Parse options
          OptionMapping moneyOpt = event.getOption("money");
          OptionMapping partnerZoosOpt = event.getOption("partner_zoos");
          OptionMapping universitiesOpt = event.getOption("universities");

          if (moneyOpt == null && partnerZoosOpt == null && universitiesOpt == null) {
            event
                .getHook()
                .sendMessage("Please specify at least one value to adjust.")
                .setEphemeral(true)
                .queue();
            return;
          }

          // Fetch the player's current state for old-value reporting
          PlayerState before =
              gameService
                  .getPlayerState(game.getId(), targetDiscordId)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Player <@" + targetDiscordId + "> is not in this game."));

          int oldMoney = before.getMoney();

          // Parse money option
          Integer moneyDelta = null;
          Integer moneySet = null;
          if (moneyOpt != null) {
            String moneyStr = moneyOpt.getAsString().trim();
            try {
              if (moneyStr.startsWith("+")) {
                moneyDelta = Integer.parseInt(moneyStr.substring(1));
              } else if (moneyStr.startsWith("-")) {
                moneyDelta = Integer.parseInt(moneyStr); // negative value
              } else {
                moneySet = Integer.parseInt(moneyStr);
                if (moneySet < 0) {
                  throw new IllegalStateException("Money cannot be set to a negative value.");
                }
              }
            } catch (NumberFormatException e) {
              throw new IllegalStateException(
                  "Invalid money value \""
                      + moneyStr
                      + "\". Use a plain integer (e.g. 25), +N to add, or -N to subtract.");
            }
          }

          Integer partnerZoosToAdd = partnerZoosOpt != null ? partnerZoosOpt.getAsInt() : null;
          Integer universitiesToAdd = universitiesOpt != null ? universitiesOpt.getAsInt() : null;

          // Apply adjustments
          PlayerState updated =
              gameService.adjustPlayer(
                  game.getId(),
                  targetDiscordId,
                  moneyDelta,
                  moneySet,
                  partnerZoosToAdd,
                  universitiesToAdd);

          // Build response embed
          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_SUCCESS)
                  .setTitle("Player Adjusted")
                  .setDescription("**" + targetName + "**'s resources have been updated.");

          if (moneyOpt != null) {
            embed.addField("Money", oldMoney + " → **" + updated.getMoney() + "**", true);
          }
          if (partnerZoosToAdd != null && partnerZoosToAdd > 0) {
            embed.addField("Partner Zoos Added", "+" + partnerZoosToAdd, true);
          }
          if (universitiesToAdd != null && universitiesToAdd > 0) {
            embed.addField("Universities Added", "+" + universitiesToAdd, true);
          }

          event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
  }
}
