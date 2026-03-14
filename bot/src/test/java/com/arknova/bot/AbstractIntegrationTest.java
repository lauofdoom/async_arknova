package com.arknova.bot;

import net.dv8tion.jda.api.JDA;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for Spring Boot integration tests.
 *
 * <p>Boots the full application context against a real PostgreSQL database (Testcontainers) and
 * suppresses JDA Discord connectivity with a Mockito mock. Flyway migrations run automatically via
 * the custom {@link com.arknova.bot.config.FlywayConfig} bean, giving each test suite a fully
 * migrated schema.
 *
 * <p>The container is declared {@code static} so it is shared across all test classes in the same
 * JVM — Spring's context caching keeps a single {@code ApplicationContext} alive for all
 * subclasses, meaning migrations run only once per test run.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("arknova_test")
          .withUsername("test")
          .withPassword("test");

  /**
   * Replace the real JDA Discord connection with a no-op mock. This prevents {@link
   * com.arknova.bot.config.JdaConfig} from attempting to connect to Discord during context startup.
   */
  @MockitoBean JDA jda;

  @DynamicPropertySource
  static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }
}
