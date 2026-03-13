package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordChannelService;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.renderer.ZooBoardRenderer;
import com.arknova.bot.service.GameService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * /arknova board — renders and posts a player's zoo board image.
 *
 * <p>Defaults to the invoking player's board. Optionally accepts a Discord user mention to view
 * another player's board (useful for spectating or checking opponents' layouts).
 *
 * <p>Response is ephemeral — the board image is only shown to the requesting player. If viewing
 * your own board, it is also posted to your private channel for persistent reference.
 */
@Component
public class BoardCommand implements ArkNovaCommand {

  private static final Logger log = LoggerFactory.getLogger(BoardCommand.class);

  private final GameService gameService;
  private final ZooBoardRenderer renderer;
  private final CommandHelper commandHelper;
  private final DiscordChannelService channelService;
  private final JDA jda;

  public BoardCommand(
      GameService gameService,
      ZooBoardRenderer renderer,
      CommandHelper commandHelper,
      DiscordChannelService channelService,
      @Lazy JDA jda) {
    this.gameService = gameService;
    this.renderer = renderer;
    this.commandHelper = commandHelper;
    this.channelService = channelService;
    this.jda = jda;
  }

  @Override
  public String getSubcommandName() {
    return "board";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("board", "View your zoo board (or another player's)")
        .addOption(OptionType.USER, "player", "Player whose board to view (default: you)", false);
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

          // Resolve which player's board to show
          OptionMapping playerOpt = event.getOption("player");
          String targetDiscordId =
              playerOpt != null ? playerOpt.getAsUser().getId() : event.getUser().getId();

          Optional<PlayerState> maybePlayer =
              gameService.getPlayerState(game.getId(), targetDiscordId);

          if (maybePlayer.isEmpty()) {
            CommandHelper.replyError(event, "That player is not in this game.");
            return;
          }
          PlayerState player = maybePlayer.get();

          // Render
          BufferedImage img = renderer.render(player);
          byte[] pngBytes = toPng(img);
          if (pngBytes == null) {
            CommandHelper.replyError(event, "Failed to render board image. Please try again.");
            return;
          }

          String filename = "board_" + player.getDiscordId() + ".png";
          event
              .getHook()
              .sendMessage(player.getDiscordName() + "'s zoo board:")
              .addFiles(FileUpload.fromData(pngBytes, filename))
              .queue();

          // Also post to private channel when viewing own board
          boolean viewingOwn = targetDiscordId.equals(event.getUser().getId());
          if (viewingOwn && player.getPrivateChannelId() != null) {
            TextChannel privateChannel = jda.getTextChannelById(player.getPrivateChannelId());
            if (privateChannel != null) {
              privateChannel
                  .sendMessage(player.getDiscordName() + "'s zoo board:")
                  .addFiles(FileUpload.fromData(pngBytes, filename))
                  .queue(null, err -> {});
            }
          }
        });
  }

  private static byte[] toPng(BufferedImage img) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      ImageIO.write(img, "PNG", baos);
      return baos.toByteArray();
    } catch (Exception e) {
      log.error("PNG encoding failed", e);
      return null;
    }
  }
}
