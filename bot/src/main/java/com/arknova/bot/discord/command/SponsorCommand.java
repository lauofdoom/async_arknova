package com.arknova.bot.discord.command;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.GameEngine;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova sponsor — play sponsor cards from your hand or take the break.
 *
 * <p>Options:
 * <ul>
 *   <li>{@code card_ids}         — comma-separated sponsor card IDs from your hand to play
 *   <li>{@code break}            — set to true to take the break instead (gain X or 2X money)
 *   <li>{@code display_card_ids} — comma-separated card IDs from the display (upgraded only)
 * </ul>
 *
 * <p>Exactly one of {@code card_ids}/{@code display_card_ids} or {@code break} must be provided.
 */
@Component
@RequiredArgsConstructor
public class SponsorCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final GameEngine gameEngine;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "sponsor";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("sponsor", "Play sponsor cards or take the break")
        .addOption(OptionType.STRING,  "card_ids",
            "Sponsor card IDs from your hand (comma-separated)", false)
        .addOption(OptionType.BOOLEAN, "break",
            "Take the break instead of playing cards (gain X or 2X money)", false)
        .addOption(OptionType.STRING,  "display_card_ids",
            "Sponsor card IDs from the display (upgraded only, comma-separated)", false);
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

      OptionMapping cardIdsOpt = event.getOption("card_ids");
      if (cardIdsOpt != null) {
        params.put("card_ids", CommandHelper.splitCsv(cardIdsOpt.getAsString()));
      }

      OptionMapping breakOpt = event.getOption("break");
      if (breakOpt != null && breakOpt.getAsBoolean()) {
        params.put("break", true);
      }

      OptionMapping displayOpt = event.getOption("display_card_ids");
      if (displayOpt != null) {
        params.put("display_card_ids", CommandHelper.splitCsv(displayOpt.getAsString()));
      }

      ActionRequest request = new ActionRequest(
          game.getId(), discordId, discordName, ActionCard.SPONSOR, params, null);

      ActionResult result = gameEngine.executeAction(request);

      Game updatedGame = gameService.findByThreadId(event.getChannel().getId()).orElse(game);
      List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
      commandHelper.sendActionResult(event, result, updatedGame, players);
    });
  }
}
