package com.arknova.bot.discord.command;

import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.DeckService;
import com.arknova.bot.service.GameService;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** /arknova status — shows current game state: turn, player resources, and action card strips. */
@Component
public class StatusCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final DeckService deckService;
  private final JDA jda;

  public StatusCommand(GameService gameService, DeckService deckService, @Lazy JDA jda) {
    this.gameService = gameService;
    this.deckService = deckService;
    this.jda = jda;
  }

  @Override
  public String getSubcommandName() {
    return "status";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("status", "Show the current game state");
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
          String channelId = event.getChannel().getId();

          Optional<Game> maybeGame = gameService.findByThreadId(channelId);
          if (maybeGame.isEmpty()) {
            CommandHelper.replyError(event, "No Ark Nova game found in this channel.");
            return;
          }
          Game game = maybeGame.get();

          List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
          Optional<PlayerState> currentPlayer = gameService.getCurrentPlayer(game);

          EmbedBuilder embed =
              new EmbedBuilder().setColor(statusColor(game)).setTitle("Ark Nova — Game Status");

          // Game-level info
          embed.addField("Status", game.getStatus().name(), true);
          embed.addField("Turn", String.valueOf(game.getTurnNumber()), true);
          currentPlayer.ifPresent(
              cp -> embed.addField("Current Player", cp.getDiscordName(), true));

          if (game.isActive() || game.getStatus() == Game.GameStatus.FINAL_SCORING) {
            int remaining = deckService.deckSize(game.getId());
            embed.addField("Deck Remaining", String.valueOf(remaining), true);
          }

          embed.addBlankField(false);

          // Per-player rows
          for (PlayerState p : players) {
            boolean isCurrent =
                currentPlayer.map(cp -> cp.getSeatIndex() == p.getSeatIndex()).orElse(false);
            String playerLabel =
                (isCurrent ? "▶ " : "")
                    + p.getDiscordName()
                    + " (seat "
                    + (p.getSeatIndex() + 1)
                    + ")";

            String resources =
                "💰 "
                    + p.getMoney()
                    + "  ⭐ "
                    + p.getAppeal()
                    + "  🌿 "
                    + p.getConservation()
                    + "  🏆 "
                    + p.getReputation();

            embed.addField(playerLabel, resources, false);

            if (game.isActive() || game.getStatus() == Game.GameStatus.FINAL_SCORING) {
              embed.addField("Action Cards", p.getActionCardOrder().toDiscordString(), false);
            }
          }

          event.getHook().sendMessageEmbeds(embed.build()).queue();

          // Also post to the calling player's private channel
          String callerId = event.getUser().getId();
          players.stream()
              .filter(p -> p.getDiscordId().equals(callerId))
              .findFirst()
              .ifPresent(caller -> {
                if (caller.getPrivateChannelId() != null) {
                  TextChannel privateChannel = jda.getTextChannelById(caller.getPrivateChannelId());
                  if (privateChannel != null) {
                    privateChannel.sendMessageEmbeds(embed.build()).queue(null, err -> {});
                  }
                }
              });
        });
  }

  private java.awt.Color statusColor(Game game) {
    return switch (game.getStatus()) {
      case SETUP -> CommandHelper.COLOR_INFO;
      case ACTIVE -> CommandHelper.COLOR_SUCCESS;
      case FINAL_SCORING -> CommandHelper.COLOR_NEUTRAL;
      case ENDED, ABANDONED -> CommandHelper.COLOR_FAILURE;
    };
  }
}
