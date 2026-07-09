package juribook.auth_service.service;

import juribook.auth_service.entity.SuspensionSource;
import juribook.auth_service.entity.User;
import juribook.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de UserSuspensionService pour SuspensionSource,
 * fichier séparé de l'UserSuspensionServiceTest existant (non fourni),
 * pour ne pas risquer de dupliquer/écraser ses mocks à l'aveugle.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserSuspensionService - SuspensionSource (Sprint 7.8)")
class UserSuspensionServiceSourceTest {

    @Mock
    private UserRepository userRepository;

    private UserSuspensionService userSuspensionService;

    private static final Long USER_ID = 4L;
    private User user;

    @BeforeEach
    void setUp() {
        userSuspensionService = new UserSuspensionService(userRepository);

        user = new User();
        user.setId(USER_ID);
        user.setEmail("test@juribook.fr");
        user.setEnabled(true);
    }

    @Test
    @DisplayName("suspendAccount - trace la source fournie")
    void suspendAccount_storesGivenSource() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userSuspensionService.suspendAccount(USER_ID, "Plus de 5 annulations en 7 jours", SuspensionSource.ABUSE_DETECTION);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getSuspensionSource()).isEqualTo(SuspensionSource.ABUSE_DETECTION);
    }

    @Test
    @DisplayName("suspendAccount - MANUAL et LAWYER_REJECTION également tracés correctement")
    void suspendAccount_manualAndLawyerRejection_storedCorrectly() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userSuspensionService.suspendAccount(USER_ID, "Désactivé manuellement par un administrateur", SuspensionSource.MANUAL);

        assertThat(user.getSuspensionSource()).isEqualTo(SuspensionSource.MANUAL);
    }

    @Test
    @DisplayName("reactivateAccount - efface suspensionSource en plus de reason/suspendedAt")
    void reactivateAccount_clearsSuspensionSource() {
        user.setEnabled(false);
        user.setSuspendedReason("Plus de 3 avis 1-étoile en 24h");
        user.setSuspensionSource(SuspensionSource.ABUSE_DETECTION);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userSuspensionService.reactivateAccount(USER_ID);

        assertThat(user.getSuspensionSource()).isNull();
        assertThat(user.getSuspendedReason()).isNull();
        assertThat(user.getSuspendedAt()).isNull();
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("suspendAccount - utilisateur introuvable, échoue silencieusement (comportement Kafka préservé)")
    void suspendAccount_userNotFound_noThrow() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        userSuspensionService.suspendAccount(999L, "raison", SuspensionSource.ABUSE_DETECTION);

        verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}