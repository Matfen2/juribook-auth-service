package juribook.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.auth_service.dto.request.LoginRequest;
import juribook.auth_service.dto.request.RegisterClientRequest;
import juribook.auth_service.dto.response.LoginResponse;
import juribook.auth_service.dto.response.RegisterClientResponse;
import juribook.auth_service.entity.Role;
import juribook.auth_service.exception.UserNotFoundException;
import juribook.auth_service.repository.UserRepository;
import juribook.auth_service.security.JwtService;
import juribook.auth_service.service.AuthService;
import juribook.auth_service.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration Web pour AuthController.
 *
 * Ce qu'on teste ici :
 *   ✅ Les codes HTTP retournés (201, 400, 404, 409...)
 *   ✅ Le contenu JSON des réponses
 *   ✅ La validation des champs (@NotBlank, @Email, @Size)
 *   ✅ Le logout avec utilisateur authentifié via @WithMockUser
 */
@WebMvcTest(AuthController.class)
@DisplayName("AuthController - Tests d'intégration Web")
class AuthControllerTest {

    /**
     * MockMvc : simule des requêtes HTTP sans démarrer un vrai serveur.
     * Toujours disponible dans @WebMvcTest.
     */
    @Autowired MockMvc mockMvc;

    /**
     * ObjectMapper instancié manuellement.
     * Spring Boot 4 ne l'auto-configure plus dans @WebMvcTest.
     */
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ── Mocks des services métier ────────────────────────────
    @MockitoBean AuthService authService;
    @MockitoBean RefreshTokenService refreshTokenService;

    // ── Mocks des dépendances du filtre JWT ──────────────────
    // JwtAuthenticationFilter est chargé par @WebMvcTest car il fait
    // partie de la chaîne Spring Security. Il dépend de JwtService
    // et UserRepository qui ne sont pas disponibles dans ce contexte.
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    // ═══════════════════════════════════════════════════════════
    //  POST /api/auth/register
    //
    //  Cas couverts :
    //    ✅ 201 - données valides → compte créé
    //    ❌ 400 - email manquant (@NotBlank)
    //    ❌ 400 - mot de passe trop court (@Size min=8)
    //    ❌ 409 - email déjà utilisé (règle métier)
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterClientEndpointTests {

        @Test
        @DisplayName("✅ 201 - inscription client avec données valides")
        void register_validRequest_returns201() throws Exception {
            RegisterClientRequest request = new RegisterClientRequest();
            request.setName("Jean Dupont");
            request.setEmail("jean@test.com");
            request.setPassword("motdepasse123");

            RegisterClientResponse response = RegisterClientResponse.builder()
                    .id(1L).name("Jean Dupont").email("jean@test.com")
                    .role(Role.CLIENT).message("Inscription réussie").build();
            when(authService.registerClient(any())).thenReturn(response);

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("CLIENT"))
                    .andExpect(jsonPath("$.message").value("Inscription réussie"));
        }

        @Test
        @DisplayName("❌ 400 - email manquant (Spring Validation bloque avant d'appeler le service)")
        void register_missingEmail_returns400() throws Exception {
            RegisterClientRequest request = new RegisterClientRequest();
            request.setName("Jean Dupont");
            // email absent → @NotBlank échoue
            request.setPassword("motdepasse123");

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            // Spring Validation intercepte AVANT AuthService
            verify(authService, never()).registerClient(any());
        }

        @Test
        @DisplayName("❌ 400 - mot de passe trop court (@Size min = 8 caractères)")
        void register_shortPassword_returns400() throws Exception {
            RegisterClientRequest request = new RegisterClientRequest();
            request.setName("Jean Dupont");
            request.setEmail("jean@test.com");
            request.setPassword("court"); // 5 chars → @Size(min=8) échoue

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("❌ 409 - email déjà utilisé (GlobalExceptionHandler → 409 Conflict)")
        void register_duplicateEmail_returns409() throws Exception {
            RegisterClientRequest request = new RegisterClientRequest();
            request.setName("Jean Dupont");
            request.setEmail("jean@test.com");
            request.setPassword("motdepasse123");

            when(authService.registerClient(any()))
                    .thenThrow(new IllegalArgumentException("Un compte existe déjà avec cet email"));

            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  POST /api/auth/login
    //
    //  Cas couverts :
    //    ✅ 200 - credentials valides → JWT + refresh token retournés
    //    ❌ 400 - email malformé
    //    ❌ 404 - email ou mot de passe incorrect
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginEndpointTests {

        @Test
        @DisplayName("✅ 200 - login réussi, JWT et refresh token présents")
        void login_validCredentials_returns200WithToken() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setEmail("jean@test.com");
            request.setPassword("motdepasse123");

            LoginResponse response = LoginResponse.builder()
                    .token("jwt-access-token")
                    .refreshToken("refresh-uuid")
                    .type("Bearer")
                    .role(Role.CLIENT)
                    .build();
            when(authService.login(any())).thenReturn(response);

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-uuid"))
                    .andExpect(jsonPath("$.type").value("Bearer"));
        }

        @Test
        @DisplayName("❌ 404 - email ou mot de passe incorrect")
        void login_wrongCredentials_returns404() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setEmail("jean@test.com");
            request.setPassword("mauvaismdp");

            when(authService.login(any()))
                    .thenThrow(new UserNotFoundException("Email ou mot de passe incorrect"));

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("❌ 400 - format d'email invalide (@Email échoue avant le service)")
        void login_invalidEmailFormat_returns400() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setEmail("pas-un-email");
            request.setPassword("motdepasse123");

            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  POST /api/auth/logout
    //
    //  Cas couverts :
    //    ✅ 204 - logout avec utilisateur authentifié via @WithMockUser
    //    ✅ 204 - logout sans authentification (route publique /api/auth/**)
    //
    //  Note : /api/auth/** est dans PUBLIC_URLS de SecurityConfig,
    //  donc le logout est accessible sans token JWT. La protection
    //  par rôle (CLIENT/LAWYER/ADMIN) est testée dans UserControllerTest.
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/logout")
    class LogoutEndpointTests {

        @Test
        @DisplayName("✅ 204 - logout avec utilisateur authentifié via @WithMockUser")
        // @WithMockUser simule un utilisateur authentifié sans passer par le filtre JWT réel.
        // Équivalent de "Authorization: Bearer <token_valide>" sans générer de vrai JWT.
        @WithMockUser(username = "jean@test.com", roles = "CLIENT")
        void logout_authenticatedUser_returns204() throws Exception {
            // Avec @WithMockUser → @AuthenticationPrincipal reçoit un User mocké
            // refreshTokenService.revokeAllTokens() est appelé, retourne 204
            mockMvc.perform(post("/api/auth/logout")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("✅ 204 - logout sans authentification (route publique /api/auth/**)")
        void logout_noAuthentication_returns204() throws Exception {
            // /api/auth/** est déclaré PUBLIC dans SecurityConfig
            // → le logout est accessible sans token (user sera null dans le controller)
            // Le controller gère ce cas : if (user != null) revokeAllTokens()
            mockMvc.perform(post("/api/auth/logout")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }
    }
}