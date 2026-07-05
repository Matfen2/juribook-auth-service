package juribook.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.auth_service.config.SecurityConfig;
import juribook.auth_service.dto.response.AdminUserResponse;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.exception.UserNotFoundException;
import juribook.auth_service.repository.UserRepository;
import juribook.auth_service.security.JwtService;
import juribook.auth_service.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration Web pour AdminUserController.
 *
 * ⚠️ @Import(SecurityConfig.class) est nécessaire : @WebMvcTest ne scanne
 * pas les classes @Configuration classiques par défaut, donc sans cet
 * import, aucune règle hasRole n'est appliquée (tout passe en 200).
 *
 * ⚠️ Authentification via SecurityMockMvcRequestPostProcessors.user(...)
 * plutôt que @WithMockUser : une fois la vraie SecurityFilterChain
 * chargée (via l'import ci-dessus), le contexte posé par @WithMockUser
 * ne survivait pas jusqu'à la vérification hasRole dans ce projet (tout
 * retombait en 403, y compris les requêtes censées être ADMIN) — cause
 * exacte non confirmée avec certitude, mais .with(user(...).roles(...))
 * attache l'authentification directement à la requête simulée et
 * fonctionne de façon fiable indépendamment de cette subtilité.
 *
 * ⚠️ 403 (pas 401) pour une requête non authentifiée : SecurityConfig ne
 * déclare aucun AuthenticationEntryPoint personnalisé (pas de httpBasic/
 * formLogin), donc Spring Security retombe sur son comportement par
 * défaut pour une API stateless — Http403ForbiddenEntryPoint, qui renvoie
 * 403 aussi bien pour "pas authentifié" que pour "rôle insuffisant".
 */
@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
@DisplayName("AdminUserController - Tests d'intégration Web")
class AdminUserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminUserService adminUserService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin@test.com").roles("ADMIN");
    }

    private static RequestPostProcessor client() {
        return SecurityMockMvcRequestPostProcessors.user("client@test.com").roles("CLIENT");
    }

    private static RequestPostProcessor lawyer() {
        return SecurityMockMvcRequestPostProcessors.user("lawyer@test.com").roles("LAWYER");
    }

    private AdminUserResponse buildLawyerResponse() {
        return new AdminUserResponse(
                10L, "Sophie Martin", "sophie.martin@example.com", "0600000000",
                Role.LAWYER, true, "75001", "Droit du travail", "Paris",
                LawyerStatus.APPROVED, null, null, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("Sécurité par rôle")
    class RoleSecurity {

        @Test
        @DisplayName("❌ 403 - sans authentification (pas d'AuthenticationEntryPoint custom → 403, pas 401)")
        void search_noAuthentication_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(adminUserService);
        }

        @Test
        @DisplayName("❌ 403 - authentifié en CLIENT, refusé")
        void search_clientRole_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users").with(client()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(adminUserService);
        }

        @Test
        @DisplayName("❌ 403 - authentifié en LAWYER, refusé")
        void search_lawyerRole_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users").with(lawyer()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(adminUserService);
        }

        @Test
        @DisplayName("✅ 200 - authentifié en ADMIN, autorisé")
        void search_adminRole_returns200() throws Exception {
            when(adminUserService.searchUsers(any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/admin/users").with(admin()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/users — filtres et pagination")
    class SearchEndpoint {

        @Test
        @DisplayName("retourne 200 avec la page retournée par le service")
        void search_delegatesToServiceAndReturns200WithBody() throws Exception {
            Page<AdminUserResponse> page = new PageImpl<>(
                    List.of(buildLawyerResponse()), PageRequest.of(0, 20), 1);
            when(adminUserService.searchUsers(Role.LAWYER, true, "Paris", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/admin/users")
                            .with(admin())
                            .param("role", "LAWYER")
                            .param("enabled", "true")
                            .param("city", "Paris"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].name").value("Sophie Martin"))
                    .andExpect(jsonPath("$.content[0].city").value("Paris"))
                    .andExpect(jsonPath("$.content[0].role").value("LAWYER"));

            verify(adminUserService).searchUsers(Role.LAWYER, true, "Paris", 0, 20);
        }

        @Test
        @DisplayName("aucun filtre fourni - délègue avec null partout et page=0/size=20 par défaut")
        void search_noFilters_defaultsPageAndSizePassNullFilters() throws Exception {
            when(adminUserService.searchUsers(isNull(), isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/admin/users").with(admin()))
                    .andExpect(status().isOk());

            verify(adminUserService).searchUsers(isNull(), isNull(), isNull(), eq(0), eq(20));
        }

        @Test
        @DisplayName("transmet page et size explicites")
        void search_explicitPageAndSize_passedThrough() throws Exception {
            when(adminUserService.searchUsers(any(), any(), any(), eq(2), eq(10)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/admin/users")
                            .with(admin())
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk());

            verify(adminUserService).searchUsers(isNull(), isNull(), isNull(), eq(2), eq(10));
        }

        @Test
        @DisplayName("filtre enabled=false seul (comptes suspendus)")
        void search_enabledFalseOnly_passedThrough() throws Exception {
            when(adminUserService.searchUsers(isNull(), eq(false), isNull(), eq(0), eq(20)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/admin/users").with(admin()).param("enabled", "false"))
                    .andExpect(status().isOk());

            verify(adminUserService).searchUsers(isNull(), eq(false), isNull(), eq(0), eq(20));
        }

        @Test
        @DisplayName("500 - role invalide (valeur hors de l'enum) — comportement actuel du GlobalExceptionHandler, pas un 400 idéal")
        void search_invalidRoleValue_returns500() throws Exception {
            mockMvc.perform(get("/api/admin/users").with(admin()).param("role", "NOT_A_ROLE"))
                    .andExpect(status().isInternalServerError());

            verifyNoInteractions(adminUserService);
        }

        @Test
        @DisplayName("retourne 200 avec une page vide quand aucun résultat")
        void search_noResults_returns200WithEmptyPage() throws Exception {
            when(adminUserService.searchUsers(any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/admin/users").with(admin()).param("city", "Nulle-Part"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/deactivate")
    class DeactivateEndpoint {

        @Test
        @DisplayName("❌ 403 - refusé pour un CLIENT")
        void deactivate_clientRole_returns403() throws Exception {
            mockMvc.perform(patch("/api/admin/users/10/deactivate").with(client()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(adminUserService);
        }

        @Test
        @DisplayName("✅ 200 - ADMIN, motif fourni dans le corps, délègue tel quel")
        void deactivate_withReason_delegatesReasonAsIs() throws Exception {
            when(adminUserService.deactivateUser(eq(10L), eq("Comportement abusif signalé")))
                    .thenReturn(buildLawyerResponse());

            mockMvc.perform(patch("/api/admin/users/10/deactivate")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                                put("reason", "Comportement abusif signalé");
                            }})))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));

            verify(adminUserService).deactivateUser(10L, "Comportement abusif signalé");
        }

        @Test
        @DisplayName("✅ 200 - ADMIN, corps absent, transmet reason=null au service")
        void deactivate_noBody_passesNullReason() throws Exception {
            when(adminUserService.deactivateUser(eq(10L), isNull())).thenReturn(buildLawyerResponse());

            mockMvc.perform(patch("/api/admin/users/10/deactivate").with(admin()))
                    .andExpect(status().isOk());

            verify(adminUserService).deactivateUser(10L, null);
        }

        @Test
        @DisplayName("❌ 404 - utilisateur introuvable")
        void deactivate_userNotFound_returns404() throws Exception {
            when(adminUserService.deactivateUser(eq(999L), any()))
                    .thenThrow(new UserNotFoundException("Utilisateur introuvable : id=999"));

            mockMvc.perform(patch("/api/admin/users/999/deactivate").with(admin()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/activate")
    class ActivateEndpoint {

        @Test
        @DisplayName("❌ 403 - refusé pour un LAWYER")
        void activate_lawyerRole_returns403() throws Exception {
            mockMvc.perform(patch("/api/admin/users/10/activate").with(lawyer()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(adminUserService);
        }

        @Test
        @DisplayName("✅ 200 - ADMIN, délègue à activateUser")
        void activate_adminRole_delegatesAndReturns200() throws Exception {
            when(adminUserService.activateUser(10L)).thenReturn(buildLawyerResponse());

            mockMvc.perform(patch("/api/admin/users/10/activate").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));

            verify(adminUserService).activateUser(10L);
        }

        @Test
        @DisplayName("❌ 404 - utilisateur introuvable")
        void activate_userNotFound_returns404() throws Exception {
            when(adminUserService.activateUser(999L))
                    .thenThrow(new UserNotFoundException("Utilisateur introuvable : id=999"));

            mockMvc.perform(patch("/api/admin/users/999/activate").with(admin()))
                    .andExpect(status().isNotFound());
        }
    }
}