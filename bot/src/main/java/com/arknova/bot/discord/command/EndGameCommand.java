package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordLogger;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova endgame — ends the active game and posts a final scores summary.
 *
 * <p>Any active participant may call this command. On success, the game is transitioned to ENDED
 * and a public embed showing each player's final scores is posted to the channel.
 */
@Component
@RequiredArgsConstructor
public class EndGameCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final CommandHelper commandHelper;
  private final DiscordLogger discordLogger;

  @Override
  public String getSubcommandName() {
    return "endgame";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("endgame", "End the game and display final scores");
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          String channelId = event.getChannel().getId();
          String discordId = event.getUser().getId();

          Optional<Game> maybeGame = commandHelper.getActiveGame(event);
          if (maybeGame.isEmpty()) return;

          Game endedGame = gameService.endGame(channelId, discordId);
          List<PlayerState> players = gameService.getPlayersInOrder(endedGame.getId());

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_INFO)
                  .setTitle("Game Over — Final Scores")
                  .setDescription("The game has ended. Here are the final scores:");

          for (PlayerState player : players) {
            embed.addField(
                player.getDiscordName(),
                "Appeal: **"
                    + player.getAppeal()
                    + "** | Conservation: **"
                    + player.getConservation()
                    + "** | Reputation: **"
                    + player.getReputation()
                    + "**",
                false);
          }

          embed.setFooter("Turn " + endedGame.getTurnNumber() + " · Game complete");

          event.getHook().sendMessageEmbeds(embed.build()).queue();
          discordLogger.logGameEnded(endedGame, players);
        });
  }
}
