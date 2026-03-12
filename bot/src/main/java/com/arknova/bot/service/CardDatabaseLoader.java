package com.arknova.bot.service;

import com.arknova.bot.config.ArkNovaProperties;
import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.CardDefinition.CardType;
import com.arknova.bot.repository.CardDefinitionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the Ark Nova card database from {@code cards/base_game.json} at startup.
 *
 * <p>The JSON file is generated from the Next-Ark-Nova-Cards community repository by running {@code
 * scripts/convert-cards.ts}. Cards already in the database are updated with any new data (name
 * changes, image URL updates); new cards are inserted; no cards are deleted (preserving game
 * references to retired/corrected cards).
 *
 * <p>Card loading is idempotent — safe to run on every startup.
 */
@Service
@RequiredArgsConstructor
public class CardDatabaseLoader {

  private static final Logger log = LoggerFactory.getLogger(CardDatabaseLoader.class);

  private final CardDefinitionRepository cardRepo;
  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;
  private final ArkNovaProperties props;

  @PostConstruct
  @Transactional
  public void loadCards() {
    String resourcePath = props.cards().resourcePath();
    log.info("Loading card database from {}", resourcePath);

    try (InputStream is = resourceLoader.getResource(resourcePath).getInputStream()) {
      JsonNode root = objectMapper.readTree(is);

      if (!root.isArray()) {
        log.error("Card database JSON must be a top-level array. Skipping load.");
        return;
      }

      List<CardDefinition> toSave = new ArrayList<>();
      int newCount = 0;
      int updatedCount = 0;

      for (JsonNode node : root) {
        String id = node.path("id").asText();
        if (id.isBlank()) {
          log.warn("Skipping card with missing id: {}", node);
          continue;
        }

        CardDefinition card = cardRepo.findById(id).orElse(null);
        boolean isNew = card == null;
        if (isNew) {
          card = new CardDefinition();
          card.setId(id);
          newCount++;
        } else {
          updatedCount++;
        }

        mapJsonToCard(node, card);
        toSave.add(card);
      }

      cardRepo.saveAll(toSave);

      long total = cardRepo.count();
      long automated = cardRepo.countByEffectCodeIsNotNull();
      log.info(
          "Card database loaded: {} new, {} updated, {} total ({}/{} automated)",
          newCount,
          updatedCount,
          total,
          automated,
          total);

    } catch (Exception e) {
      log.error("Failed to load card database from {}: {}", resourcePath, e.getMessage(), e);
      // Don't kill startup — the bot can run without full card data (degraded mode)
    }
  }

  private void mapJsonToCard(JsonNode node, CardDefinition card) {
    card.setName(node.path("name").asText("Unknown"));
    card.setCardType(parseCardType(node.path("card_type").asText("ANIMAL")));
    card.setBaseCost(node.path("base_cost").asInt(0));

    if (!node.path("min_enclosure_size").isMissingNode()
        && !node.path("min_enclosure_size").isNull()) {
      card.setMinEnclosureSize(node.path("min_enclosure_size").asInt());
    }

    card.setTags(parseStringArray(node.path("tags")));
    card.setRequirements(parseStringArray(node.path("requirements")));
    card.setAppealValue(node.path("appeal_value").asInt(0));
    card.setConservationValue(node.path("conservation_value").asInt(0));
    card.setReputationValue(node.path("reputation_value").asInt(0));

    if (!node.path("ability_text").isMissingNode()) {
      card.setAbilityText(node.path("ability_text").asText(null));
    }

    // Only update effect_code from JSON if it is explicitly present.
    // This prevents overwriting manually-implemented effect_code when regenerating
    // the JSON from the community repo (which never has effect_code).
    // To update effect_code, use the DB directly or a separate migration.
    // (The JSON file is the source for metadata; the DB is the source for effect_code.)

    if (!node.path("image_url").isMissingNode() && !node.path("image_url").isNull()) {
      card.setImageUrl(node.path("image_url").asText(null));
    }

    card.setSource(node.path("source").asText("BASE"));
    card.setCardNumber(node.path("card_number").asText(null));
  }

  private CardType parseCardType(String raw) {
    return switch (raw.toUpperCase()) {
      case "SPONSOR" -> CardType.SPONSOR;
      case "CONSERVATION" -> CardType.CONSERVATION;
      case "FINAL_SCORING" -> CardType.FINAL_SCORING;
      default -> CardType.ANIMAL;
    };
  }

  private String[] parseStringArray(JsonNode arrayNode) {
    if (arrayNode.isMissingNode() || arrayNode.isNull() || !arrayNode.isArray()) {
      return new String[] {};
    }
    List<String> values = new ArrayList<>();
    for (JsonNode element : arrayNode) {
      values.add(element.asText());
    }
    return values.toArray(String[]::new);
  }
}
