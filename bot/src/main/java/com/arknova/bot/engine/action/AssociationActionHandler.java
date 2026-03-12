package com.arknova.bot.engine.action;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.repository.CardDefinitionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles the ASSOCIATION action — perform tasks on the association board.
 *
 * <h2>Card rules</h2>
 *
 * <pre>
 * Unupgraded (I): Perform 1 association task with a maximum value of X.
 *
 * Upgraded   (II): Perform 1 or more DIFFERENT association tasks with a total maximum value of X.
 *                  In addition, you may make 1 donation.
 *                  You may play Conservation Project cards from within reputation range.
 * </pre>
 *
 * X = current strength of this action card (1–5).
 *
 * <h2>Task types and their values</h2>
 *
 * <ul>
 *   <li>PARTNER_ZOO — value = next partner zoo slot number (1st=1, 2nd=2, 3rd=3)
 *   <li>UNIVERSITY — value = next university slot number (1st=1, 2nd=2)
 *   <li>CONSERVATION_PROJECT — value = 1; sends 1 worker to a face-up project
 *   <li>RETURN_WORKERS — value = 1; returns all workers from the board
 * </ul>
 *
 * Partner zoo costs: slot 1 = 2💰, slot 2 = 3💰, slot 3 = 4💰. University costs: slot 1 = 2💰, slot
 * 2 = 3💰.
 *
 * <h2>Request parameters</h2>
 *
 * <ul>
 *   <li>{@code "sub_actions"} — ordered list of 1+ task type strings
 *   <li>{@code "project_ids"} — one project card ID per CONSERVATION_PROJECT entry (in order)
 *   <li>{@code "donation_amount"} — money to donate (upgraded only; optional; multiple of 3)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AssociationActionHandler implements ActionHandler {

  private static final Logger log = LoggerFactory.getLogger(AssociationActionHandler.class);

  /** Money cost for each successive partner zoo slot (0-indexed). */
  private static final int[] PARTNER_ZOO_COSTS = {2, 3, 4};

  /** Money cost for each successive university slot (0-indexed). */
  private static final int[] UNIVERSITY_COSTS = {2, 3};

  /** Number of worker slots per conservation project (Phase 1 simplification). */
  private static final int PROJECT_SLOTS = 2;

  /** Money cost per 1 conservation point donated. */
  private static final int DONATION_RATE = 3;

  private final ObjectMapper objectMapper;
  private final CardDefinitionRepository cardDefRepo;

  @Override
  public ActionCard getActionCard() {
    return ActionCard.ASSOCIATION;
  }

  @Override
  public ActionResult execute(
      ActionRequest request, PlayerState player, SharedBoardState sharedBoard) {

    int strength = player.getStrengthOf(ActionCard.ASSOCIATION);
    boolean upgraded = player.getActionCardOrder().isUpgraded(ActionCard.ASSOCIATION);
    String discordId = request.discordId();

    List<String> subActions = request.paramList("sub_actions");
    List<String> projectIds = request.paramList("project_ids");
    int donationAmount = request.paramInt("donation_amount", 0);

    if (subActions.isEmpty()) {
      return ActionResult.failure(
          "Please specify at least one sub-action: PARTNER_ZOO, UNIVERSITY, "
              + "CONSERVATION_PROJECT, or RETURN_WORKERS.");
    }

    // Unupgraded: exactly 1 task
    if (!upgraded && subActions.size() > 1) {
      return ActionResult.failure(
          "The un-upgraded Association card allows only 1 task per action.");
    }

    // Upgraded: tasks must all be different types
    if (upgraded) {
      Set<String> seen = new HashSet<>();
      for (String a : subActions) {
        if (!seen.add(a.toUpperCase())) {
          return ActionResult.failure(
              "Each task type may only appear once per action (duplicate: " + a + ").");
        }
      }
    }

    // Donation only allowed with upgraded card
    if (donationAmount > 0 && !upgraded) {
      return ActionResult.failure("Donations require the upgraded Association card.");
    }
    if (donationAmount < 0 || (donationAmount > 0 && donationAmount % DONATION_RATE != 0)) {
      return ActionResult.failure(
          "Donation amount must be a multiple of " + DONATION_RATE + " money.");
    }

    // ── Pre-validate: compute total task value and check money/workers ─────────
    ConservationSlots conservationSlots = readConservationSlots(player);
    int totalValue = 0;
    int pendingMoney = donationAmount; // donation counts against money but not against X
    int pendingWorkers = 0;
    int pendingPartnerZoos = 0;
    int pendingUniversities = 0;
    int projectIdCursor = 0;

    for (String raw : subActions) {
      String type = raw.toUpperCase();
      switch (type) {
        case "PARTNER_ZOO" -> {
          int slot = conservationSlots.partnerZooCount() + pendingPartnerZoos;
          if (slot >= PARTNER_ZOO_COSTS.length) {
            return ActionResult.failure(
                "You already have the maximum " + PARTNER_ZOO_COSTS.length + " partner zoos.");
          }
          int taskValue = slot + 1; // slot 0 → value 1, slot 1 → value 2, etc.
          int cost = PARTNER_ZOO_COSTS[slot];
          if (player.getMoney() - pendingMoney < cost) {
            return ActionResult.failure(
                "Partner zoo #"
                    + (slot + 1)
                    + " costs "
                    + cost
                    + "💰 (you have "
                    + (player.getMoney() - pendingMoney)
                    + " remaining).");
          }
          if (player.getAssocWorkersAvailable() - pendingWorkers < 1) {
            return ActionResult.failure("You have no available association workers.");
          }
          totalValue += taskValue;
          pendingMoney += cost;
          pendingWorkers++;
          pendingPartnerZoos++;
        }
        case "UNIVERSITY" -> {
          int slot = conservationSlots.universityCount() + pendingUniversities;
          if (slot >= UNIVERSITY_COSTS.length) {
            return ActionResult.failure(
                "You already have the maximum " + UNIVERSITY_COSTS.length + " universities.");
          }
          int taskValue = slot + 1;
          int cost = UNIVERSITY_COSTS[slot];
          if (player.getMoney() - pendingMoney < cost) {
            return ActionResult.failure(
                "University #"
                    + (slot + 1)
                    + " costs "
                    + cost
                    + "💰 (you have "
                    + (player.getMoney() - pendingMoney)
                    + " remaining).");
          }
          if (player.getAssocWorkersAvailable() - pendingWorkers < 1) {
            return ActionResult.failure("You have no available association workers.");
          }
          totalValue += taskValue;
          pendingMoney += cost;
          pendingWorkers++;
          pendingUniversities++;
        }
        case "CONSERVATION_PROJECT" -> {
          if (projectIdCursor >= projectIds.size()) {
            return ActionResult.failure(
                "Please supply a project card ID for each CONSERVATION_PROJECT "
                    + "(param: project_ids).");
          }
          String projectId = projectIds.get(projectIdCursor++);
          CardDefinition proj = cardDefRepo.findById(projectId).orElse(null);
          if (proj == null || proj.getCardType() != CardDefinition.CardType.CONSERVATION) {
            return ActionResult.failure(
                "Card " + projectId + " is not a valid conservation project.");
          }
          if (player.getAssocWorkersAvailable() - pendingWorkers < 1) {
            return ActionResult.failure("You have no available association workers.");
          }
          totalValue += 1; // conservation projects always have task value 1
          pendingWorkers++;
        }
        case "RETURN_WORKERS" -> totalValue += 1;
        default -> {
          return ActionResult.failure(
              "Unknown sub-action: "
                  + raw
                  + ". Valid types: PARTNER_ZOO, UNIVERSITY, CONSERVATION_PROJECT, RETURN_WORKERS.");
        }
      }
    }

    // Core constraint: total task value ≤ X (strength)
    if (totalValue > strength) {
      return ActionResult.failure(
          "Total task value "
              + totalValue
              + " exceeds the maximum of X="
              + strength
              + " at current strength. Choose tasks with a lower combined value.");
    }

    // Validate donation money
    if (donationAmount > 0 && player.getMoney() - pendingMoney + donationAmount < 0) {
      return ActionResult.failure("Insufficient money for donation of " + donationAmount + "💰.");
    }

    // ── Apply all sub-actions ─────────────────────────────────────────────────

    StringBuilder summary = new StringBuilder();
    summary
        .append(request.discordName())
        .append(" used **Association** (strength ")
        .append(strength)
        .append(", X=")
        .append(strength)
        .append("):");

    int totalMoneyCost = 0;
    int totalConservation = 0;
    boolean anyManualResolution = false;

    projectIdCursor = 0;
    for (String raw : subActions) {
      String type = raw.toUpperCase();
      switch (type) {
        case "PARTNER_ZOO" -> {
          int slot = conservationSlots.partnerZooCount();
          int cost = PARTNER_ZOO_COSTS[slot];
          player.setMoney(player.getMoney() - cost);
          player.setAssocWorkersAvailable(player.getAssocWorkersAvailable() - 1);
          totalMoneyCost += cost;
          conservationSlots = conservationSlots.withPartnerZoo("PARTNER_ZOO_" + (slot + 1));
          summary
              .append("\n  • Partner zoo #")
              .append(slot + 1)
              .append(" claimed for ")
              .append(cost)
              .append("💰 — effect: manual resolution.");
          anyManualResolution = true;
        }
        case "UNIVERSITY" -> {
          int slot = conservationSlots.universityCount();
          int cost = UNIVERSITY_COSTS[slot];
          player.setMoney(player.getMoney() - cost);
          player.setAssocWorkersAvailable(player.getAssocWorkersAvailable() - 1);
          totalMoneyCost += cost;
          conservationSlots = conservationSlots.withUniversity("UNIVERSITY_" + (slot + 1));
          summary
              .append("\n  • University #")
              .append(slot + 1)
              .append(" claimed for ")
              .append(cost)
              .append("💰 — effect: manual resolution.");
          anyManualResolution = true;
        }
        case "CONSERVATION_PROJECT" -> {
          String projectId = projectIds.get(projectIdCursor++);
          CardDefinition proj = cardDefRepo.getReferenceById(projectId);
          player.setAssocWorkersAvailable(player.getAssocWorkersAvailable() - 1);

          boolean completed = contributeToProject(sharedBoard, projectId, discordId);
          if (completed) {
            int cp = proj.getConservationValue();
            player.setConservation(player.getConservation() + cp);
            totalConservation += cp;
            summary
                .append("\n  • Conservation project **")
                .append(proj.getName())
                .append("** completed! +")
                .append(cp)
                .append(" conservation.");
          } else {
            summary.append("\n  • Worker sent to **").append(proj.getName()).append("**.");
          }
        }
        case "RETURN_WORKERS" -> {
          player.setAssocWorkersAvailable(player.getAssocWorkers());
          summary
              .append("\n  • All workers returned (")
              .append(player.getAssocWorkers())
              .append(" available).");
        }
      }
    }

    // Donation (upgraded bonus — free beyond task value budget)
    if (donationAmount > 0) {
      int cp = donationAmount / DONATION_RATE;
      player.setMoney(player.getMoney() - donationAmount);
      player.setConservation(player.getConservation() + cp);
      totalMoneyCost += donationAmount;
      totalConservation += cp;
      summary
          .append("\n  • Donated ")
          .append(donationAmount)
          .append("💰 → +")
          .append(cp)
          .append(" conservation.");
    }

    writeConservationSlots(player, conservationSlots);

    log.info(
        "Game {}: {} ASSOCIATION strength={} total_value={} money={} cp={} workers={}",
        request.gameId(),
        discordId,
        strength,
        totalValue,
        totalMoneyCost,
        totalConservation,
        player.getAssocWorkersAvailable());

    return new ActionResult(
        true,
        null,
        ActionCard.ASSOCIATION,
        strength,
        summary.toString(),
        Map.of(
            "money_spent", totalMoneyCost,
            "conservation_gained", totalConservation,
            "total_task_value", totalValue,
            "workers_available", player.getAssocWorkersAvailable()),
        List.of(),
        false,
        anyManualResolution,
        null);
  }

  // ── Conservation project board ────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private boolean contributeToProject(
      SharedBoardState sharedBoard, String projectId, String discordId) {
    try {
      Map<String, Object> board =
          objectMapper.readValue(sharedBoard.getConservationBoard(), new TypeReference<>() {});
      Map<String, Object> projects =
          (Map<String, Object>) board.getOrDefault("projects", new HashMap<>());

      Map<String, Object> proj =
          (Map<String, Object>)
              projects.computeIfAbsent(
                  projectId,
                  k -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("status", "available");
                    p.put(
                        "slots",
                        new ArrayList<>(java.util.Arrays.asList(new Object[PROJECT_SLOTS])));
                    return p;
                  });

      List<Object> slots = (List<Object>) proj.get("slots");
      if (slots == null) {
        slots = new ArrayList<>(java.util.Arrays.asList(new Object[PROJECT_SLOTS]));
        proj.put("slots", slots);
      }

      for (int i = 0; i < slots.size(); i++) {
        if (slots.get(i) == null) {
          slots.set(i, discordId);
          break;
        }
      }

      boolean completed = slots.stream().noneMatch(s -> s == null);
      if (completed) proj.put("status", "completed");

      sharedBoard.setConservationBoard(objectMapper.writeValueAsString(board));
      return completed;
    } catch (Exception e) {
      log.error("Failed to update conservation board for project {}", projectId, e);
      return false;
    }
  }

  // ── Player conservation slots ─────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private ConservationSlots readConservationSlots(PlayerState player) {
    try {
      Map<String, Object> raw =
          objectMapper.readValue(player.getConservationSlots(), new TypeReference<>() {});
      return new ConservationSlots(
          (List<Object>) raw.getOrDefault("partnerZoos", List.of()),
          (List<Object>) raw.getOrDefault("universities", List.of()),
          (List<Object>) raw.getOrDefault("projects", List.of()));
    } catch (Exception e) {
      log.error("Failed to read conservationSlots for {}", player.getDiscordId(), e);
      return new ConservationSlots(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
  }

  private void writeConservationSlots(PlayerState player, ConservationSlots slots) {
    try {
      Map<String, Object> raw = new HashMap<>();
      raw.put("partnerZoos", slots.partnerZoos());
      raw.put("universities", slots.universities());
      raw.put("projects", slots.projects());
      player.setConservationSlots(objectMapper.writeValueAsString(raw));
    } catch (Exception e) {
      log.error("Failed to write conservationSlots for {}", player.getDiscordId(), e);
    }
  }

  // ── Value objects ─────────────────────────────────────────────────────────────

  record ConservationSlots(
      List<Object> partnerZoos, List<Object> universities, List<Object> projects) {

    int partnerZooCount() {
      return (int) partnerZoos.stream().filter(s -> s != null).count();
    }

    int universityCount() {
      return (int) universities.stream().filter(s -> s != null).count();
    }

    ConservationSlots withPartnerZoo(String label) {
      List<Object> updated = new ArrayList<>(partnerZoos);
      updated.add(label);
      return new ConservationSlots(updated, universities, projects);
    }

    ConservationSlots withUniversity(String label) {
      List<Object> updated = new ArrayList<>(universities);
      updated.add(label);
      return new ConservationSlots(partnerZoos, updated, projects);
    }
  }
}
