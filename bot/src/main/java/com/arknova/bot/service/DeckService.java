package com.arknova.bot.service;

import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.CardDefinition.CardType;
import com.arknova.bot.model.Game;
import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerCard.CardLocation;
import com.arknova.bot.model.SharedBoardState;
import com.arknova.bot.repository.CardDefinitionRepository;
import com.arknova.bot.repository.PlayerCardRepository;
import com.arknova.bot.repository.SharedBoardStateRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages all card movement between zones: DECK → DISPLAY → HAND → PLAYED / PLACED / DISCARD.
 *
 * <p>Card location tracking uses the {@link PlayerCard} table. The shared deck ordering is stored
 * in {@link SharedBoardState#getAnimalDeck()} and {@link SharedBoardState#getSponsorDeck()} as
 * ordered arrays of card IDs (index 0 = top of deck).
 *
 * <h2>Ark Nova card flow</h2>
 * <pre>
 * Shuffled Deck → [top] → DISPLAY (6 face-up slots) → player HAND → PLAYED or PLACED
 *                       → HAND (draw from deck)                  → DISCARD
 * </pre>
 *
 * <h2>Display refill rule</h2>
 * When a card is taken from the display, remaining cards shift LEFT (toward slot 1), and a new
 * card is drawn from the deck to fill slot 6.
 */
@Service
@RequiredArgsConstructor
public class DeckService {

  private static final Logger log = LoggerFactory.getLogger(DeckService.class);

  /** Number of face-up slots in the shared card display. */
  public static final int DISPLAY_SIZE = 6;

  private final PlayerCardRepository playerCardRepo;
  private final CardDefinitionRepository cardDefRepo;
  private final SharedBoardStateRepository sharedBoardRepo;

  // ── Game Setup ─────────────────────────────────────────────────────────────

  /**
   * Initialises decks and display for a newly started game. Shuffles all base game cards by type,
   * deals the starting display (6 face-up cards), and creates PlayerCard rows for each player's
   * starting hand (empty at game start — players draw on their first CARDS action).
   *
   * @param game        the game to initialise
   * @param playerIds   ordered list of player Discord IDs (seat order)
   */
  @Transactional
  public void initializeDecks(Game game, List<String> playerIds) {
    UUID gameId = game.getId();

    // Load base game cards by type
    List<String> animalIds = shuffledIds(CardType.ANIMAL);
    List<String> sponsorIds = shuffledIds(CardType.SPONSOR);

    log.info("Game {}: initialising decks — {} animals, {} sponsors",
        gameId, animalIds.size(), sponsorIds.size());

    // Load or create shared board state
    SharedBoardState shared = sharedBoardRepo.findByGameId(gameId)
        .orElseGet(() -> {
          SharedBoardState s = new SharedBoardState();
          s.setGameId(gameId);
          return s;
        });

    // Store shuffled deck order (index 0 = top of deck)
    shared.setAnimalDeck(animalIds.toArray(String[]::new));
    shared.setSponsorDeck(sponsorIds.toArray(String[]::new));

    // Deal initial display: 6 cards from the combined animal+sponsor deck
    // Ark Nova uses a single mixed display from both decks
    List<String> displayIds = new ArrayList<>();
    List<String> remainingAnimals = new ArrayList<>(animalIds);
    List<String> remainingSponsors = new ArrayList<>(sponsorIds);

    // Fill display slots 1-6 alternating animal/sponsor (simplified — real game shuffles together)
    // For MVP: draw from animal deck for first 4 slots, sponsor for last 2
    for (int i = 0; i < 4 && !remainingAnimals.isEmpty(); i++) {
      displayIds.add(remainingAnimals.remove(0));
    }
    for (int i = 0; i < 2 && !remainingSponsors.isEmpty(); i++) {
      displayIds.add(remainingSponsors.remove(0));
    }
    while (displayIds.size() < DISPLAY_SIZE && !remainingAnimals.isEmpty()) {
      displayIds.add(remainingAnimals.remove(0));
    }

    // Persist remaining deck state after dealing display
    shared.setAnimalDeck(remainingAnimals.toArray(String[]::new));
    shared.setSponsorDeck(remainingSponsors.toArray(String[]::new));
    sharedBoardRepo.save(shared);

    // Create DISPLAY PlayerCard rows (not owned by any player — discordId = "DISPLAY")
    for (int slot = 0; slot < displayIds.size(); slot++) {
      PlayerCard pc = new PlayerCard();
      pc.setGameId(gameId);
      pc.setDiscordId("DISPLAY");
      pc.setCard(cardDefRepo.getReferenceById(displayIds.get(slot)));
      pc.setLocation(CardLocation.DISPLAY);
      pc.setSortOrder(slot);
      playerCardRepo.save(pc);
    }

    log.info("Game {}: display filled with {} cards", gameId, displayIds.size());
  }

  // ── CARDS Action ────────────────────────────────────────────────────────────

  /**
   * Draw {@code count} cards from the top of the combined deck into the player's hand.
   *
   * @param gameId     the game
   * @param discordId  the player drawing
   * @param count      number of cards to draw
   * @return list of card IDs drawn (in draw order)
   */
  @Transactional
  public List<String> drawFromDeck(UUID gameId, String discordId, int count) {
    SharedBoardState shared = requireSharedBoard(gameId);

    List<String> animals  = new ArrayList<>(List.of(shared.getAnimalDeck()));
    List<String> sponsors = new ArrayList<>(List.of(shared.getSponsorDeck()));

    // Interleave: draw from animals primarily, then sponsors
    List<String> drawn = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      if (!animals.isEmpty()) {
        drawn.add(animals.remove(0));
      } else if (!sponsors.isEmpty()) {
        drawn.add(sponsors.remove(0));
      } else {
        log.warn("Game {}: deck exhausted after {} draws (requested {})", gameId, drawn.size(), count);
        break;
      }
    }

    // Persist updated deck state
    shared.setAnimalDeck(animals.toArray(String[]::new));
    shared.setSponsorDeck(sponsors.toArray(String[]::new));
    sharedBoardRepo.save(shared);

    // Create HAND PlayerCard rows
    int handSize = playerCardRepo
        .countByGameIdAndDiscordIdAndLocation(gameId, discordId, CardLocation.HAND);

    for (int i = 0; i < drawn.size(); i++) {
      PlayerCard pc = new PlayerCard();
      pc.setGameId(gameId);
      pc.setDiscordId(discordId);
      pc.setCard(cardDefRepo.getReferenceById(drawn.get(i)));
      pc.setLocation(CardLocation.HAND);
      pc.setSortOrder(handSize + i);
      playerCardRepo.save(pc);
    }

    log.debug("Game {}: player {} drew {} cards", gameId, discordId, drawn.size());
    return drawn;
  }

  /**
   * Take a face-up card from the display into the player's hand. Shifts remaining display cards
   * left and refills slot 6 from the deck.
   *
   * @param gameId     the game
   * @param discordId  the player taking the card
   * @param cardId     the card ID to take (must currently be in DISPLAY)
   * @throws IllegalArgumentException if the card is not in the display
   */
  @Transactional
  public void takeFromDisplay(UUID gameId, String discordId, String cardId) {
    // Find the display entry for this card
    List<PlayerCard> display = playerCardRepo
        .findByGameIdAndLocationOrderBySortOrderAsc(gameId, CardLocation.DISPLAY);

    PlayerCard target = display.stream()
        .filter(pc -> pc.getCard().getId().equals(cardId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "Card " + cardId + " is not in the display"));

    // Move card to player's hand
    int handSize = playerCardRepo
        .countByGameIdAndDiscordIdAndLocation(gameId, discordId, CardLocation.HAND);
    target.setDiscordId(discordId);
    target.setLocation(CardLocation.HAND);
    target.setSortOrder(handSize);
    playerCardRepo.save(target);

    // Shift remaining display cards left (fill the gap)
    display.remove(target);
    for (int i = 0; i < display.size(); i++) {
      PlayerCard pc = display.get(i);
      pc.setSortOrder(i);
      playerCardRepo.save(pc);
    }

    // Refill display from deck (draw 1 into slot DISPLAY_SIZE - 1)
    refillDisplay(gameId, display.size());

    log.debug("Game {}: player {} took {} from display", gameId, discordId, cardId);
  }

  /**
   * Discard cards from the player's hand (used for strength-1 CARDS action: draw 2, keep 1).
   *
   * @param gameId    the game
   * @param discordId the player discarding
   * @param cardIds   IDs of the cards to discard
   */
  @Transactional
  public void discardFromHand(UUID gameId, String discordId, List<String> cardIds) {
    if (cardIds.isEmpty()) return;

    List<PlayerCard> hand = playerCardRepo
        .findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
            gameId, discordId, CardLocation.HAND);

    for (String cardId : cardIds) {
      hand.stream()
          .filter(pc -> pc.getCard().getId().equals(cardId))
          .findFirst()
          .ifPresent(pc -> {
            pc.setLocation(CardLocation.DISCARD);
            playerCardRepo.save(pc);
          });
    }
  }

  // ── ANIMALS Action ─────────────────────────────────────────────────────────

  /**
   * Move an animal card from the player's hand to PLACED status (on the zoo board).
   *
   * @param gameId       the game
   * @param discordId    the player placing the animal
   * @param cardId       the animal card ID (must be in player's HAND)
   * @param enclosureId  the enclosure reference (e.g. "E1")
   */
  @Transactional
  public void placeAnimal(UUID gameId, String discordId, String cardId, String enclosureId) {
    PlayerCard card = playerCardRepo
        .findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
            gameId, discordId, CardLocation.HAND)
        .stream()
        .filter(pc -> pc.getCard().getId().equals(cardId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "Card " + cardId + " is not in your hand"));

    card.setLocation(CardLocation.PLACED);
    card.setEnclosureRef(enclosureId);
    playerCardRepo.save(card);
  }

  /**
   * Move a sponsor card from the player's hand to PLAYED (effect resolved, stays in play area).
   */
  @Transactional
  public void playSponsor(UUID gameId, String discordId, String cardId) {
    PlayerCard card = playerCardRepo
        .findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
            gameId, discordId, CardLocation.HAND)
        .stream()
        .filter(pc -> pc.getCard().getId().equals(cardId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "Card " + cardId + " is not in your hand"));

    card.setLocation(CardLocation.PLAYED);
    playerCardRepo.save(card);
  }

  // ── Queries ────────────────────────────────────────────────────────────────

  /** Returns cards currently in the player's hand, ordered. */
  public List<PlayerCard> getHand(UUID gameId, String discordId) {
    return playerCardRepo.findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
        gameId, discordId, CardLocation.HAND);
  }

  /** Returns cards currently in the face-up display, ordered by slot. */
  public List<PlayerCard> getDisplay(UUID gameId) {
    return playerCardRepo.findByGameIdAndLocationOrderBySortOrderAsc(
        gameId, CardLocation.DISPLAY);
  }

  /** Returns how many cards remain in the combined deck. */
  public int deckSize(UUID gameId) {
    SharedBoardState shared = requireSharedBoard(gameId);
    return shared.getAnimalDeck().length + shared.getSponsorDeck().length;
  }

  // ── Internal ───────────────────────────────────────────────────────────────

  private void refillDisplay(UUID gameId, int currentDisplaySize) {
    if (currentDisplaySize >= DISPLAY_SIZE) return;

    SharedBoardState shared = requireSharedBoard(gameId);
    List<String> animals  = new ArrayList<>(List.of(shared.getAnimalDeck()));
    List<String> sponsors = new ArrayList<>(List.of(shared.getSponsorDeck()));

    int needed = DISPLAY_SIZE - currentDisplaySize;
    List<String> refill = new ArrayList<>();

    for (int i = 0; i < needed; i++) {
      if (!animals.isEmpty()) {
        refill.add(animals.remove(0));
      } else if (!sponsors.isEmpty()) {
        refill.add(sponsors.remove(0));
      } else {
        break; // deck exhausted
      }
    }

    shared.setAnimalDeck(animals.toArray(String[]::new));
    shared.setSponsorDeck(sponsors.toArray(String[]::new));
    sharedBoardRepo.save(shared);

    for (int i = 0; i < refill.size(); i++) {
      PlayerCard pc = new PlayerCard();
      pc.setGameId(gameId);
      pc.setDiscordId("DISPLAY");
      pc.setCard(cardDefRepo.getReferenceById(refill.get(i)));
      pc.setLocation(CardLocation.DISPLAY);
      pc.setSortOrder(currentDisplaySize + i);
      playerCardRepo.save(pc);
    }
  }

  private List<String> shuffledIds(CardType type) {
    List<String> ids = cardDefRepo.findByCardTypeAndSource(type, "BASE")
        .stream()
        .map(CardDefinition::getId)
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    Collections.shuffle(ids);
    return ids;
  }

  private SharedBoardState requireSharedBoard(UUID gameId) {
    return sharedBoardRepo.findByGameId(gameId)
        .orElseThrow(() -> new IllegalStateException(
            "No shared board state found for game " + gameId));
  }
}
