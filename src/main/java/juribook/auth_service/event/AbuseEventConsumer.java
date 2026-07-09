package juribook.auth_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.auth_service.entity.SuspensionSource;
import juribook.auth_service.service.UserSuspensionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Réaction automatique à abuse.detected : premier
 * consumer Kafka d'auth-service, qui n'a jamais rien consommé ni
 * publié jusqu'ici.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbuseEventConsumer {

    private final ObjectMapper objectMapper;
    private final UserSuspensionService userSuspensionService;

    @KafkaListener(topics = "abuse-events", groupId = "${spring.kafka.consumer.group-id}")
    public void onAbuseEvent(String payload) {
        AbuseEvent event;
        try {
            event = objectMapper.readValue(payload, AbuseEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Impossible de désérialiser un message du topic abuse-events : {}", payload, e);
            return;
        }

        if (!"abuse.detected".equals(event.eventType())) {
            return;
        }

        userSuspensionService.suspendAccount(event.actorId(), event.reason(), SuspensionSource.ABUSE_DETECTION);
    }
}