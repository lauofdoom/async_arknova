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

  @Modifying
  @Query("UPDATE PlayerCard pc SET pc.location = :newLocation WHERE pc.id = :id")
  void moveCard(@Param("id") Long id, @Param("newLocation") CardLocation newLocation);
}
