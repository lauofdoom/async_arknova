package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordChannelService;
import com.arknova.bot.discord.DiscordLogger;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova endturn — end the current player's turn and advance to the next player.
 *
 * <p>Only the player whose seat matches {@code game.getCurrentSeat()} may call this. On success,
 * the game advances to the next seat (and increments the turn number when the seat wraps back to
 * 0). The ended player's board is auto-posted to the #board channel.
 */
@Component
@RequiredArgsConstructor
public class EndTurnCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final CommandHelper commandHelper;
  private final DiscordLogger discordLogger;
  private final DiscordChannelService channelService;

  @Override
  public String getSubcommandName() {
    return "endturn";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("endturn", "End the active player's turn and pass to the next player")
        .addOption(
            OptionType.USER,
            "player",
            "Player whose turn to end (default: active player; specify to force-advance any player)",
            false);
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          String channelId = event.getChannel().getId();

          Optional<Game> maybeGame = commandHelper.getActiveGame(event);
          if (maybeGame.isEmpty()) return;
          Game gameBefore = maybeGame.get();

          // Resolve which player's turn to end
          OptionMapping playerOpt = event.getOption("player");
          String endingDiscordId;
          boolean forceAdvance;

          if (playerOpt != null) {
            // Explicit player specified
            endingDiscordId = playerOpt.getAsUser().getId();
            Optional<PlayerState> maybeTarget =
                gameService.getPlayerState(gameBefore.getId(), endingDiscordId);
            if (maybeTarget.isEmpty()) {
              CommandHelper.replyError(event, playerOpt.getAsUser().getName() + " is not a participant in this game.");
              return;
            }
            forceAdvance = maybeTarget.get().getSeatIndex() != gameBefore.getCurrentSeat();
          } else {
            // Default: end the currently active player's turn
            Optional<PlayerState> maybeActive = gameService.getCurrentPlayer(gameBefore);
            if (maybeActive.isEmpty()) {
              CommandHelper.replyError(event, "Could not determine the active player.");
              return;
            }
            endingDiscordId = maybeActive.get().getDiscordId();
            forceAdvance = false;
          }

          PlayerState endedPlayer =
              gameService.getPlayerState(gameBefore.getId(), endingDiscordId)
                  .orElseThrow(() -> new IllegalStateException("Player not found."));

          // Advance the turn
          Game updatedGame = forceAdvance
              ? gameService.forceEndTurn(channelId, endingDiscordId)
              : gameService.endTurn(channelId, endingDiscordId);

          List<PlayerState> allPlayers = gameService.getPlayersInOrder(updatedGame.getId());
          PlayerState nextPlayer =
              allPlayers.stream()
                  .filter(p -> p.getSeatIndex() == updatedGame.getCurrentSeat())
                  .findFirst()
                  .orElseThrow(
                      () -> new IllegalStateException("Could not determine next player."));

          String description = forceAdvance
              ? "**" + endedPlayer.getDiscordName() + "**'s turn was force-advanced by <@" + event.getUser().getId() + ">."
              : "**" + endedPlayer.getDiscordName() + "** has ended their turn.";

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(forceAdvance ? CommandHelper.COLOR_NEUTRAL : CommandHelper.COLOR_SUCCESS)
                  .setTitle("Turn Ended")
                  .setDescription(description)
                  .addField("Next Player", nextPlayer.getDiscordName(), true)
                  .addField("Turn", String.valueOf(updatedGame.getTurnNumber()), true)
                  .setFooter("Use /arknova status to see the current board state");

          event.getHook().sendMessageEmbeds(embed.build()).queue();
          discordLogger.logTurnEnded(updatedGame, endedPlayer, nextPlayer);

          // Auto-post board image to #board channel asynchronously
          final Game gameRef = updatedGame;
          final PlayerState endedRef = endedPlayer;
          new Thread(
                  () -> channelService.postBoardUpdate(gameRef, endedRef),
                  "board-update-" + updatedGame.getId())
              .start();
        });
  }
}
