package juribook.auth_service.event;
 
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
 
import java.time.LocalDateTime;
 
/**
 * Miroir de AbuseEvent (audit-service, sous-package abuse).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AbuseEvent(
    String eventType,
    Long actorId,
    String reason,
    long signalCount,
    LocalDateTime occurredAt
) {
}