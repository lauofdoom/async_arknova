package com.arknova.bot.engine.action;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.arknova.bot.engine.ActionCard;
import com.arknova.bot.engine.ActionCardOrder;
import com.arknova.bot.engine.model.ActionRequest;
import com.arknova.bot.engine.model.ActionResult;
import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.repository.CardDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssociationActionHandler")
class AssociationActionHandlerTest {

  @Mock CardDefinitionRepository cardDefRepo;

  AssociationActionHandler handler;

  private PlayerState player;
  private SharedBoardState sharedBoard;
  private UUID gameId;

  private static final String EMPTY_SLOTS =
      "{\"partnerZoos\":[],\"universities\":[],\"projects\":[]}";
  private static final String EMPTY_CONSERVATION_BOARD = "{\"projects\":{}}";

  @BeforeEach
  void setUp() {
    handler = new AssociationActionHandler(new ObjectMapper(), cardDefRepo);

    gameId = UUID.randomUUID();
    player = new PlayerState();
    player.setDiscordId("player1");
    player.setDiscordName("Alice");
    player.setMoney(20);
    player.setAssocWorkers(3);
    player.setAssocWorkersAvailable(3);
    player.setConservationSlots(EMPTY_SLOTS);
    // Default order: ASSOCIATION at strength 4

    sharedBoard = new SharedBoardState();
    sharedBoard.setGameId(gameId);
    sharedBoard.setConservationBoard(EMPTY_CONSERVATION_BOARD);
  }

  private ActionRequest req(Map<String, Object> params) {
    return new ActionRequest(gameId, "player1", "Alice", ActionCard.ASSOCIATION, params, null);
  }

  private void setStrength(int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(ActionCard.ASSOCIATION);
    cards.add(targetStrength - 1, ActionCard.ASSOCIATION);
    player.setActionCardOrder(new ActionCardOrder(cards, Set.of()));
  }

  private void setStrengthUpgraded(int targetStrength) {
    List<ActionCard> cards = new ArrayList<>(ActionCardOrder.DEFAULT_ORDER);
    cards.remove(ActionCard.ASSOCIATION);
    cards.add(targetStrength - 1, ActionCard.ASSOCIATION);
    player.setActionCardOrder(new ActionCardOrder(cards, Set.of(ActionCard.ASSOCIATION)));
  }

  private CardDefinition conservationCard(String id, int cp) {
    CardDefinition card = new CardDefinition();
    card.setId(id);
    card.setName("Project " + id);
    card.setCardType(CardDefinition.CardType.CONSERVATION);
    card.setConservationValue(cp);
    return card;
  }

  // ── Validation ────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("fails when sub_actions is empty")
    void noSubActions() {
      ActionResult r = handler.execute(req(Map.of()), player, sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("sub-action");
    }

    @Test
    @DisplayName("unupgraded: fails when more than 1 sub-action is submitted")
    void unupgradedOnlyOneTask() {
      setStrength(3);
      ActionResult r =
          handler.execute(
              req(Map.of("sub_actions", List.of("RETURN_WORKERS", "RETURN_WORKERS"))),
              player,
              sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("1 task");
    }

    @Test
    @DisplayName("upgraded: fails when duplicate task types are submitted")
    void upgradedNoDuplicates() {
      setStrengthUpgraded(5);
      ActionResult r =
          handler.execute(
              req(Map.of("sub_actions", List.of("RETURN_WORKERS", "RETURN_WORKERS"))),
              player,
              sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("duplicate");
    }

    @Test
    @DisplayName("fails when total task value exceeds strength X")
    void totalValueExceedsStrength() {
      // PARTNER_ZOO slot 1 has value 1; fine. CONSERVATION_PROJECT value=1 → total=2 > X=1
      // For unupgraded we only allow 1 task anyway, so test via upgraded
      setStrengthUpgraded(1);
      player.setMoney(20);
      // Must stub BEFORE execute so the conservation project passes card-type validation
      when(cardDefRepo.findById("proj1")).thenReturn(Optional.of(conservationCard("proj1", 2)));
      ActionResult r =
          handler.execute(
              req(
                  Map.of(
                      "sub_actions", List.of("PARTNER_ZOO", "CONSERVATION_PROJECT"),
                      "project_ids", List.of("proj1"))),
              player,
              sharedBoard);
      // PARTNER_ZOO value=1, CONSERVATION_PROJECT value=1 → total=2 > X=1
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("value");
    }

    @Test
    @DisplayName("fails when donation amount is not a multiple of 3")
    void donationNotMultipleOf3() {
      setStrengthUpgraded(3);
      ActionResult r =
          handler.execute(
              req(Map.of("sub_actions", List.of("RETURN_WORKERS"), "donation_amount", 4)),
              player,
              sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("multiple of 3");
    }

    @Test
    @DisplayName("fails when donation is specified on unupgraded card")
    void donationRequiresUpgrade() {
      setStrength(3);
      ActionResult r =
          handler.execute(
              req(Map.of("sub_actions", List.of("RETURN_WORKERS"), "donation_amount", 3)),
              player,
              sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("upgraded");
    }

    @Test
    @DisplayName("fails when player cannot afford partner zoo cost")
    void insufficientMoneyPartnerZoo() {
      setStrength(2);
      player.setMoney(1); // partner zoo slot 1 costs 2
      ActionResult r =
          handler.execute(req(Map.of("sub_actions", List.of("PARTNER_ZOO"))), player, sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("costs");
    }

    @Test
    @DisplayName("fails when no association workers available")
    void noWorkersAvailable() {
      setStrength(2);
      player.setAssocWorkersAvailable(0);
      ActionResult r =
          handler.execute(req(Map.of("sub_actions", List.of("PARTNER_ZOO"))), player, sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("worker");
    }

    @Test
    @DisplayName("fails when partner zoo is already full (3 slots claimed)")
    void partnerZooFull() {
      setStrength(5);
      // Simulate 3 partner zoos already claimed
      player.setConservationSlots(
          "{\"partnerZoos\":[\"PARTNER_ZOO_1\",\"PARTNER_ZOO_2\",\"PARTNER_ZOO_3\"],"
              + "\"universities\":[],\"projects\":[]}");
      ActionResult r =
          handler.execute(req(Map.of("sub_actions", List.of("PARTNER_ZOO"))), player, sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("maximum");
    }

    @Test
    @DisplayName("fails when university is already full (2 slots claimed)")
    void universityFull() {
      setStrength(3);
      // Simulate both universities already claimed
      player.setConservationSlots(
          "{\"partnerZoos\":[],"
              + "\"universities\":[\"UNIVERSITY_1\",\"UNIVERSITY_2\"],\"projects\":[]}");
      ActionResult r =
          handler.execute(req(Map.of("sub_actions", List.of("UNIVERSITY"))), player, sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("maximum");
    }

    @Test
    @DisplayName("fails when CONSERVATION_PROJECT project_id is missing")
    void missingProjectId() {
      setStrength(2);
      ActionResult r =
          handler.execute(
              req(
                  Map.of(
                      "sub_actions", List.of("CONSERVATION_PROJECT"),
                      "project_ids", List.of())),
              player,
              sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("project card ID");
    }

    @Test
    @DisplayName("fails when project card is not a CONSERVATION card type")
    void invalidProjectCardType() {
      setStrength(2);
      CardDefinition animalCard = new CardDefinition();
      animalCard.setId("anim1");
      animalCard.setCardType(CardDefinition.CardType.ANIMAL);
      when(cardDefRepo.findById("anim1")).thenReturn(Optional.of(animalCard));

      ActionResult r =
          handler.execute(
              req(
                  Map.of(
                      "sub_actions", List.of("CONSERVATION_PROJECT"),
                      "project_ids", List.of("anim1"))),
              player,
              sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("not a valid conservation");
    }

    @Test
    @DisplayName("fails when unknown sub_action type is provided")
    void unknownSubAction() {
      setStrength(3);
      ActionResult r =
          handler.execute(req(Map.of("sub_actions", List.of("DANCE"))), player, sharedBoard);
      assertThat(r.success()).isFalse();
      assertThat(r.errorMessage()).containsIgnoringCase("Unknown sub-action");
    }
  }

  // ── Happy paths ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("happy paths")
  class HappyPaths {

    @Test
    @DisplayName("RETURN_WORKERS: restores all workers, value=1, costs nothing")
    void returnWorkers() {
      setStrength(2); // X=2 ≥ value 1
      player.setAssocWorkersAvailable(1); // only 1 of 3 available

      ActionResult r =
          handler.execute(
              req(Map.of("sub_actions", List.of("RETURN_WORKERS"))), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getAssocWorkersAvailable()).isEqualTo(3); // fully restored
      assertThat(player.getMoney()).isEqualTo(20); // no cost
      assertThat((Integer) r.deltas().get("total_task_value")).isEqualTo(1);
    }

    @Test
    @DisplayName("PARTNER_ZOO slot 1: costs 2 money, uses 1 worker, task value=1")
    void partnerZooSlot1() {
      setStrength(2);

      ActionResult r =
          handler.execute(req(Map.of("sub_actions", List.of("PARTNER_ZOO"))), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(18); // 20 - 2
      assertThat(player.getAssocWorkersAvailable()).isEqualTo(2); // 3 - 1
      assertThat((Integer) r.deltas().get("money_spent")).isEqualTo(2);
      assertThat((Integer) r.deltas().get("total_task_value")).isEqualTo(1);
    }

    @Test
    @DisplayName("PARTNER_ZOO slot 2 (second claim): costs 3 money, task value=2")
    void partnerZooSlot2() {
      setStrength(3);
      player.setConservationSlots(
          "{\"partnerZoos\":[\"PARTNER_ZOO_1\"],\"universities\":[],\"projects\":[]}");

      ActionResult r =
          handler.execute(req(Map.of("sub_actions", List.of("PARTNER_ZOO"))), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(17); // 20 - 3
      assertThat((Integer) r.deltas().get("total_task_value")).isEqualTo(2);
    }

    @Test
    @DisplayName("UNIVERSITY slot 1: costs 2 money, task value=1")
    void universitySlot1() {
      setStrength(2);

      ActionResult r =
          handler.execute(req(Map.of("sub_actions", List.of("UNIVERSITY"))), player, sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(18);
      assertThat((Integer) r.deltas().get("total_task_value")).isEqualTo(1);
    }

    @Test
    @DisplayName("CONSERVATION_PROJECT: sends worker, awards CP when project completes")
    void conservationProjectCompletes() {
      setStrength(2);
      CardDefinition proj = conservationCard("proj1", 2);
      when(cardDefRepo.findById("proj1")).thenReturn(Optional.of(proj));
      when(cardDefRepo.getReferenceById("proj1")).thenReturn(proj);
      // Pre-fill the first slot so our worker completes the project
      sharedBoard.setConservationBoard(
          "{\"projects\":{\"proj1\":{\"status\":\"available\",\"slots\":[\"player2\",null]}}}");

      ActionResult r =
          handler.execute(
              req(
                  Map.of(
                      "sub_actions", List.of("CONSERVATION_PROJECT"),
                      "project_ids", List.of("proj1"))),
              player,
              sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getConservation()).isEqualTo(2); // proj has 2 CP
      assertThat(player.getAssocWorkersAvailable()).isEqualTo(2);
      assertThat(r.summary()).containsIgnoringCase("completed");
    }

    @Test
    @DisplayName("CONSERVATION_PROJECT: sends worker but project not yet complete")
    void conservationProjectNotYetComplete() {
      setStrength(2);
      CardDefinition proj = conservationCard("proj1", 2);
      when(cardDefRepo.findById("proj1")).thenReturn(Optional.of(proj));
      when(cardDefRepo.getReferenceById("proj1")).thenReturn(proj);

      ActionResult r =
          handler.execute(
              req(
                  Map.of(
                      "sub_actions", List.of("CONSERVATION_PROJECT"),
                      "project_ids", List.of("proj1"))),
              player,
              sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getConservation()).isEqualTo(0); // project not yet complete
      assertThat(player.getAssocWorkersAvailable()).isEqualTo(2);
      assertThat(r.summary()).containsIgnoringCase("Worker sent");
    }

    @Test
    @DisplayName("upgraded: multiple different tasks with total value ≤ X")
    void upgradedMultipleTasks() {
      setStrengthUpgraded(4); // X=4
      // PARTNER_ZOO value=1 (slot 1), UNIVERSITY value=1 (slot 1), RETURN_WORKERS value=1
      // Total = 3 ≤ X=4 ✓
      // Costs: partner zoo 2💰 + university 2💰 = 4💰 total

      ActionResult r =
          handler.execute(
              req(Map.of("sub_actions", List.of("PARTNER_ZOO", "UNIVERSITY", "RETURN_WORKERS"))),
              player,
              sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(16); // 20 - 4
      assertThat(player.getAssocWorkersAvailable()).isEqualTo(3); // returned by RETURN_WORKERS
      assertThat((Integer) r.deltas().get("total_task_value")).isEqualTo(3);
    }

    @Test
    @DisplayName("upgraded: donation adds conservation points beyond task budget")
    void upgradedDonation() {
      setStrengthUpgraded(3);
      // 1 task + donation of 6 → 2 CP
      ActionResult r =
          handler.execute(
              req(Map.of("sub_actions", List.of("RETURN_WORKERS"), "donation_amount", 6)),
              player,
              sharedBoard);

      assertThat(r.success()).isTrue();
      assertThat(player.getMoney()).isEqualTo(14); // 20 - 6
      assertThat(player.getConservation()).isEqualTo(2); // 6 / 3 = 2 CP
      assertThat((Integer) r.deltas().get("conservation_gained")).isEqualTo(2);
    }

    @Test
    @DisplayName("task type names are case-insensitive")
    void caseInsensitiveTaskNames() {
      setStrength(2);
      ActionResult r =
          handler.execute(
              req(Map.of("sub_actions", List.of("return_workers"))), player, sharedBoard);
      assertThat(r.success()).isTrue();
    }
  }
}
