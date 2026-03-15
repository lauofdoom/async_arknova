package com.arknova.bot.repository;

import com.arknova.bot.model.PlayerCard;
import com.arknova.bot.model.PlayerCard.CardLocation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerCardRepository extends JpaRepository<PlayerCard, Long> {

  List<PlayerCard> findByGameIdAndDiscordIdAndLocationOrderBySortOrderAsc(
      UUID gameId, String discordId, CardLocation location);

  List<PlayerCard> findByGameIdAndLocationOrderBySortOrderAsc(UUID gameId, CardLocation location);

  int countByGameIdAndDiscordIdAndLocation(UUID gameId, String discordId, CardLocation location);

  List<PlayerCard> findByGameIdAndDiscordIdAndLocationOrderBySortOrderDesc(
      UUID gameId, String discordId, CardLocation location);

  @Query(
      "SELECT COALESCE(MAX(pc.sortOrder), -1) FROM PlayerCard pc"
          + " WHERE pc.gameId = :gameId AND pc.discordId = :discordId AND pc.location = :location")
  int maxSortOrderInLocation(
      @Param("gameId") UUID gameId,
      @Param("discordId") String discordId,
      @Param("location") CardLocation location);

  @Modifying
  @Query("UPDATE PlayerCard pc SET pc.location = :newLocation WHERE pc.id = :id")
  void moveCard(@Param("id") Long id, @Param("newLocation") CardLocation newLocation);

  /**
   * Returns card IDs for all cards in the given location, avoiding lazy-loading the card entity.
   */
  @Query(
      "SELECT pc.card.id FROM PlayerCard pc"
          + " WHERE pc.gameId = :gameId AND pc.discordId = :discordId AND pc.location = :location")
  List<String> findCardIdsByGameIdAndDiscordIdAndLocation(
      @Param("gameId") UUID gameId,
      @Param("discordId") String discordId,
      @Param("location") CardLocation location);
}
