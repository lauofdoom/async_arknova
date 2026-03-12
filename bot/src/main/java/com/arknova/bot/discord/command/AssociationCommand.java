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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova association — perform association board tasks.
 *
 * <p>Options:
 * <ul>
 *   <li>{@code sub_actions}     — comma-separated task types (required):
 *                                 PARTNER_ZOO | UNIVERSITY | CONSERVATION_PROJECT | RETURN_WORKERS
 *   <li>{@code project_ids}     — comma-separated conservation project card IDs
 *                                 (one per CONSERVATION_PROJECT entry)
 *   <li>{@code donation_amount} — money to donate (upgraded only; must be a multiple of 3)
 * </ul>
 *
 * <p>Example: {@code sub_actions:PARTNER_ZOO,CONSERVATION_PROJECT project_ids:proj-42}
 */
@Component
@RequiredArgsConstructor
public class AssociationCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final GameEngine gameEngine;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "association";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("association", "Perform association board tasks")
        .addOption(OptionType.STRING,  "sub_actions",
            "Tasks: PARTNER_ZOO, UNIVERSITY, CONSERVATION_PROJECT, RETURN_WORKERS", true)
        .addOption(OptionType.STRING,  "project_ids",
            "Conservation project card IDs (one per CONSERVATION_PROJECT, comma-separated)", false)
        .addOption(OptionType.INTEGER, "donation_amount",
            "Money to donate (upgraded only; multiple of 3)", false);
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

      List<String> subActions = CommandHelper.splitCsv(
          event.getOption("sub_actions", OptionMapping::getAsString));
      // Normalise to uppercase
      params.put("sub_actions",
          subActions.stream().map(String::toUpperCase).collect(Collectors.toList()));

      OptionMapping projectOpt = event.getOption("project_ids");
      if (projectOpt != null) {
        params.put("project_ids", CommandHelper.splitCsv(projectOpt.getAsString()));
      }

      OptionMapping donationOpt = event.getOption("donation_amount");
      if (donationOpt != null) {
        params.put("donation_amount", donationOpt.getAsInt());
      }

      ActionRequest request = new ActionRequest(
          game.getId(), discordId, discordName, ActionCard.ASSOCIATION, params, null);

      ActionResult result = gameEngine.executeAction(request);

      Game updatedGame = gameService.findByThreadId(event.getChannel().getId()).orElse(game);
      List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
      commandHelper.sendActionResult(event, result, updatedGame, players);
    });
  }
}
