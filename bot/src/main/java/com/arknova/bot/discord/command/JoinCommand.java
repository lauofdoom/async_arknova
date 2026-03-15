package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordChannelService;
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

/** /arknova join — join the game in the current channel (#board). */
@Component
@RequiredArgsConstructor
public class JoinCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final DiscordChannelService channelService;
  private final DiscordLogger discordLogger;

  @Override
  public String getSubcommandName() {
    return "join";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("join", "Join the Ark Nova game in this channel");
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          String channelId = event.getChannel().getId();
          String discordId = event.getUser().getId();
          String name =
              event.getMember() != null
                  ? event.getMember().getEffectiveName()
                  : event.getUser().getName();

          PlayerState player = gameService.joinGame(channelId, discordId, name);
          Optional<Game> maybeGame = gameService.findByThreadId(channelId);

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_SUCCESS)
                  .setTitle("Joined!")
                  .setDescription(
                      name + " has joined the game. Your private cards channel is being created.")
                  .addField("Seat", String.valueOf(player.getSeatIndex() + 1), true);

          event.getHook().sendMessageEmbeds(embed.build()).queue();
          maybeGame.ifPresent(
              g -> discordLogger.logPlayerJoined(g, name, player.getSeatIndex() + 1));

          // Create the joining player's private #name-cards channel asynchronously
          maybeGame.ifPresent(
              g -> {
                final Game game = g;
                final PlayerState joinedPlayer = player;
                new Thread(
                        () -> channelService.createPlayerChannel(game, joinedPlayer),
                        "player-channel-" + player.getDiscordId())
                    .start();
              });
        });
  }
}
