package com.arknova.bot.discord;

import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.renderer.ZooBoardRenderer;
import com.arknova.bot.repository.GameRepository;
import com.arknova.bot.repository.PlayerStateRepository;
import com.arknova.bot.repository.SharedBoardStateRepository;
import com.arknova.bot.service.DeckService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.EnumSet;
import java.util.List;
import javax.imageio.ImageIO;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages per-game Discord channel lifecycle: creation on game start and archival on game end.
 *
 * <p>On start, creates a category with a public {@code #board} channel and a private channel per
 * player. Channel IDs are persisted back to the Game and PlayerState entities.
 *
 * <p>JDA is injected {@code @Lazy} to break the circular bean dependency:
 * JDA → SlashCommandListener → ... → DiscordChannelService → JDA.
 *
 * <p>All channel creation calls use {@code .complete()} because we need the channel IDs
 * synchronously before persisting them. This is acceptable here since it's a one-time setup during
 * {@code /arknova start} — not a hot path.
 */
@Service
public class DiscordChannelService {

  private static final Logger log = LoggerFactory.getLogger(DiscordChannelService.class);

  private static final EnumSet<Permission> PLAYER_CHANNEL_ALLOW =
      EnumSet.of(
          Permission.VIEW_CHANNEL,
          Permission.MESSAGE_SEND,
          Permission.MESSAGE_HISTORY,
          Permission.MESSAGE_EMBED_LINKS,
          Permission.MESSAGE_ATTACH_FILES);

  private static final EnumSet<Permission> DENY_VIEW = EnumSet.of(Permission.VIEW_CHANNEL);

  private final JDA jda;
  private final GameRepository gameRepo;
  private final PlayerStateRepository playerStateRepo;
  private final SharedBoardStateRepository sharedBoardRepo;
  private final ZooBoardRenderer zooBoardRenderer;
  private final DeckService deckService;

  public DiscordChannelService(
      @Lazy JDA jda,
      GameRepository gameRepo,
      PlayerStateRepository playerStateRepo,
      SharedBoardStateRepository sharedBoardRepo,
      ZooBoardRenderer zooBoardRenderer,
      DeckService deckService) {
    this.jda = jda;
    this.gameRepo = gameRepo;
    this.playerStateRepo = playerStateRepo;
    this.sharedBoardRepo = sharedBoardRepo;
    this.zooBoardRenderer = zooBoardRenderer;
    this.deckService = deckService;
  }

  // ── Channel Setup ─────────────────────────────────────────────────────────

  /**
   * Creates the full channel structure for a game and persists the channel IDs.
   *
   * <p>Structure:
   *
   * <pre>
   * 📁 Ark Nova — Game #&lt;short-id&gt;   (Category, hidden from @everyone)
   *   📢 #board                         (visible to all players — board images posted here)
   *   🔒 #&lt;name&gt;-private               (one per player, only that player + bot can see)
   * </pre>
   *
   * <p>If channel creation fails (e.g. missing bot permissions), a warning is logged and the game
   * continues without the channel architecture.
   *
   * @param game the game that just started
   * @param players players in seat order
   */
  @Transactional
  public void setupGameChannels(Game game, List<PlayerState> players) {
    try {
      Guild guild = jda.getGuildById(game.getGuildId());
      if (guild == null) {
        log.warn("Guild {} not found — skipping channel setup for game {}", game.getGuildId(), game.getId());
        return;
      }

      String shortId = game.getId().toString().substring(0, 8);

      // Create category
      Category category =
          guild.createCategory("Ark Nova — Game #" + shortId).complete();
      category
          .upsertPermissionOverride(guild.getPublicRole())
          .deny(DENY_VIEW)
          .complete();

      // Create #board channel — all players can read and write
      TextChannel boardChannel =
          guild.createTextChannel("board", category).complete();
      boardChannel
          .upsertPermissionOverride(guild.getPublicRole())
          .deny(DENY_VIEW)
          .complete();
      for (PlayerState p : players) {
        grantPlayerAccess(boardChannel, guild, p.getDiscordId());
      }
      boardChannel
          .sendMessageEmbeds(
              new EmbedBuilder()
                  .setColor(new java.awt.Color(0x2ECC71))
                  .setTitle("Ark Nova — Game Board")
                  .setDescription(
                      "Board images will be posted here after each turn.\n"
                          + "Use `/arknova board` to request a board render at any time.")
                  .build())
          .complete();

      // Create per-player private channels
      for (PlayerState player : players) {
        String channelName = sanitizeChannelName(player.getDiscordName()) + "-private";
        TextChannel privateChannel =
            guild.createTextChannel(channelName, category).complete();
        privateChannel
            .upsertPermissionOverride(guild.getPublicRole())
            .deny(DENY_VIEW)
            .complete();
        grantPlayerAccess(privateChannel, guild, player.getDiscordId());

        // Welcome message
        privateChannel
            .sendMessageEmbeds(
                new EmbedBuilder()
                    .setColor(new java.awt.Color(0x3498DB))
                    .setTitle("Your Private Channel — " + player.getDiscordName())
                    .setDescription(
                        "This channel is only visible to you.\n"
                            + "Use `/arknova refresh` at any time to update your hand, resources,"
                            + " and board image here.")
                    .build())
            .complete();

        player.setPrivateChannelId(privateChannel.getId());
        playerStateRepo.save(player);
        log.info(
            "Created private channel {} for player {} in game {}",
            privateChannel.getId(),
            player.getDiscordId(),
            game.getId());
      }

      game.setCategoryId(category.getId());
      game.setBoardChannelId(boardChannel.getId());
      gameRepo.save(game);

      log.info(
          "Channel setup complete for game {} — category={}, board={}",
          game.getId(),
          category.getId(),
          boardChannel.getId());

    } catch (Exception e) {
      log.warn(
          "Channel setup failed for game {} — game will continue without private channels: {}",
          game.getId(),
          e.getMessage(),
          e);
    }
  }

  // ── Channel Archive ───────────────────────────────────────────────────────

  /**
   * Archives the game channels on game end by removing all permission overrides so the category
   * becomes visible to everyone. Channels are NOT deleted — history is preserved.
   *
   * @param game the ended game
   * @param players all players in the game
   */
  public void archiveGameChannels(Game game, List<PlayerState> players) {
    if (game.getCategoryId() == null) return;
    try {
      Guild guild = jda.getGuildById(game.getGuildId());
      if (guild == null) return;

      Category category = guild.getCategoryById(game.getCategoryId());
      if (category != null) {
        // Remove @everyone deny so channel becomes readable post-game
        category.upsertPermissionOverride(guild.getPublicRole()).reset().complete();
      }

      // Remove permission overwrites from board channel
      if (game.getBoardChannelId() != null) {
        TextChannel boardChannel = guild.getTextChannelById(game.getBoardChannelId());
        if (boardChannel != null) {
          boardChannel.upsertPermissionOverride(guild.getPublicRole()).reset().complete();
        }
      }

      // Remove overrides from private channels
      for (PlayerState player : players) {
        if (player.getPrivateChannelId() == null) continue;
        TextChannel privateChannel =
            guild.getTextChannelById(player.getPrivateChannelId());
        if (privateChannel != null) {
          privateChannel.upsertPermissionOverride(guild.getPublicRole()).reset().complete();
        }
      }

      log.info("Archived channels for game {}", game.getId());

    } catch (Exception e) {
      log.warn("Channel archive failed for game {}: {}", game.getId(), e.getMessage(), e);
    }
  }

  // ── Player Channel Refresh ────────────────────────────────────────────────

  /**
   * Posts current hand, resources, and board PNG to the player's private channel.
   *
   * @param game the active game
   * @param player the player whose private channel to refresh
   */
  public void refreshPrivateChannel(Game game, PlayerState player) {
    if (player.getPrivateChannelId() == null) {
      log.debug("No private channel configured for player {} in game {}", player.getDiscordId(), game.getId());
      return;
    }
    try {
      Guild guild = jda.getGuildById(game.getGuildId());
      if (guild == null) return;

      TextChannel channel = guild.getTextChannelById(player.getPrivateChannelId());
      if (channel == null) {
        log.warn("Private channel {} not found for player {}", player.getPrivateChannelId(), player.getDiscordId());
        return;
      }

      // Resources embed
      int sharedBreakTrack = sharedBoardRepo.findByGameId(game.getId())
          .map(SharedBoardState::getBreakTrack).orElse(0);
      channel.sendMessageEmbeds(buildResourcesEmbed(player, sharedBreakTrack).build()).queue();

      // Hand embed
      List<PlayerCard> hand = deckService.getHand(game.getId(), player.getDiscordId());
      EmbedBuilder handEmbed =
          new EmbedBuilder()
              .setColor(new java.awt.Color(0x3498DB))
              .setTitle("Hand (" + hand.size() + " card" + (hand.size() == 1 ? "" : "s") + ")")
              .setDescription(
                  hand.isEmpty()
                      ? "*Your hand is empty.*"
                      : com.arknova.bot.discord.command.CommandHelper.formatCardList(hand, false));
      channel.sendMessageEmbeds(handEmbed.build()).queue();

      // Board PNG
      BufferedImage img = zooBoardRenderer.render(player);
      byte[] pngBytes = toPng(img);
      if (pngBytes != null) {
        channel
            .sendMessage(player.getDiscordName() + "'s zoo board:")
            .addFiles(FileUpload.fromData(pngBytes, "board_" + player.getDiscordId() + ".png"))
            .queue();
      }

    } catch (Exception e) {
      log.warn(
          "Failed to refresh private channel for player {} in game {}: {}",
          player.getDiscordId(),
          game.getId(),
          e.getMessage(),
          e);
    }
  }

  // ── Board Channel Posting ─────────────────────────────────────────────────

  /**
   * Posts a board image for the given player to the game's shared {@code #board} channel.
   *
   * @param game the game
   * @param player the player whose board to post
   */
  public void postBoardUpdate(Game game, PlayerState player) {
    if (game.getBoardChannelId() == null) return;
    try {
      Guild guild = jda.getGuildById(game.getGuildId());
      if (guild == null) return;
      TextChannel boardChannel = guild.getTextChannelById(game.getBoardChannelId());
      if (boardChannel == null) return;

      BufferedImage img = zooBoardRenderer.render(player);
      byte[] pngBytes = toPng(img);
      if (pngBytes == null) return;

      boardChannel
          .sendMessage(player.getDiscordName() + "'s zoo board (after turn " + game.getTurnNumber() + "):")
          .addFiles(FileUpload.fromData(pngBytes, "board_" + player.getDiscordId() + ".png"))
          .queue();

    } catch (Exception e) {
      log.warn(
          "Failed to post board update for player {} in game {}: {}",
          player.getDiscordId(),
          game.getId(),
          e.getMessage(),
          e);
    }
  }

  /**
   * Posts a text embed to the game's {@code #board} channel.
   *
   * @param game the game
   * @param embed the embed to post
   */
  public void postToBoardChannel(Game game, EmbedBuilder embed) {
    if (game.getBoardChannelId() == null) return;
    try {
      Guild guild = jda.getGuildById(game.getGuildId());
      if (guild == null) return;
      TextChannel boardChannel = guild.getTextChannelById(game.getBoardChannelId());
      if (boardChannel == null) return;
      boardChannel.sendMessageEmbeds(embed.build()).queue(null, err -> log.warn("Board channel post failed", err));
    } catch (Exception e) {
      log.warn("Failed to post to board channel for game {}: {}", game.getId(), e.getMessage(), e);
    }
  }

  // ── Internal ──────────────────────────────────────────────────────────────

  private void grantPlayerAccess(TextChannel channel, Guild guild, String discordId) {
    try {
      Member member = guild.retrieveMemberById(discordId).complete();
      if (member != null) {
        channel.upsertPermissionOverride(member).grant(PLAYER_CHANNEL_ALLOW).complete();
      }
    } catch (Exception e) {
      log.warn("Could not grant access to channel {} for user {}: {}", channel.getId(), discordId, e.getMessage());
    }
  }

  private static EmbedBuilder buildResourcesEmbed(PlayerState player, int sharedBreakTrack) {
    return new EmbedBuilder()
        .setColor(new java.awt.Color(0x9B59B6))
        .setTitle("Resources — " + player.getDiscordName())
        .addField("Money", String.valueOf(player.getMoney()), true)
        .addField("Appeal", String.valueOf(player.getAppeal()), true)
        .addField("Conservation", String.valueOf(player.getConservation()), true)
        .addField("Reputation", String.valueOf(player.getReputation()), true)
        .addField("Break Track (shared)", String.valueOf(sharedBreakTrack), true)
        .addField("X Tokens", String.valueOf(player.getXTokens()), true)
        .addField("Workers", player.getAssocWorkersAvailable() + "/" + player.getAssocWorkers(), true);
  }

  private static String sanitizeChannelName(String name) {
    return name.toLowerCase()
        .replaceAll("[^a-z0-9_-]", "-")
        .replaceAll("-{2,}", "-")
        .replaceAll("^-|-$", "")
        .substring(0, Math.min(name.length(), 80));
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
