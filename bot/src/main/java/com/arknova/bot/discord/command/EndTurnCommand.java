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
 * /arknova endturn — end the current player's turn and advance to the next player.
 *
 * <p>Only the player whose seat matches {@code game.getCurrentSeat()} may call this. On success,
 * the game advances to the next seat (and increments the turn number when the seat wraps back to
 * 0).
 */
@Component
@RequiredArgsConstructor
public class EndTurnCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final CommandHelper commandHelper;
  private final DiscordLogger discordLogger;

  @Override
  public String getSubcommandName() {
    return "endturn";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("endturn", "End your turn and pass to the next player");
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          String channelId = event.getChannel().getId();
          String discordId = event.getUser().getId();

          // Snapshot who was current before advancing
          Optional<Game> maybeGame = commandHelper.getActiveGame(event);
          if (maybeGame.isEmpty()) return;
          Game gameBefore = maybeGame.get();

          PlayerState endedPlayer =
              gameService
                  .getPlayerState(gameBefore.getId(), discordId)
                  .orElseThrow(
                      () -> new IllegalStateException("You are not a participant in this game."));

          // Advance the turn (validates it's the caller's turn)
          Game updatedGame = gameService.endTurn(channelId, discordId);

          List<PlayerState> allPlayers = gameService.getPlayersInOrder(updatedGame.getId());
          PlayerState nextPlayer =
              allPlayers.stream()
                  .filter(p -> p.getSeatIndex() == updatedGame.getCurrentSeat())
                  .findFirst()
                  .orElseThrow(
                      () -> new IllegalStateException("Could not determine next player."));

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_SUCCESS)
                  .setTitle("Turn Ended")
                  .setDescription(
                      "**"
                          + endedPlayer.getDiscordName()
                          + "** has ended their turn.")
                  .addField("Next Player", nextPlayer.getDiscordName(), true)
                  .addField("Turn", String.valueOf(updatedGame.getTurnNumber()), true)
                  .setFooter("Use /arknova status to see the current board state");

          event.getHook().sendMessageEmbeds(embed.build()).queue();
          discordLogger.logTurnEnded(updatedGame, endedPlayer, nextPlayer);
        });
  }
}
