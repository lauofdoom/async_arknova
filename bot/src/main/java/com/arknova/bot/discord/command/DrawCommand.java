package com.arknova.bot.discord.command;

import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.Game;
import com.arknova.bot.repository.CardDefinitionRepository;
import com.arknova.bot.service.DeckService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * /arknova draw — draw a card from the deck or take one from the display.
 *
 * <p>Options:
 *
 * <ul>
 *   <li>{@code source} — "DECK" or "DISPLAY" (required)
 *   <li>{@code card_id} — card ID to take from display (required when source=DISPLAY)
 * </ul>
 *
 * <p>Responses are sent ephemeral (only visible to the calling player).
 */
@Component
@RequiredArgsConstructor
public class DrawCommand implements ArkNovaCommand {

  private final CommandHelper commandHelper;
  private final DeckService deckService;
  private final CardDefinitionRepository cardDefinitionRepository;

  @Override
  public String getSubcommandName() {
    return "draw";
  }

  @Override
  public SubcommandData getSubcommandData() {
    return new SubcommandData("draw", "Draw a card from the deck or take one from the display")
        .addOption(
            OptionType.STRING,
            "source",
            "Where to draw from: DECK or DISPLAY",
            true)
        .addOption(
            OptionType.STRING,
            "card_id",
            "Card ID to take from display (use /arknova display to see IDs)",
            false);
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
          String source =
              event.getOption("source", OptionMapping::getAsString).trim().toUpperCase();
          OptionMapping cardIdOpt = event.getOption("card_id");

          if ("DECK".equals(source)) {
            List<String> drawn;
            try {
              drawn = deckService.drawFromDeck(game.getId(), discordId, 1);
            } catch (IllegalArgumentException e) {
              event.getHook().sendMessage("❌ " + e.getMessage()).setEphemeral(true).queue();
              return;
            }

            if (drawn.isEmpty()) {
              event
                  .getHook()
                  .sendMessage("❌ The deck is empty — no cards remaining.")
                  .setEphemeral(true)
                  .queue();
              return;
            }

            String drawnId = drawn.get(0);
            CardDefinition card =
                cardDefinitionRepository
                    .findById(drawnId)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Unknown card ID drawn from deck: " + drawnId));

            event
                .getHook()
                .sendMessageEmbeds(buildCardEmbed(card, "Card Drawn from Deck").build())
                .setEphemeral(true)
                .queue();

          } else if ("DISPLAY".equals(source)) {
            if (cardIdOpt == null) {
              event
                  .getHook()
                  .sendMessage(
                      "❌ Please provide a card_id when taking from the display.")
                  .setEphemeral(true)
                  .queue();
              return;
            }

            String cardId = cardIdOpt.getAsString().trim();
            try {
              deckService.takeFromDisplay(game.getId(), discordId, cardId);
            } catch (IllegalArgumentException e) {
              event.getHook().sendMessage("❌ " + e.getMessage()).setEphemeral(true).queue();
              return;
            }

            CardDefinition card =
                cardDefinitionRepository
                    .findById(cardId)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Unknown card ID: " + cardId));

            event
                .getHook()
                .sendMessageEmbeds(buildCardEmbed(card, "Card Taken from Display").build())
                .setEphemeral(true)
                .queue();

          } else {
            event
                .getHook()
                .sendMessage("❌ Invalid source \"" + source + "\". Use DECK or DISPLAY.")
                .setEphemeral(true)
                .queue();
          }
        });
  }

  // ── Internal ─────────────────────────────────────────────────────────────

  private EmbedBuilder buildCardEmbed(CardDefinition card, String title) {
    EmbedBuilder embed =
        new EmbedBuilder()
            .setColor(CommandHelper.COLOR_INFO)
            .setTitle(title)
            .addField("Name", card.getName(), true)
            .addField("ID", "`" + card.getId() + "`", true)
            .addField("Type", card.getCardType().name(), true);

    if (card.getBaseCost() > 0) {
      embed.addField("Cost", String.valueOf(card.getBaseCost()), true);
    }
    if (card.getAppealValue() > 0) {
      embed.addField("Appeal", "+" + card.getAppealValue(), true);
    }
    if (card.getAbilityText() != null && !card.getAbilityText().isBlank()) {
      String preview = card.getAbilityText();
      if (preview.length() > 100) {
        preview = preview.substring(0, 100) + "…";
      }
      embed.addField("Ability", preview, false);
    }

    return embed;
  }
}
