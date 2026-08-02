package juribook.auth_service.service;

import juribook.auth_service.dto.response.AdminUserResponse;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.SuspensionSource;
import juribook.auth_service.entity.User;
import juribook.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests AdminUserService pour SuspensionSource, fichier
 * séparé de l'AdminUserServiceTest existant (non fourni).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService - SuspensionSource (Sprint 7.8)")
class AdminUserServiceSourceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSuspensionService userSuspensionService;

    private AdminUserService adminUserService;

    private static final Long USER_ID = 4L;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, userSuspensionService);
    }

    @Nested
    @DisplayName("deactivateUser")
    class DeactivateUser {

        @Test
        @DisplayName("délègue à suspendAccount avec SuspensionSource.MANUAL")
        void deactivateUser_delegatesWithManualSource() {
            User user = new User();
            user.setId(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            adminUserService.deactivateUser(USER_ID, "Comportement inapproprié signalé");

            verify(userSuspensionService).suspendAccount(USER_ID, "Comportement inapproprié signalé", SuspensionSource.MANUAL);
        }

        @Test
        @DisplayName("motif vide - utilise le motif par défaut, toujours MANUAL")
        void deactivateUser_blankReason_usesDefault() {
            User user = new User();
            user.setId(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            adminUserService.deactivateUser(USER_ID, null);

            verify(userSuspensionService).suspendAccount(
                    eq(USER_ID), eq("Désactivé manuellement par un administrateur"), eq(SuspensionSource.MANUAL));
        }
    }

    @Nested
    @DisplayName("searchUsers")
    class SearchUsers {

        @Test
        @DisplayName("transmet suspensionSource au repository")
        void searchUsers_passesSuspensionSourceToRepository() {
            Page<User> page = new PageImpl<>(List.of());
            when(userRepository.search(eq(Role.CLIENT), eq(false), any(), eq(SuspensionSource.ABUSE_DETECTION), any(Pageable.class)))
                    .thenReturn(page);

            Page<AdminUserResponse> result = adminUserService.searchUsers(
                    Role.CLIENT, false, null, SuspensionSource.ABUSE_DETECTION, 0, 20);

            assertThat(result).isNotNull();
            verify(userRepository).search(Role.CLIENT, false, null, SuspensionSource.ABUSE_DETECTION,
                    org.springframework.data.domain.PageRequest.of(0, 20));
        }

        @Test
        @DisplayName("suspensionSource null - transmis tel quel (pas de filtre)")
        void searchUsers_nullSuspensionSource_passesNull() {
            Page<User> page = new PageImpl<>(List.of());
            when(userRepository.search(any(), any(), any(), eq(null), any(Pageable.class))).thenReturn(page);

            adminUserService.searchUsers(null, null, null, null, 0, 20);

            verify(userRepository).search(eq(null), eq(null), eq(null), eq(null), any(Pageable.class));
        }
    }
}