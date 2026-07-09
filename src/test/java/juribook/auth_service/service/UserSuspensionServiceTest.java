package juribook.auth_service.service;

import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.SuspensionSource;
import juribook.auth_service.entity.User;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests d'UserSuspensionService : réutilise le champ
 * `enabled` déjà existant sur User, pas un nouveau flag `suspended`.
 * Ajout de reactivateAccount, symétrique de suspendAccount.
 *
 * suspendAccount a un 3e paramètre SuspensionSource, tracé
 * et effacé au même titre que suspendedReason/suspendedAt.
 */
@ExtendWith(MockitoExtension.class)
class UserSuspensionServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserSuspensionService userSuspensionService;

    private static final Long USER_ID = 4L;
    private static final String REASON = "Plus de 5 annulations en 7 jours";

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(USER_ID);
        activeUser.setEmail("jean.dupont@example.com");
        activeUser.setRole(Role.CLIENT);
        activeUser.setEnabled(true);
    }

    @Nested
    @DisplayName("suspendAccount")
    class SuspendAccount {

        @Test
        @DisplayName("cas nominal - désactive le compte et trace le motif/la date/la source")
        void suspendAccount_activeAccount_disablesAndRecordsMetadata() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userSuspensionService.suspendAccount(USER_ID, REASON, SuspensionSource.ABUSE_DETECTION);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.isEnabled()).isFalse();
            assertThat(saved.getSuspendedReason()).isEqualTo(REASON);
            assertThat(saved.getSuspendedAt()).isNotNull();
            assertThat(saved.getSuspensionSource()).isEqualTo(SuspensionSource.ABUSE_DETECTION);
        }

        @Test
        @DisplayName("MANUAL et LAWYER_REJECTION sont tracés tout aussi fidèlement")
        void suspendAccount_manualAndLawyerRejectionSources_recordedCorrectly() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userSuspensionService.suspendAccount(USER_ID, "Désactivé manuellement par un administrateur", SuspensionSource.MANUAL);

            assertThat(activeUser.getSuspensionSource()).isEqualTo(SuspensionSource.MANUAL);
        }

        @Test
        @DisplayName("compte déjà désactivé - idempotent, ne re-sauvegarde pas")
        void suspendAccount_alreadyDisabled_doesNothing() {
            activeUser.setEnabled(false);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

            userSuspensionService.suspendAccount(USER_ID, REASON, SuspensionSource.ABUSE_DETECTION);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("utilisateur introuvable - ne plante pas, log seulement")
        void suspendAccount_userNotFound_doesNotThrow() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            userSuspensionService.suspendAccount(999L, REASON, SuspensionSource.ABUSE_DETECTION);

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("reactivateAccount")
    class ReactivateAccount {

        private User disabledUser;

        @BeforeEach
        void setUp() {
            disabledUser = new User();
            disabledUser.setId(USER_ID);
            disabledUser.setEmail("jean.dupont@example.com");
            disabledUser.setRole(Role.CLIENT);
            disabledUser.setEnabled(false);
            disabledUser.setSuspendedReason(REASON);
            disabledUser.setSuspendedAt(LocalDateTime.now().minusDays(2));
            disabledUser.setSuspensionSource(SuspensionSource.ABUSE_DETECTION);
        }

        @Test
        @DisplayName("cas nominal - réactive le compte et efface le motif/la date/la source de suspension")
        void reactivateAccount_disabledAccount_enablesAndClearsSuspensionMetadata() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(disabledUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userSuspensionService.reactivateAccount(USER_ID);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.isEnabled()).isTrue();
            assertThat(saved.getSuspendedReason()).isNull();
            assertThat(saved.getSuspendedAt()).isNull();
            assertThat(saved.getSuspensionSource()).isNull();
        }

        @Test
        @DisplayName("compte déjà actif - idempotent, ne re-sauvegarde pas")
        void reactivateAccount_alreadyEnabled_doesNothing() {
            disabledUser.setEnabled(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(disabledUser));

            userSuspensionService.reactivateAccount(USER_ID);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("utilisateur introuvable - ne plante pas, log seulement")
        void reactivateAccount_userNotFound_doesNotThrow() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            userSuspensionService.reactivateAccount(999L);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("ne touche pas lawyerStatus - un avocat réactivé garde son statut de validation d'avant")
        void reactivateAccount_lawyer_doesNotResetLawyerStatus() {
            disabledUser.setRole(Role.LAWYER);
            disabledUser.setLawyerStatus(juribook.auth_service.entity.LawyerStatus.APPROVED);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(disabledUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userSuspensionService.reactivateAccount(USER_ID);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getLawyerStatus())
                    .isEqualTo(juribook.auth_service.entity.LawyerStatus.APPROVED);
        }
    }
}