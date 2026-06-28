package juribook.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
 
@SpringBootTest
@TestPropertySource(properties = {
        // Désactive la datasource réelle - pas de PostgreSQL en CI
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        // Flyway ne tourne pas sur H2 (scripts PostgreSQL-specific)
        "spring.flyway.enabled=false",
        // ddl-auto: create-drop pour que Hibernate crée le schéma en mémoire
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Kafka désactivé en CI
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class AuthServiceApplicationTests {
 
    @Test
    void contextLoads() {
        // Vérifie que le contexte Spring démarre sans erreur
        // PostgreSQL et Kafka sont remplacés par des stubs en CI
    }
}