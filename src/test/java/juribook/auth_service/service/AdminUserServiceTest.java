package juribook.auth_service.service;

import juribook.auth_service.dto.response.AdminUserResponse;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de AdminUserService.
 *
 * Aucun appel inter-services à mocker ici, role/enabled/city sont tous
 * des champs locaux de User (cf. commentaire dans User.java : city est
 * dénormalisé côté avocat). Ce service se contente de déléguer au
 * repository et de mapper vers AdminUserResponse.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService")
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private User buildLawyer(Long id, String city, boolean enabled) {
        User u = new User();
        u.setId(id);
        u.setName("Sophie Martin");
        u.setEmail("sophie.martin+" + id + "@example.com");
        u.setPhone("0600000000");
        u.setRole(Role.LAWYER);
        u.setEnabled(enabled);
        u.setBarNumber("7500" + id);
        u.setSpecialty("Droit du travail");
        u.setCity(city);
        u.setLawyerStatus(LawyerStatus.APPROVED);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    private User buildClient(Long id, boolean enabled) {
        User u = new User();
        u.setId(id);
        u.setName("Jean Dupont");
        u.setEmail("jean.dupont+" + id + "@example.com");
        u.setRole(Role.CLIENT);
        u.setEnabled(enabled);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    @Nested
    @DisplayName("searchUsers - délégation et mapping")
    class SearchDelegationAndMapping {

        @Test
        @DisplayName("délègue au repository avec les filtres fournis et mappe vers AdminUserResponse")
        void searchUsers_delegatesWithGivenFilters_mapsToResponse() {
            User lawyer = buildLawyer(10L, "Paris", true);
            Page<User> page = new PageImpl<>(List.of(lawyer));

            when(userRepository.search(eq(Role.LAWYER), eq(true), eq("Paris"), any(Pageable.class)))
                    .thenReturn(page);

            Page<AdminUserResponse> result = adminUserService.searchUsers(Role.LAWYER, true, "Paris", 0, 20);

            assertThat(result.getContent()).hasSize(1);
            AdminUserResponse response = result.getContent().get(0);
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.name()).isEqualTo("Sophie Martin");
            assertThat(response.role()).isEqualTo(Role.LAWYER);
            assertThat(response.city()).isEqualTo("Paris");
            assertThat(response.barNumber()).isEqualTo("750010");
            assertThat(response.lawyerStatus()).isEqualTo(LawyerStatus.APPROVED);

            verify(userRepository).search(eq(Role.LAWYER), eq(true), eq("Paris"), any(Pageable.class));
        }

        @Test
        @DisplayName("mappe correctement un CLIENT (champs avocat null)")
        void searchUsers_mapsClientWithNullLawyerFields() {
            User client = buildClient(42L, true);
            when(userRepository.search(eq(Role.CLIENT), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(client)));

            Page<AdminUserResponse> result = adminUserService.searchUsers(Role.CLIENT, null, null, 0, 20);

            AdminUserResponse response = result.getContent().get(0);
            assertThat(response.role()).isEqualTo(Role.CLIENT);
            assertThat(response.barNumber()).isNull();
            assertThat(response.specialty()).isNull();
            assertThat(response.city()).isNull();
            assertThat(response.lawyerStatus()).isNull();
        }

        @Test
        @DisplayName("transmet les métadonnées de suspension quand le compte est désactivé")
        void searchUsers_mapsSuspensionMetadata() {
            User suspended = buildLawyer(11L, "Lyon", false);
            suspended.setSuspendedReason("Plus de 5 annulations en 7 jours");
            suspended.setSuspendedAt(LocalDateTime.of(2026, 7, 1, 10, 0));

            when(userRepository.search(any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(suspended)));

            Page<AdminUserResponse> result = adminUserService.searchUsers(null, false, null, 0, 20);

            AdminUserResponse response = result.getContent().get(0);
            assertThat(response.enabled()).isFalse();
            assertThat(response.suspendedReason()).isEqualTo("Plus de 5 annulations en 7 jours");
            assertThat(response.suspendedAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 0));
        }
    }

    @Nested
    @DisplayName("searchUsers - filtres optionnels")
    class OptionalFilters {

        @Test
        @DisplayName("tous les filtres null - délègue avec null partout, pas de valeur par défaut inventée")
        void searchUsers_allFiltersNull_passesNullThrough() {
            when(userRepository.search(isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            adminUserService.searchUsers(null, null, null, 0, 20);

            verify(userRepository).search(isNull(), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("filtre role seul - enabled et city restent null")
        void searchUsers_roleOnly_othersStayNull() {
            when(userRepository.search(eq(Role.ADMIN), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            adminUserService.searchUsers(Role.ADMIN, null, null, 0, 20);

            verify(userRepository).search(eq(Role.ADMIN), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("aucun résultat - page vide, pas d'erreur")
        void searchUsers_noResults_returnsEmptyPage() {
            when(userRepository.search(any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<AdminUserResponse> result = adminUserService.searchUsers(Role.LAWYER, true, "Nulle-Part", 0, 20);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("searchUsers - pagination")
    class Pagination {

        @Test
        @DisplayName("transmet page et size tels que fournis quand size <= 50")
        void searchUsers_passesPageAndSize_whenWithinLimit() {
            when(userRepository.search(any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            adminUserService.searchUsers(null, null, null, 2, 30);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).search(any(), any(), any(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(captor.getValue().getPageSize()).isEqualTo(30);
        }

        @Test
        @DisplayName("clampe la taille de page à 50 quand une valeur supérieure est demandée")
        void searchUsers_clampsPageSizeTo50() {
            when(userRepository.search(any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            adminUserService.searchUsers(null, null, null, 0, 500);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).search(any(), any(), any(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("size exactement 50 n'est pas altéré (limite inclusive)")
        void searchUsers_exactlyFifty_notAltered() {
            when(userRepository.search(any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            adminUserService.searchUsers(null, null, null, 0, 50);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).search(any(), any(), any(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        }
    }
}