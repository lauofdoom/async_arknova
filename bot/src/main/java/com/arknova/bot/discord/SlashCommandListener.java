package com.arknova.bot.discord;

import com.arknova.bot.discord.command.ArkNovaCommand;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Receives all slash command interactions and routes them to the appropriate {@link ArkNovaCommand}
 * handler. All commands share the "/arknova" top-level name and are dispatched by subcommand.
 */
@Component
public class SlashCommandListener extends ListenerAdapter {

  private static final Logger log = LoggerFactory.getLogger(SlashCommandListener.class);

  /** Maps subcommand name → handler, e.g. "ping" → PingCommand */
  private final Map<String, ArkNovaCommand> commandsBySubcommand;

  public SlashCommandListener(List<ArkNovaCommand> commands) {
    this.commandsBySubcommand =
        commands.stream()
            .collect(Collectors.toMap(ArkNovaCommand::getSubcommandName, Function.identity()));
    log.info(
        "Registered {} slash command handlers: {}",
        commands.size(),
        commandsBySubcommand.keySet());
  }

  /**
   * Builds the single {@code /arknova} parent command with all subcommands attached.
   * Called by JdaConfig to register commands with Discord.
   */
  public List<CommandData> getAllCommandData() {
    var parent = Commands.slash("arknova", "Ark Nova async game commands");
    commandsBySubcommand.values().stream()
        .map(ArkNovaCommand::getSubcommandData)
        .forEach(parent::addSubcommands);
    return List.of(parent);
  }

  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    if (!event.getName().equals("arknova")) return;

    String subcommand = event.getSubcommandName();
    if (subcommand == null) {
      event.reply("Please use a subcommand, e.g. `/arknova status`").setEphemeral(true).queue();
      return;
    }

    ArkNovaCommand handler = commandsBySubcommand.get(subcommand);
    if (handler == null) {
      log.warn("No handler for subcommand: {}", subcommand);
      event.reply("Unknown command: `" + subcommand + "`").setEphemeral(true).queue();
      return;
    }

    // Defer immediately to avoid Discord's 3-second timeout while we do DB work
    boolean ephemeral = handler.isEphemeral();
    event.deferReply(ephemeral).queue();

    try {
      handler.handle(event);
    } catch (Exception e) {
      log.error("Error handling /{} {}", event.getName(), subcommand, e);
      event
          .getHook()
          .sendMessage("An error occurred processing your command. Please try again.")
          .setEphemeral(true)
          .queue();
    }
  }
}
