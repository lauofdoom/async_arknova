package com.arknova.bot.discord.command;

import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.DeckService;
import com.arknova.bot.service.GameService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova start — start the game once all players have joined.
 *
 * <p>Transitions the game to ACTIVE, initialises player starting resources, and deals the initial
 * card display.
 */
@Component
@RequiredArgsConstructor
public class StartCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final DeckService deckService;

  @Override
  public String getSubcommandName() {
    return "start";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("start", "Start the game (requires 2–4 players)");
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          String channelId = event.getChannel().getId();
          String discordId = event.getUser().getId();

          Game game = gameService.startGame(channelId, discordId);

          List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
          List<String> playerIds = players.stream().map(PlayerState::getDiscordId).toList();

          deckService.initializeDecks(game, playerIds);

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_SUCCESS)
                  .setTitle("Ark Nova — Game Started!")
                  .setDescription(
                      "The game is underway. Turn order and starting resources are set.");

          StringBuilder turnOrder = new StringBuilder();
          for (PlayerState p : players) {
            turnOrder
                .append("**")
                .append(p.getSeatIndex() + 1)
                .append(".** ")
                .append(p.getDiscordName())
                .append(" — ")
                .append(p.getMoney())
                .append(" 💰\n");
          }
          embed.addField("Turn Order & Starting Money", turnOrder.toString().trim(), false);

          PlayerState first = players.get(0);
          embed
              .addField("First Player", first.getDiscordName(), true)
              .addField("Turn", "1", true)
              .setFooter("Use /arknova status to see the full board state");

          event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
  }
}
