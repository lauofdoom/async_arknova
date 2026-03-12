package com.arknova.bot.discord.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.time.Instant;

/**
 * /arknova ping — health check command. Verifies the bot is online and
 * confirms end-to-end Discord interaction is working.
 */
@Component
public class PingCommand implements ArkNovaCommand {

  @Override
  public String getSubcommandName() {
    return "ping";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("ping", "Check if Ark Nova bot is online");
  }

  @Override
  public boolean isEphemeral() {
    return true;
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    long gatewayPing = event.getJDA().getGatewayPing();

    EmbedBuilder embed =
        new EmbedBuilder()
            .setTitle("Ark Nova Bot — Online")
            .setDescription("The bot is running and connected to Discord.")
            .addField("Gateway Ping", gatewayPing + "ms", true)
            .addField("Status", "Ready", true)
            .setColor(Color.GREEN)
            .setTimestamp(Instant.now());

    event.getHook().sendMessageEmbeds(embed.build()).queue();
  }
}
