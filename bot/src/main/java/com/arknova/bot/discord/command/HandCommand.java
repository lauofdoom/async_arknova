package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordChannelService;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.service.DeckService;
import com.arknova.bot.service.GameService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * /arknova hand — privately shows the invoking player's current hand.
 *
 * <p>Response is always ephemeral in the game thread. Additionally posts to the player's private
 * channel (if configured) for a persistent reference between sessions.
 */
@Component
public class HandCommand implements ArkNovaCommand {

  private final GameService gameService;
  private final DeckService deckService;
  private final CommandHelper commandHelper;
  private final DiscordChannelService channelService;
  private final net.dv8tion.jda.api.JDA jda;

  public HandCommand(
      GameService gameService,
      DeckService deckService,
      CommandHelper commandHelper,
      DiscordChannelService channelService,
      @Lazy net.dv8tion.jda.api.JDA jda) {
    this.gameService = gameService;
    this.deckService = deckService;
    this.commandHelper = commandHelper;
    this.channelService = channelService;
    this.jda = jda;
  }

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

          // Ephemeral reply in game thread
          event.getHook().sendMessageEmbeds(embed.build()).queue();

          // Also post to private channel if configured
          Optional<PlayerState> maybePlayer = gameService.getPlayerState(game.getId(), discordId);
          maybePlayer.ifPresent(
              player -> {
                if (player.getPrivateChannelId() != null) {
                  TextChannel privateChannel =
                      jda.getTextChannelById(player.getPrivateChannelId());
                  if (privateChannel != null) {
                    privateChannel
                        .sendMessageEmbeds(embed.build())
                        .queue(null, err -> {});
                  }
                }
              });
        });
  }
}
