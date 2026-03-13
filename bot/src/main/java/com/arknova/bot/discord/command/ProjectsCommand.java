package com.arknova.bot.discord.command;

import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.repository.CardDefinitionRepository;
import com.arknova.bot.repository.SharedBoardStateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * /arknova projects — shows active conservation projects on the shared board.
 *
 * <p>Displays each project's name, card ID (for use in commands), conservation value, worker slots
 * filled, and current status. Visible to all players (not ephemeral).
 */
@Component
@RequiredArgsConstructor
public class ProjectsCommand implements ArkNovaCommand {

  private static final Logger log = LoggerFactory.getLogger(ProjectsCommand.class);

  private final CommandHelper commandHelper;
  private final SharedBoardStateRepository sharedBoardRepo;
  private final CardDefinitionRepository cardDefinitionRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String getSubcommandName() {
    return "projects";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("projects", "View active conservation projects on the shared board");
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

          Optional<SharedBoardState> maybeBoard = sharedBoardRepo.findByGameId(game.getId());
          if (maybeBoard.isEmpty()) {
            CommandHelper.replyError(event, "Shared board state not found for this game.");
            return;
          }
          SharedBoardState board = maybeBoard.get();

          JsonNode root = objectMapper.readTree(board.getConservationBoard());
          JsonNode projects = root.path("projects");

          if (projects.isMissingNode() || projects.isEmpty()) {
            event
                .getHook()
                .sendMessageEmbeds(
                    new EmbedBuilder()
                        .setColor(CommandHelper.COLOR_INFO)
                        .setTitle("Conservation Projects")
                        .setDescription(
                            "No conservation projects have been started yet."
                                + " Use `/arknova association` with sub_action"
                                + " `CONSERVATION_PROJECT` to begin one.")
                        .build())
                .queue();
            return;
          }

          // Batch-fetch all referenced card definitions
          List<String> cardIds = new ArrayList<>();
          Iterator<String> fieldNames = projects.fieldNames();
          while (fieldNames.hasNext()) {
            cardIds.add(fieldNames.next());
          }
          Map<String, CardDefinition> cardDefs =
              cardDefinitionRepository.findAllById(cardIds).stream()
                  .collect(java.util.stream.Collectors.toMap(CardDefinition::getId, c -> c));

          StringBuilder description = new StringBuilder();
          for (String cardId : cardIds) {
            JsonNode entry = projects.get(cardId);
            String status = entry.path("status").asText("unknown");
            JsonNode slotsNode = entry.path("slots");

            int totalSlots = slotsNode.isArray() ? slotsNode.size() : 0;
            int filledSlots = 0;
            if (slotsNode.isArray()) {
              for (JsonNode slot : slotsNode) {
                if (!slot.isNull() && !slot.asText().isEmpty()) {
                  filledSlots++;
                }
              }
            }

            CardDefinition card = cardDefs.get(cardId);
            String name = card != null ? card.getName() : "Unknown Project";
            int conservationValue = card != null ? card.getConservationValue() : 0;

            description.append("**").append(name).append("**");
            description.append(" · `").append(cardId).append("`");
            if (conservationValue > 0) {
              description.append(" · CP: +").append(conservationValue);
            }
            description.append(" · Workers: ").append(filledSlots).append("/").append(totalSlots);
            description.append(" · *").append(status).append("*\n");
          }

          event
              .getHook()
              .sendMessageEmbeds(
                  new EmbedBuilder()
                      .setColor(CommandHelper.COLOR_INFO)
                      .setTitle("Conservation Projects (" + cardIds.size() + ")")
                      .setDescription(description.toString().stripTrailing())
                      .build())
              .queue();
        });
  }
}
