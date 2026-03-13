package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordChannelService;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova refresh — posts the player's current hand, resources, and board PNG to their private
 * channel.
 *
 * <p>Replies ephemerally in the game thread confirming the refresh. If the player has no private
 * channel (e.g. old game or channel setup failed), replies with an error.
 */
@Component
@RequiredArgsConstructor
public class RefreshCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final CommandHelper commandHelper;
  private final DiscordChannelService channelService;

  @Override
  public String getSubcommandName() {
    return "refresh";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData(
        "refresh",
        "Refresh your private channel with current hand, resources, and board");
  }

  @Override
  public boolean isEphemeral() {
    return true;
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          Optional<Game> maybeGame = commandHelper.getGame(event);
          if (maybeGame.isEmpty()) return;
          Game game = maybeGame.get();

          String discordId = event.getUser().getId();
          Optional<PlayerState> maybePlayer = gameService.getPlayerState(game.getId(), discordId);
          if (maybePlayer.isEmpty()) {
            CommandHelper.replyError(event, "You are not a participant in this game.");
            return;
          }
          PlayerState player = maybePlayer.get();

          if (player.getPrivateChannelId() == null) {
            CommandHelper.replyError(
                event,
                "No private channel found for this game. Private channels are created when the"
                    + " game starts — this game may have started before the feature was enabled.");
            return;
          }

          channelService.refreshPrivateChannel(game, player);

          event
              .getHook()
              .sendMessage(
                  "Your private channel has been refreshed with your current hand, resources, and"
                      + " board.")
              .queue();
        });
  }
}
