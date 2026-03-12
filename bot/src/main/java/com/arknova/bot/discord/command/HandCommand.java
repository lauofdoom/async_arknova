package com.arknova.bot.discord.command;

import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.service.DeckService;
import com.arknova.bot.service.GameService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova hand — privately shows the invoking player's current hand.
 *
 * <p>Response is always ephemeral. Lists card names, IDs (for use in other commands), costs, and
 * key stats.
 */
@Component
@RequiredArgsConstructor
public class HandCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final DeckService deckService;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "hand";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("hand", "View your current hand (private)");
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
          List<PlayerCard> hand = deckService.getHand(game.getId(), discordId);

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_INFO)
                  .setTitle(
                      "Your Hand (" + hand.size() + " card" + (hand.size() == 1 ? "" : "s") + ")")
                  .setDescription(CommandHelper.formatCardList(hand, false));

          event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
  }
}
