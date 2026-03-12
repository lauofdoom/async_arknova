package com.arknova.bot.discord.command;

import com.arknova.bot.engine.GameEngine;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.GameService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova discard — complete a pending hand discard after a CARDS draw action.
 *
 * <p>When a CARDS action at a strength that requires a hand discard (e.g. S1, S3, S5 unupgraded) is
 * executed, the engine defers the discard so the player can see their drawn cards first. This
 * command resolves the pending discard and advances the turn.
 *
 * <p>Options:
 *
 * <ul>
 *   <li>{@code card_ids} — comma-separated card IDs from the player's hand to discard (required)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DiscardCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final GameEngine gameEngine;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "discard";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("discard", "Complete a pending hand discard after drawing cards")
        .addOption(
            OptionType.STRING,
            "card_ids",
            "Comma-separated card IDs to discard from your hand",
            true);
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

          OptionMapping cardIdsOpt = event.getOption("card_ids");
          List<String> cardIds =
              cardIdsOpt != null ? CommandHelper.splitCsv(cardIdsOpt.getAsString()) : List.of();

          ActionResult result = gameEngine.executeDiscard(game.getId(), discordId, cardIds);

          Game updatedGame = gameService.findByThreadId(event.getChannel().getId()).orElse(game);
          List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
          commandHelper.sendActionResult(event, result, updatedGame, players);
        });
  }
}
