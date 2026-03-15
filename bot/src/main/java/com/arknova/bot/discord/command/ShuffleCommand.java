package com.arknova.bot.discord.command;

import com.arknova.bot.model.Game;
import com.arknova.bot.service.DeckService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova shuffle — randomise the order of cards in a zone.
 *
 * <p>Options:
 *
 * <ul>
 *   <li>{@code target} — "DECK", "DISPLAY", or "DISCARD" (required)
 * </ul>
 *
 * <ul>
 *   <li>DECK — reshuffles both the animal and sponsor draw queues
 *   <li>DISPLAY — reassigns face-up display slot positions randomly
 *   <li>DISCARD — reassigns the calling player's personal discard pile order randomly
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ShuffleCommand implements ArkNovaCommand {

  private final CommandHelper commandHelper;
  private final DeckService deckService;

  @Override
  public String getSubcommandName() {
    return "shuffle";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData(
            "shuffle", "Shuffle a card zone — deck, display, or your discard pile")
        .addOption(OptionType.STRING, "target", "Zone to shuffle: DECK, DISPLAY, or DISCARD", true);
  }

  @Override
  public boolean isEphemeral() {
    return false;
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          Optional<Game> maybeGame = commandHelper.getActiveGame(event);
          if (maybeGame.isEmpty()) return;
          Game game = maybeGame.get();

          String discordId = event.getUser().getId();
          String target =
              event.getOption("target", OptionMapping::getAsString).trim().toUpperCase();

          switch (target) {
            case "DECK" -> {
              deckService.shuffleDeck(game.getId());
              event.getHook().sendMessage("\uD83C\uDCCF The draw deck has been shuffled.").queue();
            }
            case "DISPLAY" -> {
              deckService.shuffleDisplay(game.getId());
              event
                  .getHook()
                  .sendMessage(
                      "\uD83C\uDCCF The display has been shuffled — slot order randomised.")
                  .queue();
            }
            case "DISCARD" -> {
              deckService.shuffleDiscard(game.getId(), discordId);
              event
                  .getHook()
                  .sendMessage("\uD83C\uDCCF Your discard pile has been shuffled.")
                  .setEphemeral(true)
                  .queue();
            }
            default ->
                event
                    .getHook()
                    .sendMessage(
                        "❌ Invalid target \"" + target + "\". Use DECK, DISPLAY, or DISCARD.")
                    .setEphemeral(true)
                    .queue();
          }
        });
  }
}
