package juribook.auth_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests de KafkaLawyerEventPublisher.
 *
 * Le point critique : ce service doit fonctionner (sans planter) même
 * quand KafkaTemplate n'existe pas dans le contexte Spring, cas réel
 * en dev local où KafkaAutoConfiguration est exclue (application.yaml).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaLawyerEventPublisher")
class KafkaLawyerEventPublisherTest {

    @Mock
    private ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private KafkaLawyerEventPublisher publisher;

    private User lawyer;

    @BeforeEach
    void setUp() {
        publisher = new KafkaLawyerEventPublisher(kafkaTemplateProvider, objectMapper);

        lawyer = new User();
        lawyer.setId(10L);
        lawyer.setName("Sophie Martin");
        lawyer.setEmail("sophie.martin@example.com");
        lawyer.setRole(Role.LAWYER);
        lawyer.setBarNumber("75001");
    }

    @Test
    @DisplayName("KafkaTemplate disponible - publie sur lawyer-events avec eventType=lawyer.approved")
    void publishLawyerApproved_whenKafkaAvailable_sendsToLawyerEventsTopic() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);

        publisher.publishLawyerApproved(lawyer);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("lawyer-events");
        assertThat(keyCaptor.getValue()).isEqualTo("10");
        assertThat(payloadCaptor.getValue())
                .contains("\"eventType\":\"lawyer.approved\"")
                .contains("\"lawyerId\":10")
                .contains("sophie.martin@example.com");
    }

    @Test
    @DisplayName("KafkaTemplate disponible - lawyer.rejected inclut le motif dans le payload")
    void publishLawyerRejected_whenKafkaAvailable_includesReasonInPayload() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);

        publisher.publishLawyerRejected(lawyer, "Pièces manquantes");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("lawyer-events"), eq("10"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue())
                .contains("\"eventType\":\"lawyer.rejected\"")
                .contains("\"reason\":\"Pièces manquantes\"");
    }

    @Test
    @DisplayName("KafkaTemplate indisponible (Kafka désactivé en dev) - ne plante pas, log seulement")
    void publishLawyerApproved_whenKafkaUnavailable_doesNotThrow() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> publisher.publishLawyerApproved(lawyer)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("KafkaTemplate indisponible - publishLawyerRejected ne plante pas non plus")
    void publishLawyerRejected_whenKafkaUnavailable_doesNotThrow() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> publisher.publishLawyerRejected(lawyer, "motif"))
                .doesNotThrowAnyException();
    }
}