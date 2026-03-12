package com.arknova.bot.discord.command;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.GameEngine;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.DeckService;
import com.arknova.bot.service.GameService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova cards — draw cards, take the break, or use the snap ability.
 *
 * <p>Options:
 * <ul>
 *   <li>{@code action}          — DRAW | BREAK | SNAP (default: DRAW)
 *   <li>{@code discard_ids}     — comma-separated card IDs to discard from hand after drawing
 *   <li>{@code display_card_ids}— comma-separated card IDs to take from display (upgraded only)
 * </ul>
 *
 * <p>The engine enforces the correct draw count, required discards, and snap availability
 * based on the player's current card strength.
 */
@Component
@RequiredArgsConstructor
public class CardsCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final DeckService deckService;
  private final GameEngine gameEngine;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "cards";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("cards", "Draw cards, take a break, or use snap")
        .addOption(OptionType.STRING, "action",
            "DRAW (default) | BREAK (take 2 money) | SNAP (discard hand for new cards)", false)
        .addOption(OptionType.STRING, "discard_ids",
            "Card IDs to discard from hand (comma-separated)", false)
        .addOption(OptionType.STRING, "display_card_ids",
            "Card IDs to take from display (upgraded only, comma-separated)", false);
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(event, () -> {
      Optional<Game> maybeGame = commandHelper.getActiveGame(event);
      if (maybeGame.isEmpty()) return;
      Game game = maybeGame.get();

      String discordId   = event.getUser().getId();
      String discordName = event.getMember() != null
          ? event.getMember().getEffectiveName() : event.getUser().getName();

      Map<String, Object> params = new HashMap<>();

      OptionMapping actionOpt = event.getOption("action");
      if (actionOpt != null) {
        params.put("action", actionOpt.getAsString().toUpperCase());
      }

      OptionMapping discardOpt = event.getOption("discard_ids");
      if (discardOpt != null) {
        params.put("discard_ids", CommandHelper.splitCsv(discardOpt.getAsString()));
      }

      OptionMapping displayOpt = event.getOption("display_card_ids");
      if (displayOpt != null) {
        params.put("display_card_ids", CommandHelper.splitCsv(displayOpt.getAsString()));
      }

      ActionRequest request = new ActionRequest(
          game.getId(), discordId, discordName, ActionCard.CARDS, params, null);

      ActionResult result = gameEngine.executeAction(request);

      Game updatedGame = gameService.findByThreadId(event.getChannel().getId()).orElse(game);
      List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
      commandHelper.sendActionResult(event, result, updatedGame, players);

      // Privately reveal drawn cards to the acting player only
      if (result.success() && !result.drawnCardIds().isEmpty()) {
        List<PlayerCard> hand = deckService.getHand(game.getId(), discordId);
        List<PlayerCard> drawn = hand.stream()
            .filter(pc -> result.drawnCardIds().contains(pc.getCard().getId()))
            .toList();
        if (!drawn.isEmpty()) {
          EmbedBuilder reveal = new EmbedBuilder()
              .setColor(CommandHelper.COLOR_INFO)
              .setTitle("Cards you drew (" + drawn.size() + ")")
              .setDescription(CommandHelper.formatCardList(drawn, false))
              .setFooter("Only you can see this · use /arknova hand to see your full hand");
          event.getHook().sendMessageEmbeds(reveal.build()).setEphemeral(true).queue();
        }
      }
    });
  }
}
