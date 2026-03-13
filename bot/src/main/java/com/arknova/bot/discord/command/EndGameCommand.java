package com.arknova.bot.discord.command;

import com.arknova.bot.discord.DiscordChannelService;
import com.arknova.bot.discord.DiscordLogger;
import com.arknova.bot.engine.ScoringTables;
import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerCard.CardLocation;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.repository.CardDefinitionRepository;
import com.arknova.bot.repository.PlayerCardRepository;
import com.arknova.bot.service.GameService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * /arknova endgame — ends the active game and posts a final VP breakdown.
 *
 * <p>Any active participant may call this command. On success, the game transitions to ENDED and a
 * public embed showing each player's full VP breakdown is posted to the channel.
 *
 * <p>VP breakdown per player:
 *
 * <ul>
 *   <li>Appeal track (direct VP)
 *   <li>Conservation track (direct VP)
 *   <li>Partner zoo bonuses (+{@link ScoringTables#PARTNER_ZOO_VP} each)
 *   <li>University bonuses (+{@link ScoringTables#UNIVERSITY_VP} each)
 *   <li>X token scoring (xTokens × break track position)
 *   <li>FINAL_SCORING cards in hand — listed for manual resolution
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EndGameCommand implements ArkNovaCommand {

  private static final Logger log = LoggerFactory.getLogger(EndGameCommand.class);

  private final GameService gameService;
  private final CommandHelper commandHelper;
  private final DiscordLogger discordLogger;
  private final DiscordChannelService channelService;
  private final PlayerCardRepository playerCardRepository;
  private final CardDefinitionRepository cardDefinitionRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String getSubcommandName() {
    return "endgame";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("endgame", "End the game and display final VP breakdown");
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    CommandHelper.runSafely(
        event,
        () -> {
          String channelId = event.getChannel().getId();
          String discordId = event.getUser().getId();

          Optional<Game> maybeGame = commandHelper.getActiveGame(event);
          if (maybeGame.isEmpty()) return;

          Game endedGame = gameService.endGame(channelId, discordId);
          List<PlayerState> players = gameService.getPlayersInOrder(endedGame.getId());

          EmbedBuilder embed =
              new EmbedBuilder()
                  .setColor(CommandHelper.COLOR_INFO)
                  .setTitle("Game Over — Final VP Breakdown")
                  .setDescription(
                      "The game has ended. Calculate any FINAL_SCORING cards manually and add to"
                          + " totals.");

          int highScore = -1;
          String leader = "";

          for (PlayerState player : players) {
            int appealVp = player.getAppeal();
            int conservationVp = player.getConservation();
            int partnerZooVp = countPartnerZoos(player) * ScoringTables.PARTNER_ZOO_VP;
            int universityVp = countUniversities(player) * ScoringTables.UNIVERSITY_VP;
            int xTokenVp = ScoringTables.xTokenVp(player.getXTokens(), player.getBreakTrack());
            int subtotal = appealVp + conservationVp + partnerZooVp + universityVp + xTokenVp;

            List<String> finalScoringCards = getFinalScoringCardNames(endedGame, player);

            StringBuilder sb = new StringBuilder();
            sb.append("Appeal: **").append(appealVp).append("**");
            sb.append(" · Conservation: **").append(conservationVp).append("**");
            if (partnerZooVp > 0) sb.append(" · Partner Zoos: **+").append(partnerZooVp).append("**");
            if (universityVp > 0) sb.append(" · Universities: **+").append(universityVp).append("**");
            if (xTokenVp > 0)
              sb.append(" · X Tokens (")
                  .append(player.getXTokens())
                  .append("×")
                  .append(player.getBreakTrack())
                  .append("): **+")
                  .append(xTokenVp)
                  .append("**");
            sb.append("\n**Subtotal (excl. scoring cards): ").append(subtotal).append("**");

            if (!finalScoringCards.isEmpty()) {
              sb.append("\n📋 Scoring cards (resolve manually): ");
              sb.append(String.join(", ", finalScoringCards));
            }

            embed.addField(player.getDiscordName(), sb.toString(), false);

            if (subtotal > highScore) {
              highScore = subtotal;
              leader = player.getDiscordName();
            }
          }

          embed.setFooter(
              "Turn "
                  + endedGame.getTurnNumber()
                  + " · "
                  + (leader.isEmpty() ? "No leader" : leader + " leads with " + highScore + " VP"));

          event.getHook().sendMessageEmbeds(embed.build()).queue();
          discordLogger.logGameEnded(endedGame, players);
          channelService.postToBoardChannel(endedGame, embed);
          final Game gameRef = endedGame;
          final List<PlayerState> playersRef = players;
          new Thread(
                  () -> channelService.archiveGameChannels(gameRef, playersRef),
                  "channel-archive-" + endedGame.getId())
              .start();
        });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private int countPartnerZoos(PlayerState player) {
    try {
      JsonNode slots = objectMapper.readTree(player.getConservationSlots());
      JsonNode partnerZoos = slots.path("partnerZoos");
      if (!partnerZoos.isArray()) return 0;
      int count = 0;
      for (JsonNode slot : partnerZoos) {
        if (!slot.isNull() && !slot.asText("").isEmpty()) count++;
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to parse conservationSlots for player {}", player.getDiscordId(), e);
      return 0;
    }
  }

  private int countUniversities(PlayerState player) {
    try {
      JsonNode slots = objectMapper.readTree(player.getConservationSlots());
      JsonNode universities = slots.path("universities");
      if (!universities.isArray()) return 0;
      int count = 0;
      for (JsonNode slot : universities) {
        if (!slot.isNull() && !slot.asText("").isEmpty()) count++;
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to parse conservationSlots for player {}", player.getDiscordId(), e);
      return 0;
    }
  }

  private List<String> getFinalScoringCardNames(Game game, PlayerState player) {
    List<String> names = new ArrayList<>();
    try {
      List<String> cardIds =
          playerCardRepository.findCardIdsByGameIdAndDiscordIdAndLocation(
              game.getId(), player.getDiscordId(), CardLocation.HAND);
      for (String cardId : cardIds) {
        cardDefinitionRepository
            .findById(cardId)
            .filter(def -> def.getCardType() == CardDefinition.CardType.FINAL_SCORING)
            .ifPresent(def -> names.add(def.getName()));
      }
    } catch (Exception e) {
      log.warn(
          "Failed to load hand for player {} in game {}", player.getDiscordId(), game.getId(), e);
    }
    return names;
  }
}
