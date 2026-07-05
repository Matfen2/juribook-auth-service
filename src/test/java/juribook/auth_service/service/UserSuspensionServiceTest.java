package juribook.auth_service.service;

import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests d'UserSuspensionService : réutilise le champ
 * `enabled` déjà existant sur User, pas un nouveau flag `suspended`.
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

    @Test
    @DisplayName("cas nominal - désactive le compte et trace le motif/la date")
    void suspendAccount_activeAccount_disablesAndRecordsMetadata() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userSuspensionService.suspendAccount(USER_ID, REASON);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.getSuspendedReason()).isEqualTo(REASON);
        assertThat(saved.getSuspendedAt()).isNotNull();
    }

    @Test
    @DisplayName("compte déjà désactivé - idempotent, ne re-sauvegarde pas")
    void suspendAccount_alreadyDisabled_doesNothing() {
        activeUser.setEnabled(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

        userSuspensionService.suspendAccount(USER_ID, REASON);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("utilisateur introuvable - ne plante pas, log seulement")
    void suspendAccount_userNotFound_doesNotThrow() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        userSuspensionService.suspendAccount(999L, REASON);

        verify(userRepository, never()).save(any());
    }
}