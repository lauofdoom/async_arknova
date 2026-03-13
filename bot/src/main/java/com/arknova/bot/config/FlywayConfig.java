package com.arknova.bot.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Flyway configuration. Spring Boot 4's FlywayAutoConfiguration doesn't reliably
 * initialise the migrator before the EntityManagerFactory, so we own the lifecycle here.
 *
 * <p>Declared as a plain @Bean so that any bean that injects {@code Flyway} (or any bean that
 * Spring resolves after this one) will see a fully-migrated schema. Combined with ddl-auto=none
 * this removes the Hibernate/Flyway ordering race entirely.
 */
@Configuration
public class FlywayConfig {

  private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

  @Bean
  public Flyway flyway(DataSource dataSource) {
    log.info("Running Flyway migrations...");
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .load();
    var result = flyway.migrate();
    log.info(
        "Flyway: {} migration(s) applied, schema now at version {}",
        result.migrationsExecuted,
        result.targetSchemaVersion);
    return flyway;
  }
}
