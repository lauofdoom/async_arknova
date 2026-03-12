package com.arknova.bot.discord.command;

import com.arknova.bot.model.Game;
import com.arknova.bot.service.GameService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova create — creates a new game in the current channel/thread.
 *
 * <p>The game is permanently associated with this channel ID. Only one game per channel.
 */
@Component
@RequiredArgsConstructor
public class CreateCommand implements ArkNovaCommand {

  private final GameService gameService;

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

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_SUCCESS)
                  .setTitle("Ark Nova — Game Created")
                  .setDescription("Game created. Waiting for players to join.")
                  .addField("Game ID", game.getId().toString().substring(0, 8) + "...", true)
                  .addField("Host", name, true)
                  .addField("Join", "Other players: `/arknova join`", false)
                  .addField("Start", "Once 2–4 players have joined: `/arknova start`", false);

          event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
  }
}
