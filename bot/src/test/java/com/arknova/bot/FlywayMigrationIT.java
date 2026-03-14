package com.arknova.bot;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies that every Flyway migration (V1–V7) applies cleanly against a real PostgreSQL database.
 *
 * <p>This test catches: missing migration files, invalid SQL syntax, column/table name mismatches,
 * and out-of-order migration versions before they can reach production.
 */
@DisplayName("Flyway migrations")
class FlywayMigrationIT extends AbstractIntegrationTest {

  @Autowired Flyway flyway;

  @Test
  @DisplayName("all migrations apply successfully")
  void allMigrationsApplied() {
    MigrationInfo[] applied = flyway.info().applied();

    assertThat(applied)
        .as("expected 7 applied migrations (V1–V7)")
        .hasSize(7);

    for (MigrationInfo migration : applied) {
      assertThat(migration.getState())
          .as("migration %s should be SUCCESS", migration.getVersion())
          .isEqualTo(MigrationState.SUCCESS);
    }
  }

  @Test
  @DisplayName("schema is at version 7")
  void schemaVersionIsLatest() {
    MigrationInfo current = flyway.info().current();

    assertThat(current).isNotNull();
    assertThat(current.getVersion().getVersion()).isEqualTo("7");
    assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);
  }

  @Test
  @DisplayName("no pending migrations remain")
  void noPendingMigrations() {
    MigrationInfo[] pending = flyway.info().pending();

    assertThat(pending)
        .as("all migrations should be applied — none should be pending")
        .isEmpty();
  }
}
