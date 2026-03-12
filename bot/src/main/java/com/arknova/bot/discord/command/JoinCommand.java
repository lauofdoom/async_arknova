package com.arknova.bot.discord.command;

import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova join — join the game in the current channel.
 */
@Component
@RequiredArgsConstructor
public class JoinCommand implements ArkNovaCommand {

  private final GameService gameService;

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
    CommandHelper.runSafely(event, () -> {
      String channelId = event.getChannel().getId();
      String discordId = event.getUser().getId();
      String name      = event.getMember() != null
          ? event.getMember().getEffectiveName()
          : event.getUser().getName();

      PlayerState player = gameService.joinGame(channelId, discordId, name);

      EmbedBuilder embed = new EmbedBuilder()
          .setColor(CommandHelper.COLOR_SUCCESS)
          .setTitle("Joined!")
          .setDescription(name + " has joined the game.")
          .addField("Seat", String.valueOf(player.getSeatIndex() + 1), true);

      event.getHook().sendMessageEmbeds(embed.build()).queue();
    });
  }
}
