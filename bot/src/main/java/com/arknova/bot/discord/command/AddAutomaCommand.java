package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordLogger;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova addautoma — adds a single-player automa opponent to the game lobby.
 *
 * <p>The automa fills one player seat and takes its turns automatically whenever the seat
 * advances to it. Useful for solo testing and single-player practice games.
 *
 * <p>Can only be called while the game is in SETUP (before /arknova start). Only one automa
 * per game is permitted.
 */
@Component
@RequiredArgsConstructor
public class AddAutomaCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final DiscordLogger discordLogger;

  @Override
  public String getSubcommandName() {
    return "addautoma";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData(
        "addautoma",
        "Add an automa (AI opponent) to the game — useful for solo testing and single-player play");
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          String channelId = event.getChannel().getId();
          String discordId = event.getUser().getId();

          PlayerState automa = gameService.addAutoma(channelId, discordId);

          Optional<Game> maybeGame = gameService.findByThreadId(channelId);
          int totalPlayers =
              maybeGame
                  .map(g -> gameService.getPlayersInOrder(g.getId()).size())
                  .orElse(0);

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_SUCCESS)
                  .setTitle("Automa Added")
                  .setDescription(
                      "**Automa 🤖** has been added as player "
                          + (automa.getSeatIndex() + 1)
                          + ".\n\n"
                          + "The automa will take its turns automatically, always playing its "
                          + "strongest action card and applying a simplified gain.")
                  .addField("Seat", String.valueOf(automa.getSeatIndex() + 1), true)
                  .addField("Total Players", String.valueOf(totalPlayers), true)
                  .addField(
                      "Automa Strategy",
                      "Plays highest-strength card each turn:\n"
                          + "🃏 Cards → advances break track\n"
                          + "🏗️ Build → gains strength × 2 💰\n"
                          + "🦁 Animals → gains strength ⭐\n"
                          + "🤝 Association → gains 1 🌿\n"
                          + "💼 Sponsor → gains strength 💰 + 1 ⭐",
                      false)
                  .setFooter("Use /arknova start when ready");

          event.getHook().sendMessageEmbeds(embed.build()).queue();

          maybeGame.ifPresent(g -> discordLogger.logPlayerJoined(g, "Automa 🤖", automa.getSeatIndex() + 1));
        });
  }
}
