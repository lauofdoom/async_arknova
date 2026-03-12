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
 * /arknova display — shows the current face-up card display (shared market).
 *
 * <p>Response is ephemeral. Lists cards by slot with names, IDs, and costs. Slot position
 * determines the display cost premium for SPONSOR/CARDS take actions.
 */
@Component
@RequiredArgsConstructor
public class DisplayCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final DeckService deckService;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "display";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("display", "View the face-up card display");
  }

  @Override
  public boolean isEphemeral() {
    return true;
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(event, () -> {
      Optional<Game> maybeGame = commandHelper.getGame(event);
      if (maybeGame.isEmpty()) return;
      Game game = maybeGame.get();

      List<PlayerCard> display = deckService.getDisplay(game.getId());
      int deckRemaining = deckService.deckSize(game.getId());

      EmbedBuilder embed = new EmbedBuilder()
          .setColor(CommandHelper.COLOR_INFO)
          .setTitle("Card Display (" + display.size() + " cards)")
          .setDescription(CommandHelper.formatCardList(display, true))
          .setFooter(deckRemaining + " cards remaining in deck");

      event.getHook().sendMessageEmbeds(embed.build()).queue();
    });
  }
}
