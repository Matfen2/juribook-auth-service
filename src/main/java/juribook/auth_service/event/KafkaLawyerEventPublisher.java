package juribook.auth_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.auth_service.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publie sur le topic lawyer-events. Utilise ObjectProvider<KafkaTemplate>
 * plutôt qu'une injection directe : en dev, KafkaAutoConfiguration est
 * explicitement exclue (application.yaml), donc aucun bean KafkaTemplate
 * n'existe, getIfAvailable() retourne alors null et la publication est
 * simplement loggée en warning plutôt que de faire planter le contexte
 * Spring au démarrage. Même pattern que les autres services du projet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaLawyerEventPublisher implements LawyerEventPublisher {

    private static final String TOPIC = "lawyer-events";

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void publishLawyerApproved(User user) {
        Map<String, Object> payload = basePayload("lawyer.approved", user);
        publish(payload, user.getId());
    }

    @Override
    public void publishLawyerRejected(User user, String reason) {
        Map<String, Object> payload = basePayload("lawyer.rejected", user);
        payload.put("reason", reason);
        publish(payload, user.getId());
    }

    private Map<String, Object> basePayload(String eventType, User user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        // Cf. javadoc de LawyerEventPublisher : lawyerId ici = User.id
        // (auth-service), pas Lawyer.id (lawyer-service).
        payload.put("lawyerId", user.getId());
        payload.put("email", user.getEmail());
        payload.put("name", user.getName());
        payload.put("barNumber", user.getBarNumber());
        payload.put("occurredAt", LocalDateTime.now().toString());
        return payload;
    }

    private void publish(Map<String, Object> payload, Long userId) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();

        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate indisponible (Kafka désactivé) - événement {} non publié pour userId={}",
                    payload.get("eventType"), userId);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, String.valueOf(userId), json);
            log.info("Événement publié sur {} : {}", TOPIC, payload.get("eventType"));
        } catch (Exception e) {
            log.error("Échec de la publication sur {} pour userId={}", TOPIC, userId, e);
        }
    }
}