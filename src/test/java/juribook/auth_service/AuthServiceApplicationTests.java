package juribook.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Test de démarrage du contexte Spring.
 *
 * Vérifie que l'ApplicationContext se charge sans erreur.
 * Utilise H2 en mémoire (@ActiveProfiles("test")) pour éviter
 * de dépendre de PostgreSQL en CI/CD.
 *
 * ⚠️ Ce test ne vérifie pas la logique métier — il s'assure
 * uniquement que tous les beans Spring s'initialisent correctement.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    // Désactiver Kafka auto-connect en test
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
        // Si le contexte démarre sans exception, le test passe
    }
}