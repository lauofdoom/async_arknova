package com.arknova.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arknova.bot.AbstractIntegrationTest;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.Game.GameStatus;
import com.arknova.bot.model.PlayerState;
import com.arknova.bot.repository.PlayerStateRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link GameService} — exercises the full create → join → start → turn cycle
 * against a real PostgreSQL database.
 */
@DisplayName("GameService")
@Transactional
class GameServiceIT extends AbstractIntegrationTest {

  @Autowired GameService gameService;
  @Autowired PlayerStateRepository playerStateRepo;

  private static final String THREAD_ID = "thread-gs-test";
  private static final String GUILD_ID = "guild-001";
  private static final String ALICE_ID = "alice-001";
  private static final String BOB_ID = "bob-002";

  // ── createGame ────────────────────────────────────────────────────────────

  /** Simulates the async channel setup that CreateCommand triggers after createGame. */
  private void setupGameChannel(Game game) {
    gameService.setGameChannel(game.getId(), THREAD_ID);
  }

  @Nested
  @DisplayName("createGame")
  class CreateGame {

    @Test
    @DisplayName("persists game and adds creator as seat 0")
    void createsGameWithCreatorAtSeatZero() {
      Game game = gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice");

      assertThat(game.getId()).isNotNull();
      assertThat(game.getStatus()).isEqualTo(GameStatus.SETUP);
      assertThat(game.getOriginChannelId()).isEqualTo(THREAD_ID);

      List<PlayerState> players = gameService.getPlayersInOrder(game.getId());
      assertThat(players).hasSize(1);
      assertThat(players.get(0).getDiscordId()).isEqualTo(ALICE_ID);
      assertThat(players.get(0).getSeatIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("rejects duplicate game in the same origin channel")
    void rejectsDuplicateGameInSameThread() {
      gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice");

      assertThatThrownBy(() -> gameService.createGame(GUILD_ID, THREAD_ID, BOB_ID, "Bob"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already");
    }
  }

  // ── joinGame ──────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("joinGame")
  class JoinGame {

    @Test
    @DisplayName("adds second player at seat 1")
    void addsSecondPlayer() {
      setupGameChannel(gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice"));

      PlayerState bob = gameService.joinGame(THREAD_ID, BOB_ID, "Bob");

      assertThat(bob.getSeatIndex()).isEqualTo(1);
      assertThat(bob.getDiscordId()).isEqualTo(BOB_ID);
    }

    @Test
    @DisplayName("rejects duplicate join by same player")
    void rejectsDuplicateJoin() {
      setupGameChannel(gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice"));

      assertThatThrownBy(() -> gameService.joinGame(THREAD_ID, ALICE_ID, "Alice"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already joined");
    }

    @Test
    @DisplayName("rejects join once game has started")
    void rejectsJoinAfterStart() {
      setupGameChannel(gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice"));
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
      gameService.startGame(THREAD_ID, ALICE_ID);

      assertThatThrownBy(() -> gameService.joinGame(THREAD_ID, "charlie-003", "Charlie"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already started");
    }
  }

  // ── startGame ─────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("startGame")
  class StartGame {

    @Test
    @DisplayName("transitions game to ACTIVE and initialises player money")
    void startsGameAndInitialisesResources() {
      setupGameChannel(gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice"));
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");

      Game started = gameService.startGame(THREAD_ID, ALICE_ID);

      assertThat(started.getStatus()).isEqualTo(GameStatus.ACTIVE);
      assertThat(started.getCurrentSeat()).isEqualTo(0);
      assertThat(started.getTurnNumber()).isEqualTo(1);
      assertThat(started.getStartedAt()).isNotNull();

      List<PlayerState> players = gameService.getPlayersInOrder(started.getId());
      // Seat 0 (Alice) starts with 25 money; seat 1 (Bob) with 26
      assertThat(players.get(0).getMoney()).isEqualTo(25);
      assertThat(players.get(1).getMoney()).isEqualTo(26);
    }

    @Test
    @DisplayName("rejects start with fewer than 2 players")
    void rejectsStartWithOnePlayer() {
      setupGameChannel(gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice"));

      assertThatThrownBy(() -> gameService.startGame(THREAD_ID, ALICE_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("at least");
    }

    @Test
    @DisplayName("rejects start by non-participant")
    void rejectsStartByNonParticipant() {
      setupGameChannel(gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice"));
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");

      assertThatThrownBy(() -> gameService.startGame(THREAD_ID, "outsider-999"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("participants");
    }
  }

  // ── endTurn ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("endTurn")
  class EndTurn {

    @Test
    @DisplayName("advances seat in round-robin order")
    void advancesSeatRoundRobin() {
      setupGameChannel(gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice"));
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
      Game game = gameService.startGame(THREAD_ID, ALICE_ID);

      // Alice's turn (seat 0)
      assertThat(game.getCurrentSeat()).isEqualTo(0);

      Game afterAlice = gameService.endTurn(THREAD_ID, ALICE_ID);
      assertThat(afterAlice.getCurrentSeat()).isEqualTo(1);
      assertThat(afterAlice.getTurnNumber()).isEqualTo(1);

      // Bob's turn (seat 1) — completing the round increments turn number
      Game afterBob = gameService.endTurn(THREAD_ID, BOB_ID);
      assertThat(afterBob.getCurrentSeat()).isEqualTo(0);
      assertThat(afterBob.getTurnNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("rejects end turn when it is not the player's turn")
    void rejectsOutOfTurnEndTurn() {
      setupGameChannel(gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice"));
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
      gameService.startGame(THREAD_ID, ALICE_ID);

      // Bob tries to end his turn when it is Alice's turn
      assertThatThrownBy(() -> gameService.endTurn(THREAD_ID, BOB_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not your turn");
    }

    @Test
    @DisplayName("rejects end turn on a non-active game")
    void rejectsEndTurnOnSetupGame() {
      setupGameChannel(gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice"));
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
      // game is still in SETUP

      assertThatThrownBy(() -> gameService.endTurn(THREAD_ID, ALICE_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not currently active");
    }
  }

  // ── adjustPlayer ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("adjustPlayer")
  class AdjustPlayer {

    @Test
    @DisplayName("delta-adjusts money correctly")
    void deltaAdjustsMoney() {
      Game game = gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice");
      setupGameChannel(game);
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
      gameService.startGame(THREAD_ID, ALICE_ID);

      PlayerState updated = gameService.adjustPlayer(game.getId(), ALICE_ID, 10, null, 0, 0);
      assertThat(updated.getMoney()).isEqualTo(35); // 25 + 10
    }

    @Test
    @DisplayName("set-adjusts money correctly")
    void setAdjustsMoney() {
      Game game = gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice");
      setupGameChannel(game);
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
      gameService.startGame(THREAD_ID, ALICE_ID);

      PlayerState updated = gameService.adjustPlayer(game.getId(), ALICE_ID, null, 50, 0, 0);
      assertThat(updated.getMoney()).isEqualTo(50);
    }

    @Test
    @DisplayName("rejects negative money set")
    void rejectsNegativeMoneySet() {
      Game game = gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice");
      setupGameChannel(game);
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
      gameService.startGame(THREAD_ID, ALICE_ID);

      assertThatThrownBy(() -> gameService.adjustPlayer(game.getId(), ALICE_ID, null, -5, 0, 0))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("negative");
    }
  }

  // ── adjustTrack ───────────────────────────────────────────────────────────

  @Nested
  @DisplayName("adjustTrack")
  class AdjustTrack {

    @Test
    @DisplayName("increments appeal correctly")
    void incrementsAppeal() {
      Game game = gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice");
      setupGameChannel(game);
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
      gameService.startGame(THREAD_ID, ALICE_ID);

      PlayerState updated = gameService.adjustTrack(game.getId(), ALICE_ID, "appeal", null, 5);
      assertThat(updated.getAppeal()).isEqualTo(5);
    }

    @Test
    @DisplayName("rejects unknown track name")
    void rejectsUnknownTrack() {
      Game game = gameService.createGame(GUILD_ID, THREAD_ID, ALICE_ID, "Alice");
      setupGameChannel(game);
      gameService.joinGame(THREAD_ID, BOB_ID, "Bob");
      gameService.startGame(THREAD_ID, ALICE_ID);

      assertThatThrownBy(
              () -> gameService.adjustTrack(game.getId(), ALICE_ID, "invalid_track", null, 1))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Unknown track");
    }
  }
}
