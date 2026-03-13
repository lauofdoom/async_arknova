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
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova animals — place an animal card from your hand into an enclosure.
 *
 * <p>Options:
 *
 * <ul>
 *   <li>{@code card_id} — ID of the animal card to place (required)
 *   <li>{@code enclosure_id}— enclosure reference, e.g. E1 (required)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AnimalsCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final GameEngine gameEngine;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "animals";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("animals", "Place an animal card into an enclosure")
        .addOption(OptionType.STRING, "card_id", "Animal card ID", true)
        .addOption(OptionType.STRING, "enclosure_id", "Enclosure reference, e.g. E1", true);
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
          String discordName =
              event.getMember() != null
                  ? event.getMember().getEffectiveName()
                  : event.getUser().getName();

          String cardId = event.getOption("card_id").getAsString();
          String enclosureId = event.getOption("enclosure_id").getAsString();

          Map<String, Object> params = new HashMap<>();
          params.put("hand_card_ids", List.of(cardId));
          params.put("hand_enc_ids", List.of(enclosureId));

          ActionRequest request =
              new ActionRequest(
                  game.getId(), discordId, discordName, ActionCard.ANIMALS, params, null);

          ActionResult result = gameEngine.executeAction(request);

          Game updatedGame = gameService.findByThreadId(event.getChannel().getId()).orElse(game);
          List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
          commandHelper.sendActionResult(event, result, updatedGame, players);
        });
  }
}
