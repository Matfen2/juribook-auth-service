package juribook.auth_service.service;

import juribook.auth_service.dto.request.UpdateLawyerStatusRequest;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.SuspensionSource;
import juribook.auth_service.entity.User;
import juribook.auth_service.event.LawyerEventPublisher;
import juribook.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests AdminService pour SuspensionSource : fichier
 * séparé de l'AdminServiceTest existant (non fourni).
 *
 * ⚠️ UpdateLawyerStatusRequest supposé avec setStatus()/setReason()
 * (mutable, pas un record), pattern cohérent avec les autres DTOs
 * request du projet ; à ajuster si la forme réelle diffère.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService - SuspensionSource (Sprint 7.8)")
class AdminServiceSourceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSuspensionService userSuspensionService;
    @Mock private LawyerEventPublisher lawyerEventPublisher;

    private AdminService adminService;

    private static final Long LAWYER_ID = 4L;
    private User lawyer;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, userSuspensionService, lawyerEventPublisher);

        lawyer = new User();
        lawyer.setId(LAWYER_ID);
        lawyer.setRole(Role.LAWYER);
        lawyer.setEmail("avocat@barreau.fr");
    }

    @Test
    @DisplayName("REJECTED - suspendAccount taggé SuspensionSource.LAWYER_REJECTION")
    void updateLawyerStatus_rejected_usesLawyerRejectionSource() {
        UpdateLawyerStatusRequest request = new UpdateLawyerStatusRequest();
        request.setStatus(LawyerStatus.REJECTED);
        request.setReason("Documents incomplets");

        when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(lawyer));

        adminService.updateLawyerStatus(LAWYER_ID, request);

        verify(userSuspensionService).suspendAccount(LAWYER_ID, "Documents incomplets", SuspensionSource.LAWYER_REJECTION);
    }

    @Test
    @DisplayName("REJECTED sans motif - motif par défaut, toujours LAWYER_REJECTION")
    void updateLawyerStatus_rejectedNoReason_usesDefaultReasonAndLawyerRejectionSource() {
        UpdateLawyerStatusRequest request = new UpdateLawyerStatusRequest();
        request.setStatus(LawyerStatus.REJECTED);

        when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(lawyer));

        adminService.updateLawyerStatus(LAWYER_ID, request);

        verify(userSuspensionService).suspendAccount(
                LAWYER_ID, "Profil avocat refusé par l'administrateur", SuspensionSource.LAWYER_REJECTION);
    }

    @Test
    @DisplayName("APPROVED - ne touche pas à suspendAccount, seulement reactivateAccount")
    void updateLawyerStatus_approved_onlyReactivates() {
        UpdateLawyerStatusRequest request = new UpdateLawyerStatusRequest();
        request.setStatus(LawyerStatus.APPROVED);

        when(userRepository.findById(LAWYER_ID)).thenReturn(Optional.of(lawyer));

        adminService.updateLawyerStatus(LAWYER_ID, request);

        verify(userSuspensionService).reactivateAccount(LAWYER_ID);
        verify(userSuspensionService, org.mockito.Mockito.never())
                .suspendAccount(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}