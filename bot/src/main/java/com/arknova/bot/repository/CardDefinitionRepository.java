package com.arknova.bot.repository;

import com.arknova.bot.model.CardDefinition;
import com.arknova.bot.model.CardDefinition.CardType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CardDefinitionRepository extends JpaRepository<CardDefinition, String> {

  List<CardDefinition> findByCardType(CardType cardType);

  List<CardDefinition> findByCardTypeAndSource(CardType cardType, String source);

  /** Cards with no effect_code — not yet fully automated. */
  @Query("SELECT c FROM CardDefinition c WHERE c.effectCode IS NULL")
  List<CardDefinition> findUnimplementedCards();

  /** Full-text search across name and ability text. */
  @Query(
      value = "SELECT * FROM card_definitions WHERE search_vec @@ plainto_tsquery('english', :q)",
      nativeQuery = true)
  List<CardDefinition> fullTextSearch(@Param("q") String query);

  long countByEffectCodeIsNotNull();

  long count();
}
