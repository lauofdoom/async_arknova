package com.arknova.bot.discord.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * Contract for all /arknova subcommand handlers.
 *
 * <p>Each implementation handles one subcommand (e.g. "ping", "create", "status"). The
 * SlashCommandListener collects all implementations and builds a single {@code /arknova} parent
 * command containing them all.
 *
 * <p>Note: {@link #handle} is called AFTER the interaction has been deferred, so always use {@code
 * event.getHook()} to send responses, never {@code event.reply()}.
 */
public interface ArkNovaCommand {

  /** The subcommand name, e.g. "ping", "create", "status" */
  String getSubcommandName();

  /** Subcommand definition (options, description) for Discord command registration. */
  SubcommandData getSubcommandData();

  /**
   * Whether responses from this command should be ephemeral (only visible to the invoking user).
   * Default: false (public response in the game thread).
   */
  default boolean isEphemeral() {
    return false;
  }

  /**
   * Handle the interaction. The interaction has already been deferred — use {@code event.getHook()}
   * to send the response.
   */
  void handle(SlashCommandInteractionEvent event);
}
