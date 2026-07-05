package juribook.auth_service.service;

import juribook.auth_service.dto.request.UpdateLawyerStatusRequest;
import juribook.auth_service.dto.response.LawyerAdminResponse;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.event.LawyerEventPublisher;
import juribook.auth_service.exception.UserNotFoundException;
import juribook.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests d'AdminService - validation/refus des profils avocats.
 *
 * ⚠️ Hypothèses faites en l'absence des DTOs complets :
 *   - UpdateLawyerStatusRequest suit le pattern @Data no-args + setters déjà
 *     utilisé partout ailleurs (LoginRequest, CreateReviewRequest, etc.)
 *   - LawyerAdminResponse est un record (cf. commentaire explicite dans
 *     UserController.java : "Les réponses utilisent des DTOs records")
 * Adapte les noms d'accesseur si ça ne correspond pas exactement.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService")
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSuspensionService userSuspensionService;
    @Mock private LawyerEventPublisher lawyerEventPublisher;

    @InjectMocks
    private AdminService adminService;

    private static final Long LAWYER_ID = 10L;

    private User pendingLawyer;

    @BeforeEach
    void setUp() {
        pendingLawyer = new User();
        pendingLawyer.setId(LAWYER_ID);
        pendingLawyer.setName("Sophie Martin");
        pendingLawyer.setEmail("sophie.martin@example.com");
        pendingLawyer.setRole(Role.LAWYER);
        pendingLawyer.setEnabled(true);
        pendingLawyer.setBarNumber("75001");
        pendingLawyer.setLawyerStatus(LawyerStatus.PENDING);
    }

    private UpdateLawyerStatusRequest buildRequest(LawyerStatus status, String reason) {
        UpdateLawyerStatusRequest request = new UpdateLawyerStatusRequest();
        request.setStatus(status);
        request.setReason(reason);
        return request;
    }

    @Nested
    @DisplayName("updateLawyerStatus - APPROVED")
    class Approve {

        @Test
        @DisplayName("cas nominal - passe APPROVED, réactive via UserSuspensionService, publie lawyer.approved")
        void updateLawyerStatus_approved_reactivatesAndPublishes() {
            when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(pendingLawyer));

            adminService.updateLawyerStatus(LAWYER_ID, buildRequest(LawyerStatus.APPROVED, null));

            assertThat(pendingLawyer.getLawyerStatus()).isEqualTo(LawyerStatus.APPROVED);
            verify(userSuspensionService).reactivateAccount(LAWYER_ID);
            verify(lawyerEventPublisher).publishLawyerApproved(pendingLawyer);
            verify(lawyerEventPublisher, never()).publishLawyerRejected(any(), any());
        }

        @Test
        @DisplayName("avocat précédemment refusé - reactivateAccount efface bien la suspension (délégué, vérifié par l'appel)")
        void updateLawyerStatus_approvedAfterPriorRejection_delegatesReactivation() {
            pendingLawyer.setEnabled(false);
            pendingLawyer.setLawyerStatus(LawyerStatus.REJECTED);
            pendingLawyer.setSuspendedReason("Ancien motif de refus");
            when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(pendingLawyer));

            adminService.updateLawyerStatus(LAWYER_ID, buildRequest(LawyerStatus.APPROVED, null));

            assertThat(pendingLawyer.getLawyerStatus()).isEqualTo(LawyerStatus.APPROVED);
            verify(userSuspensionService).reactivateAccount(LAWYER_ID);
        }
    }

    @Nested
    @DisplayName("updateLawyerStatus - REJECTED")
    class Reject {

        @Test
        @DisplayName("cas nominal - passe REJECTED, délègue à suspendAccount avec le motif fourni, publie lawyer.rejected")
        void updateLawyerStatus_rejected_suspendsWithGivenReasonAndPublishes() {
            when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(pendingLawyer));

            adminService.updateLawyerStatus(LAWYER_ID, buildRequest(LawyerStatus.REJECTED, "Pièces manquantes"));

            assertThat(pendingLawyer.getLawyerStatus()).isEqualTo(LawyerStatus.REJECTED);
            verify(userSuspensionService).suspendAccount(LAWYER_ID, "Pièces manquantes");

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(lawyerEventPublisher).publishLawyerRejected(eq(pendingLawyer), reasonCaptor.capture());
            assertThat(reasonCaptor.getValue()).isEqualTo("Pièces manquantes");
        }

        @Test
        @DisplayName("motif absent - applique le motif par défaut avant délégation")
        void updateLawyerStatus_rejectedNullReason_appliesDefaultReason() {
            when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(pendingLawyer));

            adminService.updateLawyerStatus(LAWYER_ID, buildRequest(LawyerStatus.REJECTED, null));

            verify(userSuspensionService).suspendAccount(LAWYER_ID, "Profil avocat refusé par l'administrateur");
            verify(lawyerEventPublisher).publishLawyerRejected(pendingLawyer, "Profil avocat refusé par l'administrateur");
        }

        @Test
        @DisplayName("motif vide/blanc - applique aussi le motif par défaut")
        void updateLawyerStatus_rejectedBlankReason_appliesDefaultReason() {
            when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(pendingLawyer));

            adminService.updateLawyerStatus(LAWYER_ID, buildRequest(LawyerStatus.REJECTED, "   "));

            verify(userSuspensionService).suspendAccount(LAWYER_ID, "Profil avocat refusé par l'administrateur");
        }

        @Test
        @DisplayName("n'appelle jamais reactivateAccount ni publishLawyerApproved sur un refus")
        void updateLawyerStatus_rejected_neverCallsApprovalPath() {
            when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(pendingLawyer));

            adminService.updateLawyerStatus(LAWYER_ID, buildRequest(LawyerStatus.REJECTED, "motif"));

            verify(userSuspensionService, never()).reactivateAccount(any());
            verify(lawyerEventPublisher, never()).publishLawyerApproved(any());
        }
    }

    @Nested
    @DisplayName("updateLawyerStatus - cas limites")
    class EdgeCases {

        @Test
        @DisplayName("avocat introuvable - lève UserNotFoundException, aucune délégation ni publication")
        void updateLawyerStatus_lawyerNotFound_throwsBeforeAnyDelegation() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.updateLawyerStatus(999L, buildRequest(LawyerStatus.APPROVED, null)))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(userSuspensionService, lawyerEventPublisher);
        }

        @Test
        @DisplayName("id appartenant à un CLIENT (pas un LAWYER) - traité comme introuvable")
        void updateLawyerStatus_idBelongsToClient_treatedAsNotFound() {
            User client = new User();
            client.setId(50L);
            client.setRole(Role.CLIENT);
            when(userRepository.findById(50L)).thenReturn(Optional.of(client));

            assertThatThrownBy(() -> adminService.updateLawyerStatus(50L, buildRequest(LawyerStatus.APPROVED, null)))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(userSuspensionService, lawyerEventPublisher);
        }

        @Test
        @DisplayName("statut PENDING fourni en entrée - rejeté comme invalide (seuls APPROVED/REJECTED sont des actions admin)")
        void updateLawyerStatus_pendingStatusRequested_throwsIllegalArgument() {
            when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(pendingLawyer));

            assertThatThrownBy(() -> adminService.updateLawyerStatus(LAWYER_ID, buildRequest(LawyerStatus.PENDING, null)))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(userSuspensionService, lawyerEventPublisher);
        }

        @Test
        @DisplayName("retourne un LawyerAdminResponse mappé depuis l'entité mise à jour")
        void updateLawyerStatus_returnsNonNullMappedResponse() {
            when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(pendingLawyer));

            LawyerAdminResponse response = adminService.updateLawyerStatus(LAWYER_ID, buildRequest(LawyerStatus.APPROVED, null));

            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("getLawyersByStatus / getAllLawyers / getLawyerById / getStats")
    class ReadOperations {

        @Test
        @DisplayName("getLawyersByStatus - délègue au repository avec le bon statut")
        void getLawyersByStatus_delegatesCorrectly() {
            when(userRepository.findByRoleAndLawyerStatus(Role.LAWYER, LawyerStatus.PENDING))
                    .thenReturn(List.of(pendingLawyer));

            List<LawyerAdminResponse> result = adminService.getLawyersByStatus(LawyerStatus.PENDING);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getLawyerById - lève UserNotFoundException si l'id n'est pas un LAWYER")
        void getLawyerById_notLawyer_throws() {
            User client = new User();
            client.setId(60L);
            client.setRole(Role.CLIENT);
            when(userRepository.findById(60L)).thenReturn(Optional.of(client));

            assertThatThrownBy(() -> adminService.getLawyerById(60L))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("getStats - agrège les 4 comptages attendus")
        void getStats_returnsAllFourCounts() {
            when(userRepository.countByRoleAndLawyerStatus(Role.LAWYER, LawyerStatus.PENDING)).thenReturn(3L);
            when(userRepository.countByRoleAndLawyerStatus(Role.LAWYER, LawyerStatus.APPROVED)).thenReturn(10L);
            when(userRepository.countByRoleAndLawyerStatus(Role.LAWYER, LawyerStatus.REJECTED)).thenReturn(2L);
            when(userRepository.countByRole(Role.CLIENT)).thenReturn(50L);

            var stats = adminService.getStats();

            assertThat(stats)
                    .containsEntry("pending", 3L)
                    .containsEntry("approved", 10L)
                    .containsEntry("rejected", 2L)
                    .containsEntry("clients", 50L);
        }
    }
}