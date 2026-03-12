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
 * /arknova build — build an enclosure or special building on your zoo board.
 *
 * <p>Options:
 * <ul>
 *   <li>{@code size}           — enclosure size (required)
 *   <li>{@code row}            — grid row 0-based (required)
 *   <li>{@code col}            — grid column 0-based (required)
 *   <li>{@code tags}           — optional terrain tags, comma-separated e.g. "WATER,ROCK"
 *   <li>{@code upgrade_action} — action card to upgrade (strength 4+ only)
 * </ul>
 *
 * <p>For upgraded multi-build, use separate `/arknova build` calls or a future button flow.
 */
@Component
@RequiredArgsConstructor
public class BuildCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final GameEngine gameEngine;
  private final CommandHelper commandHelper;

  @Override
  public String getSubcommandName() {
    return "build";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("build", "Build an enclosure or special building")
        .addOption(OptionType.INTEGER, "size",           "Enclosure size (1–5)",           true)
        .addOption(OptionType.INTEGER, "row",            "Grid row (0-based)",             true)
        .addOption(OptionType.INTEGER, "col",            "Grid column (0-based)",          true)
        .addOption(OptionType.STRING,  "tags",           "Terrain tags, e.g. WATER,ROCK",  false)
        .addOption(OptionType.STRING,  "upgrade_action", "Action card to upgrade (strength 4+)", false);
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

      int size = event.getOption("size",  OptionMapping::getAsInt);
      int row  = event.getOption("row",   OptionMapping::getAsInt);
      int col  = event.getOption("col",   OptionMapping::getAsInt);

      Map<String, Object> params = new HashMap<>();
      params.put("size", size);
      params.put("row",  row);
      params.put("col",  col);

      OptionMapping tagsOpt = event.getOption("tags");
      if (tagsOpt != null) {
        params.put("tags", CommandHelper.splitCsv(tagsOpt.getAsString()));
      }

      OptionMapping upgradeOpt = event.getOption("upgrade_action");
      if (upgradeOpt != null) {
        params.put("upgrade_action", upgradeOpt.getAsString().toUpperCase());
      }

      ActionRequest request = new ActionRequest(
          game.getId(), discordId, discordName, ActionCard.BUILD, params, null);

      ActionResult result = gameEngine.executeAction(request);

      Game updatedGame = gameService.findByThreadId(event.getChannel().getId()).orElse(game);
      List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
      commandHelper.sendActionResult(event, result, updatedGame, players);
    });
  }
}
