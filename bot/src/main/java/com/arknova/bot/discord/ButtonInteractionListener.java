package com.arknova.bot.discord;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles all button and select-menu interactions. Button custom IDs follow the format:
 *
 * <pre>arknova:{action}:{step}:{gameId}:{...params}</pre>
 *
 * Example: {@code arknova:build:size:game-uuid:3}
 */
@Component
public class ButtonInteractionListener extends ListenerAdapter {

  private static final Logger log = LoggerFactory.getLogger(ButtonInteractionListener.class);

  private static final String PREFIX = "arknova:";

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String id = event.getComponentId();
    if (!id.startsWith(PREFIX)) return;

    // Acknowledge immediately to reset the 3s Discord timer
    event.deferEdit().queue();

    String[] parts = id.substring(PREFIX.length()).split(":", -1);
    if (parts.length < 2) {
      log.warn("Malformed button id: {}", id);
      return;
    }

    String action = parts[0];
    String step = parts[1];

    log.debug("Button interaction: action={} step={} id={}", action, step, id);

    // Route to the appropriate flow handler
    // Flows are added here as the bot grows (BuildFlow, AnimalsFlow, etc.)
    switch (action) {
      case "ping" -> handlePingAck(event);
      default -> {
        log.warn("No handler for button action: {}", action);
        event.getHook().sendMessage("Unknown button action.").setEphemeral(true).queue();
      }
    }
  }

  @Override
  public void onStringSelectInteraction(StringSelectInteractionEvent event) {
    String id = event.getComponentId();
    if (!id.startsWith(PREFIX)) return;

    event.deferEdit().queue();

    log.debug("Select interaction: id={} values={}", id, event.getValues());
    // Select menu routing will be added alongside each action flow
  }

  private void handlePingAck(ButtonInteractionEvent event) {
    event.getHook().sendMessage("Pong acknowledged!").setEphemeral(true).queue();
  }
}
