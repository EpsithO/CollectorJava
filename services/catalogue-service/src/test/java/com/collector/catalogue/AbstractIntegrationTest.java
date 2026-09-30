package com.collector.catalogue;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base des tests d'intégration : application complète contre PostgreSQL et RabbitMQ réels.
 * Ignorés (et non en échec) si Docker n'est pas disponible ; la CI, elle, les exécute.
 */
@SpringBootTest
@ActiveProfiles({"test", "dev"})
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {
}
