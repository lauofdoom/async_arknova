package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordChannelService;
import com.arknova.bot.discord.DiscordLogger;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova create — creates a new game from the current channel (lobby).
 *
 * <p>A dedicated game channel (ARK-N category + #board + creator's #name-cards) is created
 * asynchronously after the slash command reply. Other players join from #board.
 */
@Component
@RequiredArgsConstructor
public class CreateCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final DiscordChannelService channelService;
  private final DiscordLogger discordLogger;

  @Override
  public String getSubcommandName() {
    return "create";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("create", "Create a new Ark Nova game in this channel");
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          String channelId = event.getChannel().getId();
          String guildId = event.getGuild() != null ? event.getGuild().getId() : "DM";
          String discordId = event.getUser().getId();
          String name =
              event.getMember() != null
                  ? event.getMember().getEffectiveName()
                  : event.getUser().getName();

          Game game = gameService.createGame(guildId, channelId, discordId, name);
          List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
          PlayerState creator = players.get(0);

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_SUCCESS)
                  .setTitle("Ark Nova — Game Created")
                  .setDescription(
                      "A dedicated channel (ARK-"
                          + game.getGameNumber()
                          + ") is being set up. Navigate to **#board** once it appears to join.")
                  .addField("Host", name, true)
                  .addField("Join", "From **#board**: `/arknova join`", false)
                  .addField("Start", "Once 2–4 players have joined: `/arknova start`", false);

          event.getHook().sendMessageEmbeds(embed.build()).queue();
          discordLogger.logGameCreated(game, name);

          // Create Discord channels asynchronously — does not block the command response
          final Game createdGame = game;
          final PlayerState creatorState = creator;
          new Thread(
                  () -> channelService.setupNewGame(createdGame, creatorState),
                  "channel-setup-" + game.getId())
              .start();
        });
  }
}
