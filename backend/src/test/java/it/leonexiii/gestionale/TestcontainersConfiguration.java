package it.leonexiii.gestionale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Un Postgres vero in un container per i test: stesso motore della produzione.
 * Dove Docker non può scaricare immagini, si usa un Postgres già avviato:
 * TEST_DB_ESTERNO=true con DB_URL, DB_USER e DB_PASSWORD.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "test.db.esterno", havingValue = "false", matchIfMissing = true)
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
